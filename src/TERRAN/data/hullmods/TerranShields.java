package TERRAN.data.hullmods;

import UFP.data.hullmods.FederationShields;
import UFP.data.ui.ShieldHPStatusBar;
import UFP.data.ui.ShieldHPBar_AI;
import UFP.data.util.ShieldHitpointManager;
import UFP.data.util.ShieldTextureManager;
import UFP.data.util.UFP_CSV_Manager;
import UFP.data.util.EMPDeflectionManager;
import UFP.data.ai.FederationShieldsManagementAI;
import UFP.data.logger.ScriptPerformanceReader;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

public class TerranShields extends FederationShields {

    // Distinct Terran UI styling (Warm Amber/Orange theme)
    public static final Color TERRAN_BAR_COLOR = new Color(213, 131, 75, 230);
    public static final Color TERRAN_BORDER_COLOR = new Color(50, 50, 50, 200);
    public static final Color TERRAN_SHP_HIGHLIGHT = new Color(235, 150, 85, 230);

    private static final String LAST_STATE_KEY = "terran_last_shield_state";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ScriptPerformanceReader.startTrack("TerranShields.applyEffectsAfterShipCreation");
        try {
            if (ship == null || ship.getShield() == null) {
                return;
            }

            applyVisuals(ship, ship.getShield());
            ship.getMutableStats().getShieldUnfoldRateMult().modifyMult("terran_shield_unfold", SHIELD_UNFOLD_MULT);

            ShieldHitpointManager.initialize(ship);
            ShieldHitpointManager.registerDamageInterceptor(ship);
            ShieldHitpointManager.setupShieldHPUI(ship, TERRAN_BAR_COLOR, TERRAN_BORDER_COLOR);

            Map<String, Object> customData = ship.getCustomData();
            customData.put(UNIVERSAL_HP_PRESENCE_KEY, true);

            // Register the EMP reflection manager listener
            if (!Boolean.TRUE.equals(customData.get(EMP_DEFLECT_LISTENER_KEY))) {
                ship.addListener(new EMPDeflectionManager(ship));
                customData.put(EMP_DEFLECT_LISTENER_KEY, true);
            }
        } finally {
            ScriptPerformanceReader.endTrack("TerranShields.applyEffectsAfterShipCreation");
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        ScriptPerformanceReader.startTrack("TerranShields.advanceInCombat");
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

                // Automatically pulls modified customData bar colors during render
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
        } finally {
            ScriptPerformanceReader.endTrack("TerranShields.advanceInCombat");
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        ScriptPerformanceReader.startTrack("TerranShields.addPostDescriptionSection");
        try {
            if (tooltip == null) return;

            if (ship != null) {
                if (ship.getShield() == null) return;

                float maxHp = ShieldHitpointManager.getShieldHPForRefit(ship);
                if (maxHp <= 0f) return;

                tooltip.addSectionHeading("Shield Hitpoints", Alignment.MID, 10f);
                tooltip.addPara("Maximum shield hitpoints: %s", 6f, Color.WHITE, TERRAN_SHP_HIGHLIGHT, String.format(Locale.ROOT, "%,.0f", maxHp));
                tooltip.addPara("Shield damage does not generate flux; it depletes this hitpoint pool instead.", 3f, Color.LIGHT_GRAY, TERRAN_SHP_HIGHLIGHT, "hitpoint pool");
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
            tooltip.addPara("Across supported hulls: %s – %s max shield hitpoints.", 3f, Color.WHITE, TERRAN_SHP_HIGHLIGHT, String.format(Locale.ROOT, "%,.0f", min), String.format(Locale.ROOT, "%,.0f", max));
            tooltip.addPara("The exact value matching your current hull configuration will be highlighted above.", 3f, Color.LIGHT_GRAY);
        } finally {
            ScriptPerformanceReader.endTrack("TerranShields.addPostDescriptionSection");
        }
    }

    /**
     * Overrides the parent graphic token lookup hook seamlessly.
     * Direct allocation-free retrieval from pre-cached settings arrays.
     */
    @Override
    protected String getGraphicStyle() {
        return UFP_CSV_Manager.getTerranShieldStyle();
    }

    @Override
    protected void applyVisuals(ShipAPI ship, ShieldAPI shield) {
        ScriptPerformanceReader.startTrack("TerranShields.applyVisuals");
        try {
            String selectedStyle = getGraphicStyle();
            String[] textures = ShieldTextureManager.getTextures(selectedStyle);

            shield.setRadius(shield.getRadius(), textures[0], textures[1]);
            applyHullStyleColors(ship, shield);

            shield.setInnerRotationRate(ROTATION_RATE);
            shield.setRingRotationRate(ROTATION_RATE);

            ShieldHitpointManager.setShieldHPHighlightColor(ship, TERRAN_SHP_HIGHLIGHT);
        } finally {
            ScriptPerformanceReader.endTrack("TerranShields.applyVisuals");
        }
    }
}