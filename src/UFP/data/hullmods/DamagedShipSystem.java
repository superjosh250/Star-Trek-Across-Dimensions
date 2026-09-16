package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;

public class DamagedShipSystem extends BaseHullMod {

    private static final String KEY_WAS_ACTIVE = "UFP_DamagedSys_WasActive";
    private static final String KEY_EXHAUSTED = "UFP_DamagedSys_Exhausted";
    private static final String KEY_INIT = "UFP_DamagedSys_Init";

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Zero out ammo regeneration rate for systems with reload mechanics
        stats.getSystemRegenBonus().modifyMult(id, 0f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        ShipSystemAPI system = ship.getSystem();
        if (system == null) return;

        boolean usesAmmo = system.getSpecAPI() != null && system.getSpecAPI().usesAmmo();

        // 1. INITIALIZATION: Cap ammo-based systems to 1 starting charge
        if (!ship.getCustomData().containsKey(KEY_INIT)) {
            ship.getCustomData().put(KEY_INIT, true);
            if (usesAmmo && system.getAmmo() > 1) {
                system.setAmmo(1);
            }
        }

        // 2. EXHAUSTION LOCKOUT: Permanently block re-activation with clean UI clamping
        if (ship.getCustomData().containsKey(KEY_EXHAUSTED)) {
            if (usesAmmo) {
                system.setAmmo(0);
            }
            if (system.getState() == SystemState.IDLE || system.isActive()) {
                system.deactivate();
                system.forceState(SystemState.COOLDOWN, 0f);
            }
            // Match total and remaining duration to keep the HUD bar cleanly filled
            system.setCooldown(999f);
            system.setCooldownRemaining(999f);
            return;
        }

        // 3. ACTIVATION TRACKING: Detect when the single activation finishes
        boolean isActiveNow = system.isActive()
                || system.getState() == SystemState.IN
                || system.getState() == SystemState.ACTIVE
                || system.getState() == SystemState.OUT;

        if (isActiveNow) {
            ship.getCustomData().put(KEY_WAS_ACTIVE, true);
        } else if (ship.getCustomData().containsKey(KEY_WAS_ACTIVE)) {
            // First use completed -> Lock out permanently
            ship.getCustomData().put(KEY_EXHAUSTED, true);
            if (usesAmmo) {
                system.setAmmo(0);
            }
            system.deactivate();
            system.forceState(SystemState.COOLDOWN, 0f);
            system.setCooldown(999f);
            system.setCooldownRemaining(999f);
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "1";
        return null;
    }
}