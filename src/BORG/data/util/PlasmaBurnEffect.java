package BORG.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;


//Usable as a beamEffect (BeamEffectPlugin)
//Usable as an onHitEffect (OnHitEffectPlugin)
public class PlasmaBurnEffect implements BeamEffectPlugin, OnHitEffectPlugin {


    public static final String PLASMA_BURN_ACTIVE_KEY = "borg_plasmaBurn_active_until";
    public static final String PLASMA_ARMOR_TAKEN_ID = "borg_plasmaBurn_armorTaken";
    public static final String PLASMA_HULL_TAKEN_ID = "borg_plasmaBurn_hullTaken";
    public static final String PLASMA_MANAGER_KEY = "borg_plasmaBurn_manager_registered";


    public static final float DURATION = 10f;
    public static final float ARMOR_DAMAGE_TAKEN_MULT = 1.40f;
    public static final float HULL_DAMAGE_TAKEN_MULT  = 1.25f;
    public static final float CHANCE_IF_SHIELDS_UP = 0.01f;
    public static final float CHANCE_ON_HIT        = 0.05f;

    public static final float CHANCE_ON_HIT_HIGH_EXPLOSIVE = 0.25f;

    // Visual tuning
    private static final Color OVERLAY    = new Color(130, 255, 130, 100); // faint green
    private static final Color SMOKE      = new Color(140, 220, 140, 95);  // now USED
    private static final Color ARC_CORE   = new Color(170, 255, 170, 160);
    private static final Color ARC_FRINGE = new Color(90,  200,  90, 200);

    // EMP arc visual tuning
    private static final int   ARC_MIN_PER_TICK = 2;
    private static final int   ARC_MAX_PER_TICK = 4; // inclusive
    private static final float ARC_THICKNESS    = 12f;

    // Smoke tuning
    private static final float SMOKE_SIZE_MIN   = 22f;
    private static final float SMOKE_SIZE_ADD   = 30f;
    private static final float SMOKE_END_MULT   = 1.55f;
    private static final float SMOKE_RAMP_UP    = 0.10f;
    private static final float SMOKE_FULL_BRT   = 0.25f;
    private static final float SMOKE_DUR_MIN    = 0.85f;
    private static final float SMOKE_DUR_ADD    = 0.55f;

    private static final Random RNG = new Random();

    // Beam: throttle application checks so we don't roll RNG every frame continuously.
    private final IntervalUtil beamCheckInterval = new IntervalUtil(0.15f, 0.25f);

