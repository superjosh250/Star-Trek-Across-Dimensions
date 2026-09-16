package UFP.data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;

/**
 * Effects-only tractor beam plugin.
 * Applies the same movement/turn suppression + mild velocity damping as [TractorBeam.java](https://onedrive.live.com?cid=EC0C3F61338E8B0E&id=EC0C3F61338E8B0E!s8372aba006264dc28935490b6096d7cf&EntityRepresentationId=f472b6d2-574d-4670-8cac-3c87f7dcf559),
 * but without any visuals/particles. Designed to be used as a BeamEffectPlugin on a weapon.
 *
 * Usage: set your .wpn beam's "beamEffect" (or equivalent field) to "UFP.data.plugins.TractorEffect".
 */
public class TractorEffect implements BeamEffectPlugin {

    // Unique modifier id prefix so multiple beams/weapons don't collide
    private static final String MOD_PREFIX = "ufp_tractor_effect_";

    // Track what we last modified so we can unmodify reliably
    private ShipAPI lastTarget = null;
    private String lastModId = null;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine == null || beam == null) return;
        if (engine.isPaused()) return;

        CombatEntityAPI srcEntity = beam.getSource();
        CombatEntityAPI tgtEntity = beam.getDamageTarget();

        ShipAPI source = (srcEntity instanceof ShipAPI) ? (ShipAPI) srcEntity : null;
        ShipAPI target = (tgtEntity instanceof ShipAPI) ? (ShipAPI) tgtEntity : null;

        // Brightness is a good proxy for "effect level" for beams
        float effectLevel = clamp01(beam.getBrightness());

        // If no meaningful effect (beam off / not hitting), clean up and exit
        if (effectLevel <= 0f || source == null || target == null || !target.isAlive() || target.isHulk()) {
            cleanup();
            return;
        }

        // If target changed, unapply old target
        if (lastTarget != null && lastTarget != target) {
            cleanup();
        }

        // Build a stable mod id per source + weapon + plugin instance
        // weapon id can be null depending on how beam is spawned; fall back to beam hash
        String weaponId = (beam.getWeapon() != null) ? beam.getWeapon().getId() : null;
        String modId = MOD_PREFIX
                + safeId(source)
                + "_"
                + (weaponId != null ? weaponId : Integer.toHexString(System.identityHashCode(beam)));

        lastTarget = target;
        lastModId = modId;

        // --- Match [TractorBeam.java]
        float sourceMass = source.getMass();
        float targetMass = Math.max(1f, target.getMass());
        float ratio = sourceMass / targetMass;

        float stoppingPower;
        if (ratio >= 1f) stoppingPower = 0.95f;
        else if (ratio >= 0.8f) stoppingPower = 0.9f;
        else if (ratio >= 0.5f) stoppingPower = 0.7f;
        else stoppingPower = 0.5f;

        float appliedPower = stoppingPower * effectLevel;
        appliedPower = clamp(appliedPower, 0f, 0.95f);

        float mult = 1f - appliedPower;

        MutableShipStatsAPI stats = target.getMutableStats();
        stats.getMaxSpeed().modifyMult(modId, mult);
        stats.getAcceleration().modifyMult(modId, mult);
        stats.getDeceleration().modifyMult(modId, mult);
        stats.getMaxTurnRate().modifyMult(modId, mult);
        stats.getTurnAcceleration().modifyMult(modId, mult);

        // --- Match the small continuous velocity damp from TractorBeam ---
        Vector2f vel = target.getVelocity();
        if (vel != null) {
            vel.scale(1f - appliedPower * 0.05f);
        }
    }

    /** Unapply any modifiers we last applied. */
    private void cleanup() {
        if (lastTarget != null && lastModId != null) {
            try {
                MutableShipStatsAPI stats = lastTarget.getMutableStats();
                stats.getMaxSpeed().unmodify(lastModId);
                stats.getAcceleration().unmodify(lastModId);
                stats.getDeceleration().unmodify(lastModId);
                stats.getMaxTurnRate().unmodify(lastModId);
                stats.getTurnAcceleration().unmodify(lastModId);
            } catch (Throwable ignored) {
                // avoid hard failures on edge cases (despawn, etc.)
            }
        }
        lastTarget = null;
        lastModId = null;
    }

    private static String safeId(ShipAPI ship) {
        if (ship == null) return "null";
        String id = ship.getId();
        if (id != null) return id;
        // fallback
        return Integer.toHexString(System.identityHashCode(ship));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}