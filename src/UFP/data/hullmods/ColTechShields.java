package UFP.data.hullmods;

import UFP.data.ai.FederationShieldsManagementAI;
import UFP.data.logger.ScriptPerformanceReader;
import UFP.data.ui.ShieldHPBar_AI;
import UFP.data.ui.ShieldHPStatusBar;
import UFP.data.util.EMPDeflectionManager;
import UFP.data.util.ShieldHPDamageScaler;
import UFP.data.util.ShieldHitpointManager;
import UFP.data.util.ShieldTextureManager;
import UFP.data.util.UFP_CSV_Manager;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import org.json.JSONObject;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

public class ColTechShields extends BaseHullMod {

    public static final float ROTATION_RATE = 0.35f;
    public static final float SHIELD_UNFOLD_MULT = 2.4f;

    public static final Color COLTECH_BAR_COLOR = new Color(86, 89, 86, 230);
    public static final Color COLTECH_BORDER_COLOR = new Color(168, 236, 177, 200);
    public static final Color COLTECH_HIGHLIGHT = new Color(140, 170, 150, 230);

    public static final Color COLTECH_DAMAGE_FLOATY_COLOR = new Color(210, 255, 210, 230);
    public static final Color COLTECH_VENT_RESTORE_COLOR = new Color(120, 220, 170, 230);

    public static final String UNIVERSAL_HP_PRESENCE_KEY = "UFP_SH_HP";
    public static final String SHIELD_HP_BAR_PLUGIN_KEY = "UFP_ShieldHPBarAI_Registered";
    public static final String EMP_BLOCK_ID = "ufp_emp_block_when_shield_up";
    public static final String EMP_DEFLECT_LISTENER_KEY = "ufp_emp_deflect_listener_added";

    public static final String DEFAULT_COLTECH_STYLE = "Shields384";

    private static final String LAST_STATE_KEY = "ufp_last_shield_state";
    private static final String CT_DAMAGE_INTERCEPTOR_ADDED_KEY = "ufp_ct_damage_interceptor_added";

