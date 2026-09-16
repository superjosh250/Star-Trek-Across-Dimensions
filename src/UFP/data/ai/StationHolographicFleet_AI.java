package UFP.data.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class StationHolographicFleet_AI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private ShipwideAIFlags flags;
    private CombatEngineAPI engine;

    // Fast check interval for local threats (0.3 - 0.4s)
    private final IntervalUtil localTracker = new IntervalUtil(0.3f, 0.4f);

    // Slow check interval for full combat grid scanning (2.0 - 3.0s)
    private final IntervalUtil gridTracker = new IntervalUtil(2.0f, 3.0f);

    // Cached grid calculation results
    private boolean gridTriggerMet = false;

    public void init(ShipAPI ship, ShipSystemAPI system, Object params, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.engine = engine;
    }

    @Override
    public void init(ShipAPI shipAPI, ShipSystemAPI shipSystemAPI, ShipwideAIFlags shipwideAIFlags, CombatEngineAPI combatEngineAPI) {
        this.ship = shipAPI;
        this.system = shipSystemAPI;
        this.flags = shipwideAIFlags;
        this.engine = combatEngineAPI;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (ship == null || system == null || engine == null) return;
        if (system.isActive() || system.getCooldownRemaining() > 0f) return;

        localTracker.advance(amount);
        if (!localTracker.intervalElapsed()) return;

        // Fast Fail: Check life and flux status first before doing ANY calculations
        if (!ship.isAlive() || ship.getFluxTracker().isOverloadedOrVenting()) return;

        boolean isStation = ship.isStation() || ship.isStationModule();
        float fluxLevel = ship.getFluxLevel();

        // 1. NON-STATION SAFETY: High flux check (Stop immediately if flux > 60% due to 15x flux upkeep penalty)
        if (!isStation && fluxLevel > 0.60f) return;

        // 2. SLOW GRID DENSITY SCAN (Runs once every 2-3 seconds instead of every frame)
        gridTracker.advance(localTracker.getElapsed());
        if (gridTracker.intervalElapsed()) {
            gridTriggerMet = checkGridDensity();
        }

        if (gridTriggerMet) {
            ship.useSystem();
            return;
        }

        // 3. DEFENSIVE TRIGGER: Heavy missile/fighter threat or high hull pressure
        if (missileDangerDir != null || ship.getHullLevel() < 0.75f) {
            ship.useSystem();
            return;
        }

        // 4. OFFENSIVE TRIGGER: In combat with an enemy target nearby (< 2000 units)
        if (target != null && target.isAlive() && target.getOwner() != ship.getOwner()) {
            float distSq = Misc.getDistanceSq(ship.getLocation(), target.getLocation());
            if (distSq < 4000000f) { // 2000 * 2000 pre-calculated
                boolean isCritical = flags != null && flags.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER);
                if (target.getFluxLevel() > 0.40f || isCritical) {
                    ship.useSystem();
                    return;
                }
            }
        }

        // 5. GENERAL COMBAT TRIGGER: Under fire while surrounded by active enemy ships
        boolean needsHelp = flags != null && flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP);
        if (needsHelp || fluxLevel > 0.30f) {
            ship.useSystem();
        }
    }

    /**
     * Heavy scan method executed strictly on the slow 2.0s - 3.0s timer.
     */
    private boolean checkGridDensity() {
        int capitalCount = 0;
        int enemyCount = 0;
        int owner = ship.getOwner();

        List<ShipAPI> shipsOnMap = engine.getShips();
        for (int i = 0; i < shipsOnMap.size(); i++) {
            ShipAPI s = shipsOnMap.get(i);
            if (s == null || !s.isAlive() || s.isFighter() || s.isHulk()) continue;

            if (s.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
                capitalCount++;
            }

            if (s.getOwner() != owner) {
                enemyCount++;
            }

            // Early exit if criteria is already satisfied to avoid processing remaining ships in huge battles
            if (capitalCount >= 4 || enemyCount >= 10) {
                return true;
            }
        }

        return false;
    }
}