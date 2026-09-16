package BORG.data.hullmods;

import UFP.data.ai.FederationShieldsManagementAI;
import UFP.data.hullmods.FederationShields;
import UFP.data.logger.ScriptPerformanceReader;
import UFP.data.ui.ShieldHPBar_AI;
import UFP.data.ui.ShieldHPStatusBar;
import UFP.data.util.EMPDeflectionManager;
import UFP.data.util.ShieldHitpointManager;
import UFP.data.util.ShieldTextureManager;
import UFP.data.util.UFP_CSV_Manager;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import org.json.JSONObject;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

public class BorgShields extends BaseHullMod {

    // Borg Shield Constants
    public static final float ROTATION_RATE = 0.12f;
    public static final float SHIELD_UNFOLD_MULT = 2.75f;

    public static final Color BORG_BAR_COLOR = new Color(95, 230, 120, 235);
    public static final Color BORG_BORDER_COLOR = new Color(25, 35, 25, 210);
    public static final Color SHP_HIGHLIGHT = new Color(95, 230, 120, 235);

    public static final String DEFAULT_BORG_STYLE = "BubbleShield384";
    private static final String LAST_STATE_KEY = "borg_last_shield_state";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ScriptPerformanceReader.startTrack("BorgShields.applyEffectsAfterShipCreation");
        try {
            if (ship == null || ship.getShield() == null) {
                return;
            }

            applyVisuals(ship, ship.getShield());
            ship.getMutableStats().getShieldUnfoldRateMult().modifyMult("borg_shield_unfold", SHIELD_UNFOLD_MULT);

            ShieldHitpointManager.initialize(ship);
            ShieldHitpointManager.registerDamageInterceptor(ship);
            ShieldHitpointManager.setupShieldHPUI(ship, BORG_BAR_COLOR, BORG_BORDER_COLOR);

            Map<String, Object> customData = ship.getCustomData();
            customData.put(FederationShields.UNIVERSAL_HP_PRESENCE_KEY, true);

            if (!Boolean.TRUE.equals(customData.get(FederationShields.EMP_DEFLECT_LISTENER_KEY))) {
                ship.addListener(new EMPDeflectionManager(ship));
                customData.put(FederationShields.EMP_DEFLECT_LISTENER_KEY, true);
            }

            // Register Management AI
            ship.setCustomData(FederationShieldsManagementAI.KEY_ENABLED_ON_SHIP, true);
        } finally {
            ScriptPerformanceReader.endTrack("BorgShields.applyEffectsAfterShipCreation");
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        ScriptPerformanceReader.startTrack("BorgShields.advanceInCombat");
        try {
            if (ship == null || !ship.isAlive() || ship.getShield() == null) {
                return;
            }

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) {
                return;
            }

            Map<String, Object> customData = ship.getCustomData();
            boolean isPlayer = (ship == engine.getPlayerShip());

            if (isPlayer) {
                Map<String, Object> engineData = engine.getCustomData();

                if (!engineData.containsKey(FederationShields.SHIELD_HP_BAR_PLUGIN_KEY)) {
                    engineData.put(FederationShields.SHIELD_HP_BAR_PLUGIN_KEY, true);
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

            // State-based EMP immunity logic
            boolean shieldIsOn = ship.getShield().isOn();
            Object lastStateObj = customData.get(LAST_STATE_KEY);

            int currentState = isShieldDisabled ? 1 : (shieldIsOn ? 2 : 3);
            int lastState = (lastStateObj instanceof Integer) ? (Integer) lastStateObj : 0;

            if (currentState != lastState) {
                customData.put(LAST_STATE_KEY, currentState);

                if (currentState == 1 || currentState == 3) {
                    ship.getMutableStats().getEmpDamageTakenMult().unmodify(FederationShields.EMP_BLOCK_ID);
                } else if (currentState == 2) {
                    ship.getMutableStats().getEmpDamageTakenMult().modifyMult(FederationShields.EMP_BLOCK_ID, 0f);
                }
            }
        } finally {
            ScriptPerformanceReader.endTrack("BorgShields.advanceInCombat");
        }
    }

    protected String getGraphicStyle() {
        String selectedStyle = UFP_CSV_Manager.getSelectedShieldStyle();
        if (selectedStyle != null && ShieldTextureManager.isValidStyle(selectedStyle)) {
            return selectedStyle;
        }
        return DEFAULT_BORG_STYLE;
    }

    protected void applyVisuals(ShipAPI ship, ShieldAPI shield) {
        ScriptPerformanceReader.startTrack("BorgShields.applyVisuals");
        try {
            String selectedStyle = getGraphicStyle();
            String[] textures = ShieldTextureManager.getTextures(selectedStyle);

            shield.setRadius(shield.getRadius(), textures[0], textures[1]);
            applyHullStyleColors(ship, shield);

            shield.setInnerRotationRate(ROTATION_RATE);
            shield.setRingRotationRate(ROTATION_RATE);

            ShieldHitpointManager.setShieldHPHighlightColor(ship, SHP_HIGHLIGHT);
        } finally {
            ScriptPerformanceReader.endTrack("BorgShields.applyVisuals");
        }
    }

    protected void applyHullStyleColors(ShipAPI ship, ShieldAPI shield) {
        ScriptPerformanceReader.startTrack("BorgShields.applyHullStyleColors");
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
        } catch (Exception ignored) {
        } finally {
            ScriptPerformanceReader.endTrack("BorgShields.applyHullStyleColors");
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        ScriptPerformanceReader.startTrack("BorgShields.addPostDescriptionSection");
        try {
            if (tooltip == null) return;

            tooltip.addSectionHeading("Borg Adaptive Shielding", Alignment.MID, 10f);

            if (ship != null) {
                if (ship.getShield() == null) return;

                float maxHp = ShieldHitpointManager.getShieldHPForRefit(ship);
                if (maxHp <= 0f) return;

                tooltip.addPara("Standard shielding replaced with a collective adaptation matrix. Efficiency is secondary to structural integrity.", 6f);
                tooltip.addPara("Collective HP Pool: %s", 6f, Color.WHITE, SHP_HIGHLIGHT, String.format(Locale.ROOT, "%,.0f", maxHp));
                tooltip.addPara("Shield damage does not generate flux; it depletes this hitpoint pool instead.", 3f, Color.LIGHT_GRAY, SHP_HIGHLIGHT, "hitpoint pool");
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

            tooltip.addPara("Standard shielding replaced with a collective adaptation matrix across supported hulls.", 6f, Color.LIGHT_GRAY);
            tooltip.addPara("Across supported hulls: %s – %s max collective shield hitpoints.", 3f, Color.WHITE, SHP_HIGHLIGHT, String.format(Locale.ROOT, "%,.0f", min), String.format(Locale.ROOT, "%,.0f", max));
        } finally {
            ScriptPerformanceReader.endTrack("BorgShields.addPostDescriptionSection");
        }
    }
}