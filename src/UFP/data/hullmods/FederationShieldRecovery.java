package UFP.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import UFP.data.util.BaseShieldHitpointsRecovery;
import UFP.data.util.ShieldHitpointManager;

/**
 * Hullmod that enables shield HP regeneration for Federation-style shields.
 * Works with ShieldHitpointManager / BaseShieldHitpointsRecovery.
 * Does NOT enable ESM logic or keys.
 */
public class FederationShieldRecovery extends BaseHullMod {

    private static final String SHIELD_RECOVERY_LOGGED_KEY = "shield_recovery_logged";
    private static final String RECOVERY_PLUGIN_ADDED_KEY  = "UFP_SH_REC_PLUGIN_ADDED";

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive() || ship.getShield() == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // One-time frame gate: If this ship's recovery flags are configured, escape immediately.
        // This keeps the hullmod at near-zero overhead for the rest of the battle.
        if (Boolean.TRUE.equals(ship.getCustomData().get(SHIELD_RECOVERY_LOGGED_KEY))) {
            return;
        }

        // 1. Initialize recovery engine plugin once per battle
        if (!engine.hasPluginOfClass(BaseShieldHitpointsRecovery.class) &&
                !Boolean.TRUE.equals(engine.getCustomData().get(RECOVERY_PLUGIN_ADDED_KEY))) {

            engine.getCustomData().put(RECOVERY_PLUGIN_ADDED_KEY, true);
            engine.addPlugin(new BaseShieldHitpointsRecovery());
            Global.getLogger(this.getClass()).info("FederationShieldRecovery: BaseShieldHitpointsRecovery plugin initialized.");
        }

        // 2. Safe standalone fallback check
        // If FederationShields isn't on this ship for some reason, initialize the tracking pool.
        // If FederationShields IS present, containsKey("universal_shield_hp") is true, avoiding duplicate damage listeners.
        if (!ship.getCustomData().containsKey("universal_shield_hp")) {
            ShieldHitpointManager.initialize(ship);
            ShieldHitpointManager.registerDamageInterceptor(ship);
            ship.getCustomData().put(FederationShields.UNIVERSAL_HP_PRESENCE_KEY, true);
            Global.getLogger(this.getClass()).info("FederationShieldRecovery: Standalone initialized shield HP for ship: " + ship.getName());
        }

        // 3. Flip the switch to let BaseShieldHitpointsRecovery start managing this ship's passive regeneration
        ship.setCustomData(BaseShieldHitpointsRecovery.UNIVERSAL_SHIELD_RECOVERY_FLAG, true);

        // Close the gatekeeper check for subsequent frames
        ship.setCustomData(SHIELD_RECOVERY_LOGGED_KEY, true);
        Global.getLogger(this.getClass()).info("FederationShieldRecovery: Recovery tracking activated for ship: " + ship.getName());
    }
}