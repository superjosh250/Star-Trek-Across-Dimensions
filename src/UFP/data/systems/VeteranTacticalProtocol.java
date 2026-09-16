
package UFP.data.systems;

import com.fs.starfarer.api.combat.*;
import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class VeteranTacticalProtocol extends BaseShipSystemScript {


    public static final float WEAPON_DAMAGE_BOOST = 15f;
    public static final float SPEED_BOOST = 5f;
    public static final float TURN_ACCEL_BOOST = 10f;
    public static final float BASE_SHIELD_RESIST = 0.15f; // 15%
    public static final float SHIELD_STEP_RESIST = 0.025f; // 2.5%
    public static final float BASE_HULL_RESIST = 0.10f; // 10%
    public static final float HULL_STEP_RESIST = 0.025f; // 2.5%

    private ShipAPI shipRef;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;
        this.shipRef = ship;

        if (state == State.ACTIVE || state == State.IN || state == State.OUT) {
            stats.getBeamWeaponDamageMult().modifyPercent(id, WEAPON_DAMAGE_BOOST * effectLevel);
            stats.getMissileWeaponDamageMult().modifyPercent(id, WEAPON_DAMAGE_BOOST * effectLevel);
            stats.getBallisticWeaponDamageMult().modifyPercent(id, WEAPON_DAMAGE_BOOST * effectLevel);
            stats.getMaxSpeed().modifyPercent(id, SPEED_BOOST * effectLevel);
            stats.getTurnAcceleration().modifyPercent(id, TURN_ACCEL_BOOST * effectLevel);
            float shieldBuff = calculateShieldBuff(ship) * effectLevel;
            float hullBuff = calculateHullBuff(ship) * effectLevel;
            if (shieldBuff > 0) {
                stats.getShieldDamageTakenMult().modifyMult(id, 1f - shieldBuff);
            } else {
                stats.getShieldDamageTakenMult().unmodify(id);
            }

            if (hullBuff > 0) {
                stats.getHullDamageTakenMult().modifyMult(id, 1f - hullBuff);
            } else {
                stats.getHullDamageTakenMult().unmodify(id);
            }
        } else {
            unapply(stats, id);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBeamWeaponDamageMult().unmodify(id);
        stats.getMissileWeaponDamageMult().unmodify(id);
        stats.getBallisticWeaponDamageMult().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        shipRef = null;
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (shipRef == null || effectLevel <= 0) return null;

        float shieldBuff = calculateShieldBuff(shipRef) * effectLevel * 100f;
        float hullBuff = calculateHullBuff(shipRef) * effectLevel * 100f;

        switch (index) {
            case 0: return new StatusData("Weapon dmg +" + (int)(WEAPON_DAMAGE_BOOST * effectLevel) + "%", false);
            case 1: return new StatusData("Speed +" + (int)(SPEED_BOOST * effectLevel) + "% | Turn +" + (int)(TURN_ACCEL_BOOST * effectLevel) + "%", false);
            case 2: return new StatusData(String.format("Shield resist: %.1f%%", shieldBuff), false);
            case 3: return new StatusData(String.format("Hull resist: %.1f%%", hullBuff), false);
            default: return null;
        }
    }

    @Override
    public float getActiveOverride(ShipAPI shipAPI) { return -1; }
    @Override
    public float getInOverride(ShipAPI shipAPI) { return -1; }
    @Override
    public float getOutOverride(ShipAPI shipAPI) { return -1; }
    @Override
    public int getUsesOverride(ShipAPI shipAPI) { return -1; }
    @Override
    public float getRegenOverride(ShipAPI shipAPI) { return -1; }
    @Override
    public String getDisplayNameOverride(State state, float effectLevel) {
        return null;
    }

    private float calculateShieldBuff(ShipAPI ship) {
        if (ship.getShield() == null) return 0f;
        float currentHP = ShieldHitpointManager.getShieldHP(ship);
        float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
        if (maxHP <= 0f) return 0f;

        float shieldPercent = currentHP / maxHP;
        if (shieldPercent < 0.75f) {
            float missingPercent = (0.75f - shieldPercent) * 100f;
            int steps = (int)(missingPercent / 5f);
            return BASE_SHIELD_RESIST + (steps * SHIELD_STEP_RESIST);
        }
        return 0f;
    }

    private float calculateHullBuff(ShipAPI ship) {
        float hullPercent = ship.getHitpoints() / ship.getMaxHitpoints();
        if (hullPercent < 0.75f) {
            float missingPercent = (0.75f - hullPercent) * 100f;
            int steps = (int)(missingPercent / 5f);
            return BASE_HULL_RESIST + (steps * HULL_STEP_RESIST);
        }
        return 0f;
    }
}
