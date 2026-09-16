package BORG.data.util;

import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.HashMap;
import java.util.Map;

public class BorgRegenerationMatrix {

    // --- QUICK CONFIGURATION OVERRIDES ---
    public static final float PASSIVE_HULL_REGEN_STD = 0.025f;    // 2.5%
    public static final float PASSIVE_HULL_REGEN_CAP = 0.050f;    // 5.0%
    public static final float HULL_TIMER_STD = 6.0f;              // 6 seconds
    public static final float HULL_TIMER_CAP = 8.0f;              // 8 seconds

    public static final float VENT_HEAL_STD = 0.001f;             // 0.1% per 100 flux
    public static final float VENT_HEAL_CAP = 0.0075f;            // 0.75% per 125 flux
    public static final float VENT_STEP_STD = 100f;
    public static final float VENT_STEP_CAP = 125f;

    public static final float ARMOR_REGEN_PASSIVE = 0.005f;       // 0.5%
    public static final float ARMOR_REGEN_TIMER = 3.0f;           // 3 seconds
    public static final float ARMOR_REGEN_CAP = 0.50f;            // Max 50% repair

    public static final float[] DIMINISHING_CAPS = {1.00f, 0.75f, 0.50f, 0.45f};
    // -------------------------------------

    private static final Map<String, ShipLifeState> SHIP_STATES = new HashMap<>();

    private final IntervalUtil armorTimer = new IntervalUtil(ARMOR_REGEN_TIMER, ARMOR_REGEN_TIMER);
    private final IntervalUtil stdHullTimer = new IntervalUtil(HULL_TIMER_STD, HULL_TIMER_STD);
    private final IntervalUtil capHullTimer = new IntervalUtil(HULL_TIMER_CAP, HULL_TIMER_CAP);

    public void advance(float amount, ShipAPI ship) {
        if (ship == null || !ship.isAlive()) return;

        // Use ship ID to persist state throughout the battle
        ShipLifeState state = SHIP_STATES.computeIfAbsent(ship.getId(), id -> new ShipLifeState());

        float currentHull = ship.getHitpoints();
        float maxHull = ship.getMaxHitpoints();
        float currentCap = DIMINISHING_CAPS[state.lifeCyclesUsed];
        boolean isCapital = ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP;

        // 1. DIMINISHING RETURNS TRACKER
        // If ship hits the current "soft cap", it's ready to drop to the next stage if it takes heavy damage again
        if (currentHull >= (maxHull * currentCap) * 0.98f) {
            state.readyToDrop = true;
        }
        // If ship was at its cap but is now critically low (<10%), drop the cap level
        if (state.readyToDrop && currentHull < (maxHull * 0.10f)) {
            if (state.lifeCyclesUsed < DIMINISHING_CAPS.length - 1) {
                state.lifeCyclesUsed++;
                state.readyToDrop = false;
            }
        }

        // 2. PASSIVE HULL REGENERATION
        IntervalUtil activeTimer = isCapital ? capHullTimer : stdHullTimer;
        activeTimer.advance(amount);

        if (activeTimer.intervalElapsed() && currentHull < (maxHull * currentCap)) {
            float regenAmount = isCapital ? PASSIVE_HULL_REGEN_CAP : PASSIVE_HULL_REGEN_STD;
            ship.setHitpoints(Math.min(maxHull * currentCap, currentHull + (maxHull * regenAmount)));
        }

        // 3. VENTING REGENERATION (Corrected for Flux-to-Heal Scaling)
        if (ship.getFluxTracker().isVenting() && currentHull < (maxHull * currentCap)) {
            // In Starsector, venting dissipates flux at 2x the base rate
            float fluxDissipatedThisFrame = (ship.getMutableStats().getFluxDissipation().getModifiedValue() * 2f) * amount;

            // Ensure we don't "heal" from empty flux
            if (ship.getFluxTracker().getCurrFlux() > 0) {
                float step = isCapital ? VENT_STEP_CAP : VENT_STEP_STD;
                float rate = isCapital ? VENT_HEAL_CAP : VENT_HEAL_STD;

                // Calculate healing: (Flux dissipated / Step size) * healing percentage
                float ventHeal = (fluxDissipatedThisFrame / step) * (maxHull * rate);
                ship.setHitpoints(Math.min(maxHull * currentCap, currentHull + ventHeal));
            }
        }

        // 4. PASSIVE ARMOR REGENERATION
        armorTimer.advance(amount);
        if (armorTimer.intervalElapsed()) {
            handleArmorRegen(ship);
        }
    }

    private void handleArmorRegen(ShipAPI ship) {
        ArmorGridAPI grid = ship.getArmorGrid();
        float cellMax = grid.getMaxArmorInCell();
        float limit = cellMax * ARMOR_REGEN_CAP;
        float heal = cellMax * ARMOR_REGEN_PASSIVE;

        for (int x = 0; x < grid.getGrid().length; x++) {
            for (int y = 0; y < grid.getGrid()[0].length; y++) {
                float val = grid.getArmorValue(x, y);
                if (val < limit) {
                    grid.setArmorValue(x, y, Math.min(limit, val + heal));
                }
            }
        }
    }

    // Helper class to store the "memory" of the ship's damage stages
    private static class ShipLifeState {
        int lifeCyclesUsed = 0; // Index for DIMINISHING_CAPS
        boolean readyToDrop = false;
    }

    // Call this in your ModPlugin's onGameLoad or similar to prevent memory leaks between battles
    public static void clearState() {
        SHIP_STATES.clear();
    }

}