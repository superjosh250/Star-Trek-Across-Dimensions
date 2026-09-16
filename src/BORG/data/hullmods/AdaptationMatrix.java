package BORG.data.hullmods;

import BORG.data.util.BorgAdaptationMatrix;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class AdaptationMatrix extends BaseHullMod {

    public static float OVERRIDE_GCD_RESISTANCE_SEC = 2.0f;
    public static float OVERRIDE_GCD_BEAM_TICK_SEC = 3.0f;
    public static float OVERRIDE_GCD_FULL_ADAPT_ROLL = 25.0f;
    public static float OVERRIDE_GLOBAL_ADAPT_COOLDOWN = 1.5f;

    public static float OVERRIDE_TYPE_RES_SAME_WEAPON = 0.05f;
    public static float OVERRIDE_TYPE_RES_NEW_WEAPON = 0.075f;

    //Cap on global type resistance
    public static float OVERRIDE_TYPE_RES_MAX = 0.50f;

    //Hull size learning slot caps.
    public static int OVERRIDE_SLOTS_FRIGATE = 1;
    public static int OVERRIDE_SLOTS_DESTROYER = 2;
    public static int OVERRIDE_SLOTS_CRUISER = 3;
    public static int OVERRIDE_SLOTS_CAPITAL = 4;
    public static int OVERRIDE_SLOTS_DEFAULT = 1;

    public static boolean OVERRIDE_RENDER_PLAYER_HUD = true;

    private static final String KEY_OVERRIDES_APPLIED = "borg_adaptation_overrides_applied";

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null || !ship.isAlive()) return;

        if (ship.getCustomData().get(KEY_OVERRIDES_APPLIED) == null) {
            applyOverridesToBorgMatrix();
            ship.setCustomData(KEY_OVERRIDES_APPLIED, Boolean.TRUE);
        }


        BorgAdaptationMatrix.register(ship);  // attaches DamageTakenModifier listener if missing

        // 3) Optional: HUD only for player ship
        if (OVERRIDE_RENDER_PLAYER_HUD && ship == engine.getPlayerShip()) {
            BorgAdaptationMatrix.renderHUD(ship, engine);  // shows analyzing / immunity / etc.
        }

        // Keep your logger running (unchanged)
        BORG.data.logger.BorgAdaptLogger.logCombatState(ship);
    }

    private static void applyOverridesToBorgMatrix() {
        // Push values into BorgAdaptationMatrix public statics
        BorgAdaptationMatrix.GCD_RESISTANCE_SEC = OVERRIDE_GCD_RESISTANCE_SEC;
        BorgAdaptationMatrix.GCD_BEAM_TICK_SEC = OVERRIDE_GCD_BEAM_TICK_SEC;
        BorgAdaptationMatrix.GCD_FULL_ADAPT_ROLL = OVERRIDE_GCD_FULL_ADAPT_ROLL;
        BorgAdaptationMatrix.GLOBAL_ADAPT_COOLDOWN = OVERRIDE_GLOBAL_ADAPT_COOLDOWN;

        // Slot caps
        BorgAdaptationMatrix.SLOTS_FRIGATE = OVERRIDE_SLOTS_FRIGATE;
        BorgAdaptationMatrix.SLOTS_DESTROYER = OVERRIDE_SLOTS_DESTROYER;
        BorgAdaptationMatrix.SLOTS_CRUISER = OVERRIDE_SLOTS_CRUISER;
        BorgAdaptationMatrix.SLOTS_CAPITAL = OVERRIDE_SLOTS_CAPITAL;
        BorgAdaptationMatrix.SLOTS_DEFAULT = OVERRIDE_SLOTS_DEFAULT;
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {

        if (index == 0) return "" + (int) Math.round(OVERRIDE_GCD_RESISTANCE_SEC) + "s";
        if (index == 1) return "" + (int) Math.round(OVERRIDE_GCD_BEAM_TICK_SEC) + "s";
        if (index == 2) return "" + (int) Math.round(OVERRIDE_GCD_FULL_ADAPT_ROLL) + "s";
        if (index == 3) return "" + (int) Math.round(OVERRIDE_GLOBAL_ADAPT_COOLDOWN * 100f) / 100f + "s";

        if (index == 4) return "" + (int) Math.round(OVERRIDE_TYPE_RES_SAME_WEAPON * 100f) + "%";
        if (index == 5) return "" + (int) Math.round(OVERRIDE_TYPE_RES_NEW_WEAPON * 100f) + "%";
        if (index == 6) return "" + (int) Math.round(OVERRIDE_TYPE_RES_MAX * 100f) + "%";

        // Slot caps by hull size
        if (index == 7) return "" + OVERRIDE_SLOTS_FRIGATE;
        if (index == 8) return "" + OVERRIDE_SLOTS_DESTROYER;
        if (index == 9) return "" + OVERRIDE_SLOTS_CRUISER;
        if (index == 10) return "" + OVERRIDE_SLOTS_CAPITAL;

        return null;
    }

    public String getDescriptionParam(int index, ShipAPI ship) {
        ShipAPI.HullSize hs = ship != null ? ship.getHullSize() : ShipAPI.HullSize.FRIGATE;
        return getDescriptionParam(index, hs);
    }
}