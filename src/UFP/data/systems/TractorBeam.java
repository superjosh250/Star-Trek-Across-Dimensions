package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import UFP.data.plugins.TractorBeamVisual;

public class TractorBeam extends BaseShipSystemScript {

    private TractorBeamVisual visualPlugin;
    private ShipAPI affectedTarget; // ship we last debuffed

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI sourceShip = (ShipAPI) stats.getEntity();
        if (sourceShip == null) return;

        // If we are not actually applying an effect, ensure everything is cleaned up.
        // This stops "always engaged" behavior when apply() gets called with effectLevel == 0
        // or during cooldown-type states.
        if (effectLevel <= 0f || state == State.COOLDOWN) {
            cleanupAll(id);
            return;
        }

        // Need a valid ship target
        CombatEntityAPI target = sourceShip.getShipTarget();
        ShipAPI targetShip = (target instanceof ShipAPI) ? (ShipAPI) target : null;

        if (targetShip == null || targetShip.isHulk() || !targetShip.isAlive()) {
            cleanupAll(id);
            return;
        }

        // If target changed, cleanup old target + visuals
        if (affectedTarget != null && affectedTarget != targetShip) {
            cleanupTarget(id, affectedTarget);
            affectedTarget = null;
            removeVisual();
        }

        affectedTarget = targetShip;
        MutableShipStatsAPI targetStats = targetShip.getMutableStats();

        // Mass ratio logic
        float sourceMass = sourceShip.getMass();
        float targetMass = targetShip.getMass();
        if (targetMass <= 0f) targetMass = 1f;

        float ratio = sourceMass / targetMass;

        float stoppingPower;
        if (ratio >= 1f) stoppingPower = 0.95f;
        else if (ratio >= 0.8f) stoppingPower = 0.9f;
        else if (ratio >= 0.5f) stoppingPower = 0.7f;
        else stoppingPower = 0.5f;

        // Apply only meaningful power; clamp to sane range
        float appliedPower = stoppingPower * effectLevel;
        if (appliedPower < 0f) appliedPower = 0f;
        if (appliedPower > 0.95f) appliedPower = 0.95f;

        // Apply movement penalties
        float mult = 1f - appliedPower;
        targetStats.getMaxSpeed().modifyMult(id, mult);
        targetStats.getAcceleration().modifyMult(id, mult);
        targetStats.getDeceleration().modifyMult(id, mult);
        targetStats.getMaxTurnRate().modifyMult(id, mult);
        targetStats.getTurnAcceleration().modifyMult(id, mult);

        // Reduce current velocity a bit (small continuous damp)
        targetShip.getVelocity().scale(1f - appliedPower * 0.05f);

        // Visual plugin: ensure it exists only while system is active
        if (visualPlugin == null) {
            visualPlugin = new TractorBeamVisual(sourceShip, targetShip);
            Global.getCombatEngine().addPlugin(visualPlugin);
        } else {
            // If your TractorBeamVisual supports retargeting, do it here.
            // Otherwise, the target-change branch above removes/recreates visuals.
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        cleanupAll(id);
    }

    private void cleanupAll(String id) {
        if (affectedTarget != null) {
            cleanupTarget(id, affectedTarget);
            affectedTarget = null;
        }
        removeVisual();
    }

    private void cleanupTarget(String id, ShipAPI targetShip) {
        if (targetShip == null) return;
        MutableShipStatsAPI targetStats = targetShip.getMutableStats();
        targetStats.getMaxSpeed().unmodify(id);
        targetStats.getAcceleration().unmodify(id);
        targetStats.getDeceleration().unmodify(id);
        targetStats.getMaxTurnRate().unmodify(id);
        targetStats.getTurnAcceleration().unmodify(id);
    }

    private void removeVisual() {
        if (visualPlugin != null && Global.getCombatEngine() != null) {
            Global.getCombatEngine().removePlugin(visualPlugin);
            visualPlugin = null;
        }
    }

    // Prevent system use unless a ship target exists
    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null) return false;
        CombatEntityAPI t = ship.getShipTarget();
        return (t instanceof ShipAPI) && ((ShipAPI) t).isAlive() && !((ShipAPI) t).isHulk();
    }

    // Proper info text for READY/NO TARGET/ACTIVE
    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null) return null;
        CombatEntityAPI t = ship.getShipTarget();
        if (!(t instanceof ShipAPI)) return "NO TARGET";
        ShipAPI ts = (ShipAPI) t;
        if (!ts.isAlive() || ts.isHulk()) return "INVALID TARGET";
        if (system != null && system.isActive()) return "TRACTORING";
        return "READY";
    }

    @Override
    public StatusData getStatusData(int index, ShipSystemStatsScript.State state, float effectLevel) {
        // Only show "engaged" while actually applying effect
        if (index == 0 && effectLevel > 0f && state != State.COOLDOWN) {
            return new StatusData("Tractor beam engaged", false);
        }
        return null;
    }
}
