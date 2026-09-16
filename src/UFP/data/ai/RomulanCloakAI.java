package UFP.data.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class RomulanCloakAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI cloakSystem;
    private ShipwideAIFlags flags;
    private IntervalUtil interval = new IntervalUtil(0.2f, 0.4f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.cloakSystem = system;
        this.flags = flags;
    }

    @Override
    public void advance(float amount, Vector2f missileTarget, Vector2f shipTarget, ShipAPI ship) {
        if (cloakSystem == null || ship == null || !ship.isAlive()) return;

        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        ShipAPI target = ship.getShipTarget();

        boolean canCloak = cloakSystem.getState() == ShipSystemAPI.SystemState.IDLE;
        boolean isCloaked = cloakSystem.getState() == ShipSystemAPI.SystemState.ACTIVE;

        if (target != null && target.isAlive()) {
            float distanceToTarget = Misc.getDistance(ship.getLocation(), target.getLocation());
            boolean targetVulnerable = target.getFluxTracker().isOverloadedOrVenting() || target.getShield() == null || !target.getShield().isOn();
            boolean behindTarget = distanceToTarget < 600f &&
                    Math.abs(Misc.getAngleInDegrees(ship.getLocation(), target.getLocation()) - target.getFacing()) > 120f;

            boolean inWeaponRange = distanceToTarget < getMaxWeaponRange(target);

            // Don't cloak if in weapon range — wait until out of danger
            if (canCloak && !isCloaked && !targetVulnerable && !inWeaponRange) {
                ship.useSystem(); // Cloak to reposition
                return;
            }

            // Decloak when behind and target is vulnerable
            if (isCloaked && targetVulnerable && behindTarget) {
                cloakSystem.deactivate(); // Strike!
                return;
            }


            // If cloaked and not ready to strike, move away or flank
            if (isCloaked && (!targetVulnerable || !behindTarget)) {
                flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS, 1f);
                flags.setFlag(ShipwideAIFlags.AIFlags.MANEUVER_TARGET, 1f, target);
                flags.setFlag(ShipwideAIFlags.AIFlags.BACK_OFF, 1f);
                return;
            }

        }

        // Defensive fallback
        boolean underThreat = ship.getFluxLevel() > 0.85f || ship.getHullLevel() < 0.4f;
        if (canCloak && underThreat) {
            ship.useSystem(); // Emergency cloak
        }
    }

    private float getMaxWeaponRange(ShipAPI ship) {
        float maxRange = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getSpec() != null) {
                maxRange = Math.max(maxRange, weapon.getSpec().getMaxRange());
            }
        }
        return maxRange;
    }
}