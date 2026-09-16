package UFP.data.ai;

import UFP.data.util.EnergySystemManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class StationReactorAI {

    private static final String KEY_AI_STATE = "UFP_StationReactorAI_State";
    private static final String KEY_DETACHED = "UFP_DetachedTimestamp";

    public static class AIState {
        public boolean isHoldingFire = false;
    }

    public static void advance(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        // If this ship has detached from its station, stop running Station AI logic on it completely
        if (ship.getCustomData().containsKey(KEY_DETACHED)) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(engine, ship);
        if (esm == null || esm.getMaxEnergy() <= 0f) return;

        AIState state = (AIState) ship.getCustomData().get(KEY_AI_STATE);
        if (state == null) {
            state = new AIState();
            ship.getCustomData().put(KEY_AI_STATE, state);
        }

        float energyLevel = esm.getEnergyLevel();

        // Hysteresis Logic: Lock at 0%, release at 25%
        if (state.isHoldingFire) {
            if (energyLevel >= 0.25f) {
                state.isHoldingFire = false;
            }
        } else {
            if (esm.getCurrEnergy() <= 0f || energyLevel <= 0.001f) {
                state.isHoldingFire = true;
            }
        }

        // Direct engine-level weapon suppression for the station and its attached modules
        if (state.isHoldingFire) {
            suppressAllWeapons(ship);
        }
    }

    private static void suppressAllWeapons(ShipAPI targetShip) {
        if (targetShip == null || !targetShip.isAlive()) return;

        for (WeaponAPI weapon : targetShip.getAllWeapons()) {
            if (weapon.getSlot() == null || weapon.getSlot().isSystemSlot() || weapon.getSlot().isDecorative()) {
                continue;
            }
            weapon.setForceNoFireOneFrame(true);
        }

        // Recursively apply suppression ONLY to modules that are still attached to the station
        if (targetShip.getChildModulesCopy() != null) {
            for (ShipAPI module : targetShip.getChildModulesCopy()) {
                if (module == null || !module.isAlive()) continue;

                // Skip detached modules (stamped by ModuleDetachment)
                if (module.getCustomData().containsKey(KEY_DETACHED)) continue;

                // Skip modules that no longer point to this station as parent
                if (module.getParentStation() != targetShip) continue;

                suppressAllWeapons(module);
            }
        }
    }
}