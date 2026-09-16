package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.EnumSet;

public class RomulanCloak extends BaseShipSystemScript {

    private float lastDamageTime = 0f;
    private final IntervalUtil checkInterval = new IntervalUtil(0.1f, 0.2f);
    private boolean wasForceDecloaked = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();
        checkInterval.advance(engine.getElapsedInLastFrame());

        if (ship == null || ship.getSystem() == null || ship.getSystem().getState() != ShipSystemAPI.SystemState.IN) {
            lastDamageTime = ship.getSinceLastDamageTaken();
        }

        // Handle forced decloak logic
        if (wasForceDecloaked && ship.getShield() != null) {
            ship.getShield().toggleOff(); // Suppress shield manually
            stats.getShieldUnfoldRateMult().modifyMult(id, 0f);
            stats.getShieldUpkeepMult().modifyMult(id, 0f);
            wasForceDecloaked = false; // Reset flag
        }

        // Shield accessibility logic
        if (ship.getShield() != null) {
            switch (state) {
                case OUT: // DECLOAKING
                    ship.getShield().toggleOff(); // Only suppress shield during decloak
                    stats.getShieldUnfoldRateMult().modifyMult(id, 0f);
                    stats.getShieldUpkeepMult().modifyMult(id, 0f);
                    break;

                case IDLE:
                case COOLDOWN:
                    stats.getShieldUnfoldRateMult().unmodify(id);
                    stats.getShieldUpkeepMult().unmodify(id);
                    break;

                default:
                    break;
            }
        }

        // Cloak logic
        if (state == ShipSystemStatsScript.State.ACTIVE) {
            if (checkInterval.intervalElapsed()) {
                float timeSinceHit = ship.getSinceLastDamageTaken();
                if (timeSinceHit < lastDamageTime) {
                    if (!wasHitByAsteroid(ship, engine)) {
                        wasForceDecloaked = true;
                        ship.getSystem().deactivate(); // Decloak on hit unless asteroid
                        return;
                    }
                }
                lastDamageTime = timeSinceHit;
            }

            boolean isPlayer = ship == engine.getPlayerShip();
            boolean isAlly = ship.getOwner() == 0;

            // Visibility logic
            float alpha = (isPlayer || isAlly) ? 0.3f : 0f;
            ship.setAlphaMult(alpha);
            ship.setApplyExtraAlphaToEngines(true);
            ship.setPhased(true);
            ship.setCollisionClass(CollisionClass.SHIP);
            ship.setOwner(100); // Prevent friendly fire

            // Remove target lock from enemies
            for (ShipAPI otherShip : engine.getShips()) {
                if (otherShip.getOwner() != ship.getOwner()) {
                    if (otherShip.getShipTarget() == ship) {
                        otherShip.setShipTarget(null);
                    }
                }
            }

            // Suppress visual effects using fadeToOtherColor
            ship.getEngineController().fadeToOtherColor(
                    this,
                    new Color(0, 0, 0, 0), // engine glow color
                    new Color(0, 0, 0, 0), // contrail color
                    1f, // effect level
                    1f  // max blend
            );

            ship.setWeaponGlow(0f, Color.BLACK, EnumSet.allOf(WeaponAPI.WeaponType.class));
            ship.setJitter(this, new Color(0, 0, 0, 0), 0f, 0, 0f);

            // Afterimage effect
            ship.addAfterimage(
                    Color.BLACK,
                    ship.getLocation().x,
                    ship.getLocation().y,
                    ship.getVelocity().x,
                    ship.getVelocity().y,
                    5f, 0.5f, 0.5f, 1f, true, true, false
            );
        } else {
            unapply(stats, id);
        }
    }

    private boolean wasHitByAsteroid(ShipAPI ship, CombatEngineAPI engine) {
        for (CombatEntityAPI asteroid : engine.getAsteroids()) {
            float distance = Misc.getDistance(ship.getLocation(), asteroid.getLocation());
            float collisionThreshold = ship.getCollisionRadius() + asteroid.getCollisionRadius();

            if (distance < collisionThreshold) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        ship.setAlphaMult(1f);
        ship.setApplyExtraAlphaToEngines(false);
        ship.setPhased(false);
        ship.setCollisionClass(CollisionClass.SHIP);
        ship.setOwner(ship.getOriginalOwner());

        if (ship.getShield() != null) {
            stats.getShieldUnfoldRateMult().unmodify(id);
            stats.getShieldUpkeepMult().unmodify(id);
        }
    }

    @Override
    public StatusData getStatusData(int index, ShipSystemStatsScript.State state, float effectLevel) {
        if (index == 0) {
            if (state == ShipSystemStatsScript.State.ACTIVE) {
                return new StatusData("Cloaked", false);
            } else if (state == ShipSystemStatsScript.State.OUT || wasForceDecloaked) {
                return new StatusData("Decloaking - shields offline", false);
            }
        }
        return null;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system == null || ship == null) return null;

        ShipSystemAPI.SystemState state = system.getState();

        switch (state) {
            case ACTIVE:
                return "CLOAKED";
            case IN:
                return "ENGAGING";
            case OUT:
                return "DECLOAKING";
            case COOLDOWN:
                return "COOLDOWN";
            case IDLE:
            default:
                return "READY";
        }
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return ship != null && ship.isAlive();
    }
}