    private static final String CT_WAS_VENTING_KEY = "ufp_ct_was_venting";
    private static final String CT_LAST_FLUX_KEY = "ufp_ct_last_flux";
    private static final String CT_VENT_ACCUM_KEY = "ufp_ct_vent_accum";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ScriptPerformanceReader.startTrack("ColTechShields.applyEffectsAfterShipCreation");
        try {
            if (ship == null || ship.getShield() == null) return;

            applyVisuals(ship, ship.getShield());
            ship.getMutableStats().getShieldUnfoldRateMult().modifyMult("coltech_shield_unfold", SHIELD_UNFOLD_MULT);

            ShieldHitpointManager.initialize(ship);
            ShieldHitpointManager.setupShieldHPUI(ship, COLTECH_BAR_COLOR, COLTECH_BORDER_COLOR);

            Map<String, Object> customData = ship.getCustomData();
            customData.put(UNIVERSAL_HP_PRESENCE_KEY, true);

            if (!Boolean.TRUE.equals(customData.get(EMP_DEFLECT_LISTENER_KEY))) {
                ship.addListener(new EMPDeflectionManager(ship));
                customData.put(EMP_DEFLECT_LISTENER_KEY, true);
            }

            customData.put(CT_WAS_VENTING_KEY, false);
            customData.put(CT_LAST_FLUX_KEY, 0f);
            customData.put(CT_VENT_ACCUM_KEY, 0f);
            customData.put(CT_DAMAGE_INTERCEPTOR_ADDED_KEY, false);
        } finally {
            ScriptPerformanceReader.endTrack("ColTechShields.applyEffectsAfterShipCreation");
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        ScriptPerformanceReader.startTrack("ColTechShields.advanceInCombat");
        try {
            if (ship == null || !ship.isAlive() || ship.getShield() == null) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            Map<String, Object> customData = ship.getCustomData();
            boolean isPlayer = (ship == engine.getPlayerShip());

            if (isPlayer) {
                Map<String, Object> engineData = engine.getCustomData();

                if (!engineData.containsKey(SHIELD_HP_BAR_PLUGIN_KEY)) {
                    engineData.put(SHIELD_HP_BAR_PLUGIN_KEY, true);
                    if (!engine.hasPluginOfClass(ShieldHPBar_AI.class)) {
                        engine.addPlugin(new ShieldHPBar_AI());
                    }
                }

                if (!engineData.containsKey(FederationShieldsManagementAI.KEY_PLUGIN_REGISTERED)) {
                    engineData.put(FederationShieldsManagementAI.KEY_PLUGIN_REGISTERED, true);
                    if (!engine.hasPluginOfClass(FederationShieldsManagementAI.class)) {
                        engine.addPlugin(new FederationShieldsManagementAI());
                    }
                }

                ShieldHPStatusBar.render(ship, engine);
            } else {
                customData.put(ShieldHPBar_AI.SHOW_SHIELD_HP_BAR_KEY, true);
            }

            ShieldHitpointManager.update(ship, amount);

            boolean isShieldDisabled = ShieldHitpointManager.isShieldDisabled(ship);
            Object containerObj = customData.get(ShieldHPBar_AI.UFP_BAR_DATA_CONTAINER_KEY);

            if (containerObj instanceof ShieldHPBar_AI.ShieldBarState) {
                ShieldHPBar_AI.ShieldBarState container = (ShieldHPBar_AI.ShieldBarState) containerObj;
                container.currentHp = ShieldHitpointManager.getShieldHP(ship);
                container.isDisabled = isShieldDisabled;
            }

            boolean shieldIsOn = ship.getShield().isOn();
            Object lastStateObj = customData.get(LAST_STATE_KEY);

            int currentState = isShieldDisabled ? 1 : (shieldIsOn ? 2 : 3);
            int lastState = (lastStateObj instanceof Integer) ? (Integer) lastStateObj : 0;

            if (currentState != lastState) {
                customData.put(LAST_STATE_KEY, currentState);

                if (currentState == 1 || currentState == 3) {
                    ship.getMutableStats().getEmpDamageTakenMult().unmodify(EMP_BLOCK_ID);
                } else if (currentState == 2) {
                    ship.getMutableStats().getEmpDamageTakenMult().modifyMult(EMP_BLOCK_ID, 0f);
                }
            }

            if (engine.isPaused()) return;

            if (!Boolean.TRUE.equals(customData.get(CT_DAMAGE_INTERCEPTOR_ADDED_KEY))) {
                if (engine.getListenerManager() != null) {
                    engine.getListenerManager().addListener(new ColTechShieldDamageInterceptor(ship));
                    customData.put(CT_DAMAGE_INTERCEPTOR_ADDED_KEY, true);
                }
            }

            advanceVentingRecovery(ship, engine);
        } finally {
            ScriptPerformanceReader.endTrack("ColTechShields.advanceInCombat");
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        ScriptPerformanceReader.startTrack("ColTechShields.addPostDescriptionSection");
        try {
            if (tooltip == null) return;

            if (ship != null) {
                if (ship.getShield() == null) return;

                float maxHp = ShieldHitpointManager.getShieldHPForRefit(ship);
                if (maxHp <= 0f) return;

                tooltip.addSectionHeading("Shield Hitpoints", Alignment.MID, 10f);
                tooltip.addPara("Maximum shield hitpoints: %s", 6f, Color.WHITE, COLTECH_HIGHLIGHT, String.format(Locale.ROOT, "%,.0f", maxHp));
                tooltip.addPara("Shield damage does not generate flux; it depletes this hitpoint pool instead.", 3f, Color.LIGHT_GRAY, COLTECH_HIGHLIGHT, "hitpoint pool");
                return;
            }

            Map<String, Float> hpMap = UFP_CSV_Manager.getShieldHPMap();
            if (hpMap == null || hpMap.isEmpty()) return;

            float min = Float.MAX_VALUE;
            float max = 0f;
            for (Float v : hpMap.values()) {
                if (v == null || v <= 0f) continue;
                if (v < min) min = v;
                if (v > max) max = v;
            }
            if (max <= 0f || min == Float.MAX_VALUE) return;

            tooltip.addSectionHeading("Shield Hitpoints", Alignment.MID, 10f);
            tooltip.addPara("This hullmod applies a fixed shield hitpoint pool directly to the hull configuration.", 6f, Color.LIGHT_GRAY);
            tooltip.addPara("Across supported hulls: %s – %s max shield hitpoints.", 3f, Color.WHITE, COLTECH_HIGHLIGHT, String.format(Locale.ROOT, "%,.0f", min), String.format(Locale.ROOT, "%,.0f", max));
            tooltip.addPara("The exact value matching your current hull configuration will be highlighted above.", 3f, Color.LIGHT_GRAY);
        } finally {
            ScriptPerformanceReader.endTrack("ColTechShields.addPostDescriptionSection");
        }
    }

    public static float getConfiguredShieldHPOrFlux(ShipAPI ship) {
        return ShieldHitpointManager.getShieldHPForRefit(ship);
    }

    protected String getGraphicStyle() {
        String selectedStyle = UFP_CSV_Manager.getColTechShieldStyle();
        if (selectedStyle != null && ShieldTextureManager.isValidStyle(selectedStyle)) {
            return selectedStyle;
        }
        return DEFAULT_COLTECH_STYLE;
    }

    protected void applyVisuals(ShipAPI ship, ShieldAPI shield) {
        ScriptPerformanceReader.startTrack("ColTechShields.applyVisuals");
        try {
            String selectedStyle = getGraphicStyle();
            String[] textures = ShieldTextureManager.getTextures(selectedStyle);

            shield.setRadius(shield.getRadius(), textures[0], textures[1]);
            applyHullStyleColors(ship, shield);

            shield.setInnerRotationRate(ROTATION_RATE);
            shield.setRingRotationRate(ROTATION_RATE);

            ShieldHitpointManager.setShieldHPHighlightColor(ship, COLTECH_HIGHLIGHT);
        } finally {
            ScriptPerformanceReader.endTrack("ColTechShields.applyVisuals");
        }
    }

    protected void applyHullStyleColors(ShipAPI ship, ShieldAPI shield) {
        try {
            String hullStyleId = ship.getHullStyleId();
            if (hullStyleId == null) return;

            JSONObject settingsJson = Global.getSettings().getSettingsJSON();
            if (settingsJson == null || !settingsJson.has("hullStyles")) return;

            JSONObject hullStyles = settingsJson.getJSONObject("hullStyles");
            if (!hullStyles.has(hullStyleId)) return;

            JSONObject styleJson = hullStyles.getJSONObject(hullStyleId);

            if (styleJson.has("shieldInnerColor")) {
                var innerArray = styleJson.getJSONArray("shieldInnerColor");
                shield.setInnerColor(new Color(innerArray.getInt(0), innerArray.getInt(1), innerArray.getInt(2), innerArray.length() > 3 ? innerArray.getInt(3) : 255));
            }
            if (styleJson.has("shieldRingColor")) {
                var ringArray = styleJson.getJSONArray("shieldRingColor");
                shield.setRingColor(new Color(ringArray.getInt(0), ringArray.getInt(1), ringArray.getInt(2), ringArray.length() > 3 ? ringArray.getInt(3) : 255));
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // Damage Interceptor (Queries UFP_CSV_Manager)
    // ============================================================
    private static final class ColTechShieldDamageInterceptor implements DamageListener {
        private final ShipAPI ship;

        private ColTechShieldDamageInterceptor(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
            if (target != ship || result == null || result.getDamageToShields() <= 0f) return;
            if (ship.getShield() == null || !ship.isAlive() || !ship.getShield().isOn()) return;
            if (!ship.getCustomData().containsKey("universal_shield_hp") || source == ship) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            float rawEngineShieldDamage = result.getDamageToShields();
            float rawEngineOverMax = ShieldHPDamageScaler.INCLUDE_OVERMAX_SHIELD_DAMAGE ? Math.max(0f, result.getOverMaxDamageToShields()) : 0f;
            float rawEngineTotalShieldDamage = rawEngineShieldDamage + rawEngineOverMax;

            Float ctMult = resolveColTechOverrideMult(source);
            float hpDamage;

            if (ctMult != null) {
                float raw = rawEngineTotalShieldDamage;
                if (ShieldHPDamageScaler.CONVERT_SHIELD_FLUX_TO_RAW_DAMAGE) {
                    ShieldAPI sh = ship.getShield();
                    if (sh != null && sh.getFluxPerPointOfDamage() > 0f) {
                        raw /= sh.getFluxPerPointOfDamage();
                    }
                }
                hpDamage = Math.max(0f, raw * ctMult);
            } else {
                hpDamage = ShieldHPDamageScaler.computeShieldHpDamage(source, ship, rawEngineTotalShieldDamage);
            }

            ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) - hpDamage);
            result.setDamageToShields(0f);

            FluxTrackerAPI flux = ship.getFluxTracker();
            if (flux != null) {
                flux.setCurrFlux(Math.max(0f, flux.getCurrFlux() - rawEngineTotalShieldDamage));
                flux.setHardFlux(Math.max(0f, flux.getHardFlux() - rawEngineTotalShieldDamage));
            }

            String txt = String.format(Locale.ROOT, "-%.2f Shield HP", hpDamage);
            engine.addFloatingText(ship.getLocation(), txt, 12f, COLTECH_DAMAGE_FLOATY_COLOR, ship, 0.5f, 0.5f);

            if (ShieldHitpointManager.getShieldHP(ship) <= 0f && !ShieldHitpointManager.isShieldDisabled(ship)) {
                ship.setCustomData("universal_shield_disabled", true);
                ship.getShield().toggleOff();
                engine.addFloatingText(ship.getLocation(), "Shield Depleted", 20f, Color.RED, ship, 1f, 1f);
            }
        }
    }

    private static Float resolveColTechOverrideMult(Object source) {
        if (source instanceof WeaponAPI) {
            String wId = ((WeaponAPI) source).getId();
            Float w = UFP_CSV_Manager.getColTechWeaponMultMap().get(wId);
            if (w != null) return w;
        }
        if (source instanceof DamagingProjectileAPI) {
            String pId = ((DamagingProjectileAPI) source).getProjectileSpecId();
            if (pId != null) {
                Float p = UFP_CSV_Manager.getColTechProjectileMultMap().get(pId);
                if (p != null) return p;
            }
        }
        return null;
    }

    // ============================================================
    // Venting Recovery Mechanics
    // ============================================================
    private static void advanceVentingRecovery(ShipAPI ship, CombatEngineAPI engine) {
        if (ship == null || engine == null || ShieldHitpointManager.isShieldDisabled(ship)) return;

        FluxTrackerAPI flux = ship.getFluxTracker();
        if (flux == null) return;

        boolean venting = flux.isVenting();
        boolean wasVenting = Boolean.TRUE.equals(ship.getCustomData().get(CT_WAS_VENTING_KEY));
        float fluxNow = flux.getCurrFlux();
        float lastFlux = getFloat(ship, CT_LAST_FLUX_KEY);
        float accum = getFloat(ship, CT_VENT_ACCUM_KEY);

        if (venting && !wasVenting) {
            ship.setCustomData(CT_VENT_ACCUM_KEY, 0f);
            ship.setCustomData(CT_LAST_FLUX_KEY, fluxNow);
            ship.setCustomData(CT_WAS_VENTING_KEY, true);
            return;
        }

        if (venting && wasVenting) {
            accum += Math.max(0f, lastFlux - fluxNow);
            ship.setCustomData(CT_VENT_ACCUM_KEY, accum);
            ship.setCustomData(CT_LAST_FLUX_KEY, fluxNow);
            return;
        }

        if (!venting && wasVenting) {
            ship.setCustomData(CT_WAS_VENTING_KEY, false);
            ship.setCustomData(CT_LAST_FLUX_KEY, fluxNow);
            ship.setCustomData(CT_VENT_ACCUM_KEY, 0f);

            float maxFlux = ship.getMaxFlux();
            if (maxFlux <= 0f) return;

            float tier = toQuarterTier(accum / maxFlux);
            if (tier < 0.25f) return;

            float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
            float curHP = ShieldHitpointManager.getShieldHP(ship);
            if (maxHP <= 0f) return;

            float targetHP = maxHP * tier;
            if (curHP < targetHP) {
                ShieldHitpointManager.setShieldHP(ship, targetHP);
                int pct = Math.round(tier * 100f);
                engine.addFloatingText(
                        ship.getLocation(),
                        "+" + pct + "% Shield HP (Venting)",
                        12f,
                        COLTECH_VENT_RESTORE_COLOR,
                        ship,
                        0.5f,
                        0.5f
                );
            }
        } else {
            ship.setCustomData(CT_LAST_FLUX_KEY, fluxNow);
        }
    }

    private static float toQuarterTier(float ventedPct) {
        if (ventedPct >= 1.0f) return 1.0f;
        if (ventedPct >= 0.75f) return 0.75f;
        if (ventedPct >= 0.50f) return 0.50f;
        if (ventedPct >= 0.25f) return 0.25f;
        return 0f;
    }

    private static float getFloat(ShipAPI ship, String key) {
        Object o = ship.getCustomData().get(key);
        return (o instanceof Float) ? (Float) o : 0f;
    }
}