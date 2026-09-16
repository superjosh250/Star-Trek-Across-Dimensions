package BORG.data.hullmods;

import BORG.data.util.BorgRegenerationMatrix;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * RegenerationMatrix now delegates to BorgRegenerationMatrix correctly.
 *
 * This class also provides optional "override keys" that can replace the public static
 * config values inside BorgRegenerationMatrix WITHOUT modifying BorgRegenerationMatrix.
 *
 * NOTE: Overrides are applied via reflection (best-effort). If reflection is blocked
 * in your runtime, the defaults in BorgRegenerationMatrix will remain in effect.
 */
public class RegenerationMatrix extends BaseHullMod {

    // ---------------------------------------------------------------------
    // OVERRIDE KEYS (set to Float.NaN to keep BorgRegenerationMatrix defaults)
    // These map 1:1 to public static fields in BorgRegenerationMatrix.
    // ---------------------------------------------------------------------

    /** Overrides BorgRegenerationMatrix.PASSIVE_HULL_REGEN_STD */
    public static float OVERRIDE_PASSIVE_HULL_REGEN_STD = Float.NaN;

    /** Overrides BorgRegenerationMatrix.PASSIVE_HULL_REGEN_CAP */
    public static float OVERRIDE_PASSIVE_HULL_REGEN_CAP = Float.NaN;

    /** Overrides BorgRegenerationMatrix.HULL_TIMER_STD */
    public static float OVERRIDE_HULL_TIMER_STD = Float.NaN;

    /** Overrides BorgRegenerationMatrix.HULL_TIMER_CAP */
    public static float OVERRIDE_HULL_TIMER_CAP = Float.NaN;

    /** Overrides BorgRegenerationMatrix.VENT_HEAL_STD */
    public static float OVERRIDE_VENT_HEAL_STD = Float.NaN;

    /** Overrides BorgRegenerationMatrix.VENT_HEAL_CAP */
    public static float OVERRIDE_VENT_HEAL_CAP = Float.NaN;

    /** Overrides BorgRegenerationMatrix.VENT_STEP_STD */
    public static float OVERRIDE_VENT_STEP_STD = Float.NaN;

    /** Overrides BorgRegenerationMatrix.VENT_STEP_CAP */
    public static float OVERRIDE_VENT_STEP_CAP = Float.NaN;

    /** Overrides BorgRegenerationMatrix.ARMOR_REGEN_PASSIVE */
    public static float OVERRIDE_ARMOR_REGEN_PASSIVE = Float.NaN;

    /** Overrides BorgRegenerationMatrix.ARMOR_REGEN_TIMER */
    public static float OVERRIDE_ARMOR_REGEN_TIMER = Float.NaN;

    /** Overrides BorgRegenerationMatrix.ARMOR_REGEN_CAP */
    public static float OVERRIDE_ARMOR_REGEN_CAP = Float.NaN;

    // ---------------------------------------------------------------------

    private static boolean overridesApplied = false;
    private final BorgRegenerationMatrix matrix = new BorgRegenerationMatrix();

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        // Apply overrides once per combat runtime (best-effort).
        applyOverridesOnce();

        // Correct current BorgRegenerationMatrix usage (2-arg advance).
        matrix.advance(amount, ship);