    // ==========================================================
    // 1) BEAM EFFECT ENTRYPOINT (weapon_data "beamEffect")
    // ==========================================================
    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine == null || beam == null) return;

        beamCheckInterval.advance(amount);
        if (!beamCheckInterval.intervalElapsed()) return;

        // only roll when beam is actively dealing damage this frame (reduces spam)
        if (!beam.didDamageThisFrame()) return;

        CombatEntityAPI t = beam.getDamageTarget();
        if (!(t instanceof ShipAPI target)) return;
        if (!target.isAlive() || target.isHulk()) return;

        // Apply using the shared rules
        tryApply(target, isShieldsUp(target));
    }

    // ==========================================================
    // 2) PROJECTILE ON-HIT ENTRYPOINT (OnHitEffectPlugin)
    // ==========================================================
    // Helper signature (kept for compatibility with older calling patterns)
    public void onHit(DamagingProjectileAPI projectile,
                      CombatEntityAPI target,
                      Vector2f point,
                      boolean shieldHit,
                      CombatEngineAPI engine) {

        if (projectile == null) return;
        if (!(target instanceof ShipAPI ship)) return;
        if (!ship.isAlive() || ship.isHulk()) return;

        // HIGH_EXPLOSIVE projectile => fixed 25% chance
        if (projectile.getDamageType() == DamageType.HIGH_EXPLOSIVE) {
            if (!isActive(ship) && RNG.nextFloat() <= CHANCE_ON_HIT_HIGH_EXPLOSIVE) {
                applyEffect(ship);
            }
            return;
        }

        // Existing behavior: If shieldHit OR shields currently on => 1% chance, else 5% chance.
        boolean shieldsUp = shieldHit || isShieldsUp(ship);
        tryApply(ship, shieldsUp);
    }

    // The actual OnHitEffectPlugin method signature in current API.
    @Override
    public void onHit(DamagingProjectileAPI projectile,
                      CombatEntityAPI target,
                      Vector2f point,
                      boolean shieldHit,
                      ApplyDamageResultAPI result,
                      CombatEngineAPI engine) {
        onHit(projectile, target, point, shieldHit, engine);
    }

    // ==========================================================
    // 3) PUBLIC STATIC HELPERS (hullmods/listeners/etc.)
    // ==========================================================

    /** External call: attempt application using the same rules (5% or 1% if shields up). */
    public static void tryApplyFromWeaponHit(ShipAPI target) {
        if (target == null || !target.isAlive() || target.isHulk()) return;
        tryApply(target, isShieldsUp(target));
    }

    /** External call: force apply (no RNG). */
    public static void forceApply(ShipAPI target) {
        if (target == null || !target.isAlive() || target.isHulk()) return;
        applyEffect(target);
    }

    /** External call: check active. */
    public static boolean isActive(ShipAPI ship) {
        if (ship == null) return false;
        Object o = ship.getCustomData().get(PLASMA_BURN_ACTIVE_KEY);
        return o instanceof Float;
    }

    // ==========================================================
    // INTERNAL LOGIC
    // ==========================================================
    private static boolean isShieldsUp(ShipAPI ship) {
        return ship != null && ship.getShield() != null && ship.getShield().isOn();
    }

    private static void tryApply(ShipAPI target, boolean shieldsUp) {
        if (isActive(target)) return;
        float chance = shieldsUp ? CHANCE_IF_SHIELDS_UP : CHANCE_ON_HIT;
        if (RNG.nextFloat() <= chance) {
            applyEffect(target);
        }
    }

    private static void applyEffect(ShipAPI target) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        PlasmaBurnManager.showAppliedText(target);
        ensureManagerRegistered(engine);

        float expiresAt = engine.getTotalElapsedTime(false) + DURATION;
        target.setCustomData(PLASMA_BURN_ACTIVE_KEY, expiresAt);

        // Debuff: takes more damage
        target.getMutableStats().getArmorDamageTakenMult().modifyMult(
                PLASMA_ARMOR_TAKEN_ID, ARMOR_DAMAGE_TAKEN_MULT);
        target.getMutableStats().getHullDamageTakenMult().modifyMult(
                PLASMA_HULL_TAKEN_ID, HULL_DAMAGE_TAKEN_MULT);
    }

    private static void removeEffect(ShipAPI ship) {
        if (ship == null) return;

        ship.getCustomData().remove(PLASMA_BURN_ACTIVE_KEY);
        ship.getMutableStats().getArmorDamageTakenMult().unmodify(PLASMA_ARMOR_TAKEN_ID);
        ship.getMutableStats().getHullDamageTakenMult().unmodify(PLASMA_HULL_TAKEN_ID);
    }

    private static void ensureManagerRegistered(CombatEngineAPI engine) {
        if (engine == null) return;

        Map<String, Object> data = engine.getCustomData();
        if (Boolean.TRUE.equals(data.get(PLASMA_MANAGER_KEY))) return;

        engine.addPlugin(new PlasmaBurnManager());
        data.put(PLASMA_MANAGER_KEY, true);
    }

    // ==========================================================
    // INTERNAL MANAGER (handles duration + visuals)
    // ==========================================================
    private static final class PlasmaBurnManager extends BaseEveryFrameCombatPlugin {

        // Smoke now slightly more frequent for visible "billow"
        private final IntervalUtil smokeInterval   = new IntervalUtil(0.10f, 0.18f);
        // More arcs overall (also multiplied by multiple arcs per tick)
        private final IntervalUtil arcInterval     = new IntervalUtil(0.30f, 0.70f);
        private final IntervalUtil overlayInterval = new IntervalUtil(0.12f, 0.20f);

        @Override
        public void advance(float amount, List<com.fs.starfarer.api.input.InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            overlayInterval.advance(amount);
            smokeInterval.advance(amount);
            arcInterval.advance(amount);

            float now = engine.getTotalElapsedTime(false);

            for (ShipAPI ship : engine.getShips()) {
                if (ship == null || !ship.isAlive() || ship.isHulk()) continue;

                Object o = ship.getCustomData().get(PLASMA_BURN_ACTIVE_KEY);
                if (!(o instanceof Float expiresAt)) continue;

                if (now >= expiresAt) {
                    removeEffect(ship);
                    continue;
                }

                // Faint overlay (intervalled to prevent "massive glow")
                if (overlayInterval.intervalElapsed()) {
                    engine.addHitParticle(
                            ship.getLocation(),
                            new Vector2f(),
                            ship.getCollisionRadius() * 0.65f,
                            0.08f,
                            0.15f,
                            OVERLAY
                    );
                }

                // Engine smoke (greenish) - FIXED to spawn at correct engine positions
                if (smokeInterval.intervalElapsed()) {
                    spawnEngineSmoke(engine, ship);
                }

                // EMP arc flicker across hull - now spawns a few arcs per tick
                if (arcInterval.intervalElapsed()) {
                    spawnArcFlicker(engine, ship);
                }
            }
        }

        private static void spawnArcFlicker(CombatEngineAPI engine, ShipAPI ship) {
            if (engine == null || ship == null) return;

            int arcs = ARC_MIN_PER_TICK + RNG.nextInt((ARC_MAX_PER_TICK - ARC_MIN_PER_TICK) + 1);
            float r = ship.getCollisionRadius();

            for (int i = 0; i < arcs; i++) {
                Vector2f from = ship.getLocation();

                Vector2f to = Vector2f.add(from,
                        new Vector2f(
                                (RNG.nextFloat() - 0.5f) * r,
                                (RNG.nextFloat() - 0.5f) * r
                        ),
                        null);

                engine.spawnEmpArcVisual(
                        from,
                        ship,
                        to,
                        ship,
                        ARC_THICKNESS,
                        ARC_CORE,
                        ARC_FRINGE
                );
            }
        }

        /**
         * Spawns smoke at each active engine's WORLD position.
         * IMPORTANT: ShipEngineAPI.getLocation() is already in absolute coordinates,
         * so we do NOT rotate/add ship location again.
         */
        private static void spawnEngineSmoke(CombatEngineAPI engine, ShipAPI ship) {
            if (engine == null || ship == null) return;

            ShipEngineControllerAPI ec = ship.getEngineController();
            if (ec == null) return;

            for (ShipEngineControllerAPI.ShipEngineAPI e : ec.getShipEngines()) {
                if (e == null) continue;
                if (e.isDisabled()) continue;
                if (!e.isActive()) continue; // smoke from engaged engines only

                Vector2f world = new Vector2f(e.getLocation());

                // Base smoke velocity = ship velocity + turbulence
                Vector2f vel = new Vector2f(ship.getVelocity());
                vel.x += (RNG.nextFloat() - 0.5f) * 20f;
                vel.y += (RNG.nextFloat() - 0.5f) * 20f;

                // Slight trailing offset opposite ship movement for a "billow" look
                Vector2f trail = new Vector2f(ship.getVelocity());
                if (trail.lengthSquared() > 1f) {
                    trail.normalise();
                    trail.scale(-8f - RNG.nextFloat() * 10f);
                    Vector2f.add(world, trail, world);
                }

                engine.addNebulaSmokeParticle(
                        world,
                        vel,
                        SMOKE_SIZE_MIN + RNG.nextFloat() * SMOKE_SIZE_ADD,
                        SMOKE_END_MULT,
                        SMOKE_RAMP_UP,
                        SMOKE_FULL_BRT,
                        SMOKE_DUR_MIN + RNG.nextFloat() * SMOKE_DUR_ADD,
                        SMOKE
                );
            }
        }

        private static void showAppliedText(ShipAPI ship) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || ship == null) return;

            engine.addFloatingText(
                    ship.getLocation(),
                    "PLASMA BURN",
                    36f,
                    new Color(140, 255, 140),
                    ship,
                    0.8f,
                    1.6f
            );
        }

        public static ShipSystemStatsScript.StatusData getStatusData(ShipAPI ship, int index) {
            if (index != 0) return null;
            if (!isActive(ship)) return null;
            return new ShipSystemStatsScript.StatusData("PLASMA BURN", true);
        }

        public boolean runWhilePaused() {
            return false;
        }
    }
}