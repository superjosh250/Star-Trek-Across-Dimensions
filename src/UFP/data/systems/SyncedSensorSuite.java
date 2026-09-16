
package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import UFP.data.plugins.SyncedSensorSuiteVisualEffect;
import UFP.data.util.ShieldHitpointManager;

import java.awt.*;
import java.util.List;

public class SyncedSensorSuite extends BaseShipSystemScript {

    private static final float ALLY_RANGE = 4000f;

    // Self buffs
    private static final float SELF_SPEED_BONUS = 0.50f;
    private static final float SELF_ACCEL_BONUS = 0.15f;
    private static final float SELF_SHIELD_DAMAGE_REDUCTION = 0.15f;
    private static final float SELF_SENSOR_RANGE_BONUS = 0.40f;
    private static final float SELF_SHIELD_RESTORE_PCT = 0.20f;

    // Ally buffs
    private static final float ALLY_ENERGY_DAMAGE_BONUS = 0.20f;
    private static final float ALLY_RANGE_BONUS = 0.15f;
    private static final float ALLY_DAMAGE_REDUCTION = 0.10f;
    private static final float ALLY_SENSOR_RANGE_BONUS = 0.20f;

    private boolean shieldRestored = false;
    private SyncedSensorSuiteVisualEffect visualEffect;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        if (state == State.ACTIVE && effectLevel > 0f) {
            // Mark system active for visual effect
            ship.setCustomData("synced_sensor_suite_active", true);

            // Determine if this ship is the primary emitter
            boolean isPrimaryEmitter = true;
            for (ShipAPI other : engine.getShips()) {
                if (other == ship || other.isFighter() || other.isDrone()) continue;
                if (other.getOwner() != ship.getOwner()) continue;

                ShipSystemAPI otherSystem = other.getSystem();
                if (otherSystem != null && otherSystem.isActive() && otherSystem.getId().equals(ship.getSystem().getId())) {
                    if (other.getId().compareTo(ship.getId()) < 0) {
                        isPrimaryEmitter = false;
                        break;
                    }
                }
            }

            // Initialize visual effect if not present
            if (visualEffect == null || visualEffect.isExpired()) {
                visualEffect = new SyncedSensorSuiteVisualEffect();
                visualEffect.init(ship);
                visualEffect.setPrimaryEmitter(isPrimaryEmitter);
                engine.addLayeredRenderingPlugin(visualEffect);
            }

            // Self buffs
            stats.getMaxSpeed().modifyMult(id + "_speed", 1f + SELF_SPEED_BONUS * effectLevel);
            stats.getAcceleration().modifyMult(id + "_accel", 1f + SELF_ACCEL_BONUS * effectLevel);
            stats.getShieldDamageTakenMult().modifyMult(id + "_shield", 1f - SELF_SHIELD_DAMAGE_REDUCTION * effectLevel);
            stats.getSightRadiusMod().modifyMult(id + "_sensor", 1f + SELF_SENSOR_RANGE_BONUS * effectLevel);

            // Restore shield HP once per activation
            if (!shieldRestored) {
                float restoreAmt = ShieldHitpointManager.getMaxShieldHP(ship) * SELF_SHIELD_RESTORE_PCT;
                ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + restoreAmt);
                engine.addFloatingText(ship.getLocation(), String.format("+%.0f%% Shield HP Restored", SELF_SHIELD_RESTORE_PCT * 100f),
                        12f, Color.CYAN, ship, 0.5f, 0.5f);
                shieldRestored = true;
            }

            // Ally buffs within range
            List<ShipAPI> ships = engine.getShips();
            for (ShipAPI other : ships) {
                if (other == ship || other.isFighter() || other.isDrone()) continue;
                if (other.getOwner() != ship.getOwner()) continue;

                float distance = Misc.getDistance(ship.getLocation(), other.getLocation());
                if (distance > ALLY_RANGE) continue;

                MutableShipStatsAPI allyStats = other.getMutableStats();
                allyStats.getEnergyWeaponDamageMult().modifyMult(id + "_ally_energy", 1f + ALLY_ENERGY_DAMAGE_BONUS);
                allyStats.getBallisticWeaponRangeBonus().modifyMult(id + "_ally_range_ballistic", 1f + ALLY_RANGE_BONUS);
                allyStats.getEnergyWeaponRangeBonus().modifyMult(id + "_ally_range_energy", 1f + ALLY_RANGE_BONUS);
                allyStats.getMissileWeaponRangeBonus().modifyMult(id + "_ally_range_missile", 1f + ALLY_RANGE_BONUS);
                allyStats.getHullDamageTakenMult().modifyMult(id + "_ally_damage_reduction", 1f - ALLY_DAMAGE_REDUCTION);
                allyStats.getSightRadiusMod().modifyMult(id + "_ally_sensor", 1f + ALLY_SENSOR_RANGE_BONUS);
            }
        } else {
            // System inactive: cleanup visual effect and custom data
            ship.removeCustomData("synced_sensor_suite_active");
            if (visualEffect != null) {
                visualEffect.cleanup();
                visualEffect = null;
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // Remove self buffs
        stats.getMaxSpeed().unmodify(id + "_speed");
        stats.getAcceleration().unmodify(id + "_accel");
        stats.getShieldDamageTakenMult().unmodify(id + "_shield");
        stats.getSightRadiusMod().unmodify(id + "_sensor");

        // Remove ally buffs
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null) {
            for (ShipAPI other : engine.getShips()) {
                if (other.getOwner() == ship.getOwner()) {
                    MutableShipStatsAPI allyStats = other.getMutableStats();
                    allyStats.getEnergyWeaponDamageMult().unmodify(id + "_ally_energy");
                    allyStats.getBallisticWeaponRangeBonus().unmodify(id + "_ally_range_ballistic");
                    allyStats.getEnergyWeaponRangeBonus().unmodify(id + "_ally_range_energy");
                    allyStats.getMissileWeaponRangeBonus().unmodify(id + "_ally_range_missile");
                    allyStats.getHullDamageTakenMult().unmodify(id + "_ally_damage_reduction");
                    allyStats.getSightRadiusMod().unmodify(id + "_ally_sensor");
                }
            }
        }

        // Cleanup visual effect
        if (visualEffect != null) {
            visualEffect.cleanup();
            visualEffect = null;
        }

        shieldRestored = false; // Reset for next activation
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.ACTIVE) {
            if (index == 0) {
                return new StatusData(
                        String.format("Self: +%.0f%% speed, +%.0f%% accel, -%.0f%% shield dmg",
                                SELF_SPEED_BONUS * 100f, SELF_ACCEL_BONUS * 100f, SELF_SHIELD_DAMAGE_REDUCTION * 100f),
                        false
                );
            }
            if (index == 1) {
                return new StatusData(
                        String.format("Allies: +%.0f%% energy dmg, +%.0f%% range, -%.0f%% dmg",
                                ALLY_ENERGY_DAMAGE_BONUS * 100f, ALLY_RANGE_BONUS * 100f, ALLY_DAMAGE_REDUCTION * 100f),
                        false
                );
            }
        }
        return null;
    }
}