        // Optional HUD feedback for the player ship.
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null && ship == engine.getPlayerShip()) {
            displayRegenStatus(ship, engine);
        }
    }

    private static void applyOverridesOnce() {
        if (overridesApplied) return;
        overridesApplied = true;

        // Hull passive regen + timer
        overrideIfSet("PASSIVE_HULL_REGEN_STD", OVERRIDE_PASSIVE_HULL_REGEN_STD);
        overrideIfSet("PASSIVE_HULL_REGEN_CAP", OVERRIDE_PASSIVE_HULL_REGEN_CAP);
        overrideIfSet("HULL_TIMER_STD",        OVERRIDE_HULL_TIMER_STD);
        overrideIfSet("HULL_TIMER_CAP",        OVERRIDE_HULL_TIMER_CAP);

        // Vent healing scaling
        overrideIfSet("VENT_HEAL_STD", OVERRIDE_VENT_HEAL_STD);
        overrideIfSet("VENT_HEAL_CAP", OVERRIDE_VENT_HEAL_CAP);
        overrideIfSet("VENT_STEP_STD", OVERRIDE_VENT_STEP_STD);
        overrideIfSet("VENT_STEP_CAP", OVERRIDE_VENT_STEP_CAP);

        // Armor regen
        overrideIfSet("ARMOR_REGEN_PASSIVE", OVERRIDE_ARMOR_REGEN_PASSIVE);
        overrideIfSet("ARMOR_REGEN_TIMER",   OVERRIDE_ARMOR_REGEN_TIMER);
        overrideIfSet("ARMOR_REGEN_CAP",     OVERRIDE_ARMOR_REGEN_CAP);
    }

    private static void overrideIfSet(String borgFieldName, float value) {
        if (Float.isNaN(value)) return; // not overriding
        setStaticFinalFloat(BorgRegenerationMatrix.class, borgFieldName, value);
    }

    /**
     * Best-effort override of a public static final float in BorgRegenerationMatrix.
     * This avoids changing BorgRegenerationMatrix source, as requested.
     */
    private static void setStaticFinalFloat(Class<?> clazz, String fieldName, float value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            // Remove FINAL modifier if possible (works on many Java 7/8 runtimes).
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignored) {
                // If we can't remove FINAL, we still attempt setFloat; runtime may ignore.
            }

            field.setFloat(null, value);
        } catch (Throwable ignored) {
            // If reflection is blocked or field doesn't exist, we silently fall back to defaults.
        }
    }

    private void displayRegenStatus(ShipAPI ship, CombatEngineAPI engine) {
        boolean isCapital = ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP;

        float hullRegen = isCapital
                ? BorgRegenerationMatrix.PASSIVE_HULL_REGEN_CAP
                : BorgRegenerationMatrix.PASSIVE_HULL_REGEN_STD;

        float hullTimer = isCapital
                ? BorgRegenerationMatrix.HULL_TIMER_CAP
                : BorgRegenerationMatrix.HULL_TIMER_STD;

        float ventRate = isCapital
                ? BorgRegenerationMatrix.VENT_HEAL_CAP
                : BorgRegenerationMatrix.VENT_HEAL_STD;

        float ventStep = isCapital
                ? BorgRegenerationMatrix.VENT_STEP_CAP
                : BorgRegenerationMatrix.VENT_STEP_STD;

        String status = String.format(
                "Hull: +%.1f%% / %.1fs | Vent: %.2f%% per %.0f flux",
                hullRegen * 100f, hullTimer,
                ventRate * 100f, ventStep
        );

        engine.maintainStatusForPlayerShip(
                "BORG_REGEN_HUD",
                "graphics/icons/hullsys/repair_bots.png",
                "Regen Matrix",
                status,
                false
        );
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        boolean isCapital = hullSize == ShipAPI.HullSize.CAPITAL_SHIP;

        if (index == 0) return "" + (int) Math.round(BorgRegenerationMatrix.PASSIVE_HULL_REGEN_STD * 100f) + "%";
        if (index == 1) return "" + (int) Math.round(BorgRegenerationMatrix.HULL_TIMER_STD);

        if (index == 2) return "" + (int) Math.round(BorgRegenerationMatrix.PASSIVE_HULL_REGEN_CAP * 100f) + "%";
        if (index == 3) return "" + (int) Math.round(BorgRegenerationMatrix.HULL_TIMER_CAP);

        if (index == 4) {
            float rate = isCapital ? BorgRegenerationMatrix.VENT_HEAL_CAP : BorgRegenerationMatrix.VENT_HEAL_STD;
            return "" + (int) Math.round(rate * 100f) + "%";
        }

        if (index == 5) {
            float step = isCapital ? BorgRegenerationMatrix.VENT_STEP_CAP : BorgRegenerationMatrix.VENT_STEP_STD;
            return "" + (int) Math.round(step);
        }

        if (index == 6) return "" + (int) Math.round(BorgRegenerationMatrix.ARMOR_REGEN_PASSIVE * 100f) + "%";
        if (index == 7) return "" + (int) Math.round(BorgRegenerationMatrix.ARMOR_REGEN_TIMER);
        if (index == 8) return "" + (int) Math.round(BorgRegenerationMatrix.ARMOR_REGEN_CAP * 100f) + "%";

        return null;
    }

    /**
     * Some tooltip contexts call the ShipAPI overload instead of HullSize.
     * Delegate to the HullSize version so %s never becomes null.
     */
    public String getDescriptionParam(int index, ShipAPI ship) {
        ShipAPI.HullSize hs = ship != null ? ship.getHullSize() : ShipAPI.HullSize.FRIGATE;
        return getDescriptionParam(index, hs);
    }
}
