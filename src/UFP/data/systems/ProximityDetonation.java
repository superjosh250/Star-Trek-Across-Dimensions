package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class ProximityDetonation extends BaseShipSystemScript {

    // CONFIG
    private static final float PROX_RANGE_MIN = 100f;      // added to collision radius
    private static final float PROX_RANGE_MAX = 200f;
    private static final float AOE_RADIUS = 1000f;         // AoE radius
    private static final float CHECK_INTERVAL_MIN = 0.05f;
    private static final float CHECK_INTERVAL_MAX = 0.10f;

    private final IntervalUtil interval = new IntervalUtil(CHECK_INTERVAL_MIN, CHECK_INTERVAL_MAX);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI)
                ? (ShipAPI) stats.getEntity()
                : null;
        if (ship == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();

        interval.advance(engine.getElapsedInLastFrame());
        if (!interval.intervalElapsed()) return;

        // Correct system state: only IN and ACTIVE count
        boolean systemActive =
                ship.getSystem() != null &&
                        (ship.getSystem().getState() == ShipSystemAPI.SystemState.IN ||
                                ship.getSystem().getState() == ShipSystemAPI.SystemState.ACTIVE);

        // Tag missiles fired WHILE system is active
        if (systemActive) {
            for (MissileAPI m : engine.getMissiles()) {
                if (m.getOwner() != ship.getOwner()) continue;
                if (!m.isArmed() || m.isFading()) continue;
                if (m.getCustomData().containsKey("prox_enabled")) continue;

                m.setCustomData("prox_enabled", true);
            }
        }

        // Proximity logic for tagged missiles
        for (MissileAPI m : engine.getMissiles()) {
            if (m.getCustomData().containsKey("prox_enabled")) {
                handleProximityDetonation(m, engine);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) { }

    private void handleProximityDetonation(MissileAPI missile, CombatEngineAPI engine) {

        ShipAPI target = findClosestEnemy(missile, engine);
        if (target == null) return;

        float shipRadius = target.getCollisionRadius();
        float extraRange = MathUtils.getRandomNumberInRange(PROX_RANGE_MIN, PROX_RANGE_MAX);
        float triggerRange = shipRadius + extraRange;

        float dist = MathUtils.getDistance(target, missile.getLocation());
        if (dist <= triggerRange) {

            Vector2f loc = missile.getLocation();

            spawnExplosion(loc, engine);
            applyAOEDamage(loc, missile, engine);

            missile.explode();
            missile.setArmingTime(9999f);
        }
    }

    private ShipAPI findClosestEnemy(MissileAPI missile, CombatEngineAPI engine) {
        ShipAPI closest = null;
        float closestDist = Float.MAX_VALUE;

        for (ShipAPI s : engine.getShips()) {
            if (s.getOwner() == missile.getOwner()) continue;
            if (s.isHulk()) continue;

            float dist = MathUtils.getDistance(s.getLocation(), missile.getLocation());
            if (dist < closestDist) {
                closestDist = dist;
                closest = s;
            }
        }
        return closest;
    }

    // =========================================================
    // FX SECTION
    // =========================================================

    // Strong bright radius ring for clear visibility
    private void spawnRadiusRing(Vector2f loc, CombatEngineAPI engine) {
        engine.addSmoothParticle(
                loc,
                new Vector2f(),
                AOE_RADIUS * 2f,        // full diameter
                1.35f,                  // strong brightness
                0.40f,                  // long enough to be highly visible
                new java.awt.Color(255, 255, 180, 200)
        );
    }

    // Dark outline halo for contrast
    private void spawnDarkOutline(Vector2f loc, CombatEngineAPI engine) {
        engine.addSmoothParticle(
                loc,
                new Vector2f(),
                AOE_RADIUS * 2.15f,
                0.9f,
                0.30f,
                new java.awt.Color(0, 0, 0, 180)
        );
    }

    // BIGGER, BRIGHTER SHOCKWAVE
    private void spawnShockwave(Vector2f loc, CombatEngineAPI engine) {
        engine.addHitParticle(
                loc,
                new Vector2f(),
                AOE_RADIUS * 0.55f,
                1.4f,
                0.30f,
                new java.awt.Color(255, 220, 80, 255)
        );
    }

    private void spawnExpandingRing(Vector2f loc, CombatEngineAPI engine) {
        for (int i = 0; i < 6; i++) {
            float size = (i + 1) * (AOE_RADIUS / 6f);
            float brightness = 1f - (i * 0.15f);
            float duration = 0.25f + (i * 0.05f);

            engine.addSmoothParticle(
                    loc,
                    new Vector2f(),
                    size,
                    brightness,
                    duration,
                    new java.awt.Color(255, 180 - (i * 20), 60, 200 - (i * 20))
            );
        }
    }

    private void spawnExplosion(Vector2f loc, CombatEngineAPI engine) {

        // Center flash
        engine.addSmoothParticle(
                loc,
                new Vector2f(),
                320f,
                1.2f,
                0.40f,
                new java.awt.Color(255, 160, 60, 255)
        );

        // Main explosion
        engine.spawnExplosion(
                loc,
                new Vector2f(),
                new java.awt.Color(255, 170, 90),
                AOE_RADIUS,
                0.45f
        );

        // NEW FX
        spawnDarkOutline(loc, engine);
        spawnRadiusRing(loc, engine);
        spawnShockwave(loc, engine);

        // Original secondary ring pulses
        spawnExpandingRing(loc, engine);
    }

    // =========================================================
    // DAMAGE SECTION
    // =========================================================

    private void applyAOEDamage(Vector2f loc, MissileAPI source, CombatEngineAPI engine) {

        float baseDamage = source.getDamageAmount();

        engine.applyDamage(
                source,
                loc,
                baseDamage,
                DamageType.HIGH_EXPLOSIVE,
                baseDamage * 0.5f,
                false, false,
                false
        );

        for (ShipAPI s : engine.getShips()) {
            if (s.isHulk()) continue;

            float dist = MathUtils.getDistance(s.getLocation(), loc);
            if (dist > AOE_RADIUS) continue;

            float damageMult = computeDamagePercent(dist);
            float finalDamage = baseDamage * damageMult;

            if (finalDamage <= 0f) continue;

            engine.applyDamage(
                    s,
                    loc,
                    finalDamage,
                    DamageType.HIGH_EXPLOSIVE,
                    finalDamage * 0.5f,
                    false, false,
                    source.getSource()
            );
        }
    }

    private float computeDamagePercent(float dist) {
        if (dist <= 200f) return 1f;

        if (dist <= 500f) {
            float t = (dist - 200f) / 300f;
            return lerp(1f, 0.75f, t);
        }

        if (dist <= 750f) {
            float t = (dist - 500f) / 250f;
            return lerp(0.75f, 0.50f, t);
        }

        if (dist <= 1000f) {
            float t = (dist - 750f) / 250f;
            return lerp(0.50f, 0.25f, t);
        }

        return 0f;
    }

    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return true;
    }
}