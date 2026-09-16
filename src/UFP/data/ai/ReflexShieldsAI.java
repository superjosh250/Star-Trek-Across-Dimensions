package UFP.data.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;
import UFP.data.util.ShieldHitpointManager;

public class ReflexShieldsAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private float timer = 0f;

    private static final float CHECK_INTERVAL = 0.25f;
    private static final int PROJECTILE_THRESHOLD = 3;
    private static final int MISSILE_THRESHOLD = 5;
    private static final float FLUX_THRESHOLD = 0.4f;
    private static final float SHIELD_HP_THRESHOLD = 0.6f;
    private static final float ENEMY_RADIUS = 800f;
    private static final int ENEMY_COUNT_THRESHOLD = 3;

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI ship) {
        this.ship = ship;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || ship == null || !ship.isAlive()) return;

        // FIX: Completely hand control back to the player if they are driving this ship
        if (ship == engine.getPlayerShip()) return;

        timer += amount;
        if (timer < CHECK_INTERVAL) return;
        timer = 0f;

        ShipSystemAPI system = ship.getSystem();
        if (system == null || system.isActive()) return;

        if (shouldActivate(engine)) {
            ship.useSystem();
        }
    }

    private boolean shouldActivate(CombatEngineAPI engine) {
        ShieldAPI shield = ship.getShield();
        if (shield == null || !shield.isOn()) return false;

        int validProjectiles = 0;
        int incomingMissiles = 0;

        for (DamagingProjectileAPI proj : engine.getProjectiles()) {
            if (proj.getDamageTarget() != ship) continue;
            if (!shield.isWithinArc(proj.getLocation())) continue;

            WeaponAPI weapon = proj.getWeapon();
            if (weapon == null) continue;

            WeaponAPI.WeaponType type = weapon.getSpec().getType();
            if (type == WeaponAPI.WeaponType.BALLISTIC ||
                    type == WeaponAPI.WeaponType.MISSILE ||
                    type == WeaponAPI.WeaponType.COMPOSITE) {
                validProjectiles++;
                if (type == WeaponAPI.WeaponType.MISSILE) incomingMissiles++;
            }
        }

        boolean projectileDensity = validProjectiles >= PROJECTILE_THRESHOLD;
        boolean missileSwarm = incomingMissiles >= MISSILE_THRESHOLD;
        boolean surrounded = countNearbyEnemies(engine) >= ENEMY_COUNT_THRESHOLD;

        boolean heavyShieldUse = false;
        if (ship.getVariant().hasHullMod("bubbleShield")) {
            float curHP = ShieldHitpointManager.getShieldHP(ship);
            float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
            if (maxHP > 0f) {
                heavyShieldUse = (curHP / maxHP) <= SHIELD_HP_THRESHOLD;
            }
        } else {
            heavyShieldUse = ship.getFluxTracker().getFluxLevel() >= FLUX_THRESHOLD;
        }

        return projectileDensity || missileSwarm || surrounded || heavyShieldUse;
    }

    private int countNearbyEnemies(CombatEngineAPI engine) {
        int count = 0;
        Vector2f loc = ship.getLocation();
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other.isAlly() || !other.isAlive()) continue;

            float dist = Vector2f.sub(loc, other.getLocation(), null).length();
            if (dist <= ENEMY_RADIUS) count++;
        }
        return count;
    }

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
    }
}