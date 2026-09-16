package UFP.data.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class SyncedSensorSuiteAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;

    private static final float RANGE = 2000f;
    private static final float ENEMY_FLUX_LOW = 0.5f;
    private static final float ENEMY_FLUX_HIGH = 0.75f;
    private static final float ENEMY_HP_LOW = 0.75f;
    private static final float ENEMY_HP_CRITICAL = 0.25f;

    private static final String ACTIVE_AI_KEY = "synced_sensor_suite_ai_user";

    private IntervalUtil interval = new IntervalUtil(0.5f, 1.0f);
    private Random random = new Random();

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.flags = flags;
        this.engine = engine;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine == null || ship == null || system == null || !ship.isAlive()) return;

        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        if (system.getCooldownRemaining() > 0 || system.isOutOfAmmo()) return;
        if (ship.getFluxTracker().isOverloadedOrVenting()) return;

        // Check if this ship is player-controlled
        if (ship.getOriginalOwner() == 0 && ship.getShipAI() == null) return;

        // Check if another AI ship is already using the system
        Object activeUserObj = Global.getCombatEngine().getCustomData().get(ACTIVE_AI_KEY);
        if (activeUserObj instanceof ShipAPI) {
            ShipAPI activeUser = (ShipAPI) activeUserObj;
            if (activeUser != null && activeUser != ship && activeUser.isAlive() && activeUser.getSystem() != null &&
                    activeUser.getSystem().isActive()) {
                return; // Another AI ship is already using the system
            } else {
                // Clear stale reference
                Global.getCombatEngine().getCustomData().remove(ACTIVE_AI_KEY);
            }
        }

        boolean allyInRange = false;
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other.isFighter() || other.isDrone()) continue;
            if (other.getOwner() != ship.getOwner()) continue;
            if (!other.isAlive()) continue;

            float distance = Misc.getDistance(ship.getLocation(), other.getLocation());
            if (distance <= RANGE) {
                allyInRange = true;
                break;
            }
        }

        float shipWeaponRange = getMaxWeaponRange(ship);

        boolean validEnemyTargetInRange = false;
        for (ShipAPI enemy : engine.getShips()) {
            if (enemy == null || enemy.isFighter() || enemy.isDrone()) continue;
            if (enemy.getOwner() == ship.getOwner()) continue;
            if (!enemy.isAlive()) continue;

            boolean hasBubbleShield = enemy.getVariant().hasHullMod("bubbleShield");
            float fluxLevel = enemy.getFluxLevel();
            float hpRatio = enemy.getHitpoints() / enemy.getMaxHitpoints();

            boolean fluxTrigger = !hasBubbleShield && (fluxLevel >= ENEMY_FLUX_LOW || fluxLevel >= ENEMY_FLUX_HIGH);
            boolean hpTrigger = hasBubbleShield && (hpRatio <= ENEMY_HP_LOW || hpRatio <= ENEMY_HP_CRITICAL);

            float distanceToShip = Misc.getDistance(ship.getLocation(), enemy.getLocation());
            boolean inWeaponRangeOfShip = distanceToShip <= shipWeaponRange;

            boolean inWeaponRangeOfAlly = false;
            if (allyInRange) {
                for (ShipAPI ally : engine.getShips()) {
                    if (ally == ship || ally.isFighter() || ally.isDrone()) continue;
                    if (ally.getOwner() != ship.getOwner()) continue;
                    if (!ally.isAlive()) continue;

                    float allyWeaponRange = getMaxWeaponRange(ally);
                    float distanceToAlly = Misc.getDistance(ally.getLocation(), enemy.getLocation());
                    if (distanceToAlly <= allyWeaponRange) {
                        inWeaponRangeOfAlly = true;
                        break;
                    }
                }
            }

            if ((fluxTrigger || hpTrigger) && (inWeaponRangeOfShip || inWeaponRangeOfAlly)) {
                validEnemyTargetInRange = true;
                break;
            }
        }

        boolean shouldActivate = allyInRange && validEnemyTargetInRange;

        // Add randomness to activation
        boolean randomChance = random.nextFloat() < 0.4f; // 40% chance

        if (shouldActivate && randomChance && system.getState() == ShipSystemAPI.SystemState.IDLE) {
            ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0);
            Global.getCombatEngine().getCustomData().put(ACTIVE_AI_KEY, ship);
        }
    }

    private float getMaxWeaponRange(ShipAPI ship) {
        float maxRange = 0f;
        for (WeaponAPI weapon : ship.getUsableWeapons()) {
            if (weapon == null || weapon.isDisabled() || weapon.getType() == WeaponAPI.WeaponType.SYSTEM) continue;
            maxRange = Math.max(maxRange, weapon.getRange());
        }
        return maxRange;
    }
}
