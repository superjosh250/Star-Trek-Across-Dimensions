package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReflexShields implements ShipSystemStatsScript {

    private static final Color DEFLECT_COLOR = new Color(219, 234, 234, 200);
    private static final float RANDOM_ANGLE_OFFSET = 15f;
    private static final float DAMAGE_REDUCTION_ON_DEFLECT = 0.8f;
    private static final float MISSILE_EXTRA_LIFE = 5f;

    private static final String DEFLECTED_KEY = "UFP_ReflexShields_deflected";
    private static final String ARC_TIME_KEY = "UFP_ReflexShields_arcTimeSinceLast";

    // Beam Performance Throttling Settings
    private static final float ARC_INTERVAL = 0.10f;
    private static final int MAX_ARCS_PER_FRAME = 6;
    private static final float BEAM_DIST_PADDING = 100f;
    private static final float ARC_DAMAGE_MULT = 0.8f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        ShieldAPI shield = ship.getShield();
        if (shield == null || !shield.isOn()) return;

        if (state == State.ACTIVE && effectLevel > 0f) {
            shield.setInnerColor(DEFLECT_COLOR);
            shield.setRingColor(DEFLECT_COLOR);

            // Reduce beam damage to shield by 50%
            stats.getBeamShieldDamageTakenMult().modifyMult(id, 0.5f);

            reflectProjectilesAndMissiles(ship, shield, engine);
            redirectBeams(ship, shield, engine, engine.getElapsedInLastFrame());
        }
    }

    private void reflectProjectilesAndMissiles(ShipAPI ship, ShieldAPI shield, CombatEngineAPI engine) {
        float shieldRadius = ship.getShieldRadiusEvenIfNoShield();
        float checkRange = shieldRadius + 50f;
        Vector2f shipLoc = ship.getLocation();

        // 1. Process Standard Projectiles (Ballistic/Energy)
        List<DamagingProjectileAPI> projectiles = CombatUtils.getProjectilesWithinRange(shipLoc, checkRange);
        for (int i = 0, size = projectiles.size(); i < size; i++) {
            deflectOne(ship, shield, engine, projectiles.get(i));
        }

        // 2. Process Missiles (Restored separate array tracking loop)
        List<MissileAPI> missiles = CombatUtils.getMissilesWithinRange(shipLoc, checkRange);
        for (int i = 0, size = missiles.size(); i < size; i++) {
            deflectOne(ship, shield, engine, missiles.get(i));
        }
    }

    private void deflectOne(ShipAPI ship, ShieldAPI shield, CombatEngineAPI engine, DamagingProjectileAPI proj) {
        if (proj == null || proj.didDamage() || proj.isExpired()) return;

        // Ignore friendly fire variables
        if (proj.getSource() == ship || proj.getOwner() == ship.getOwner()) return;

        // Prevent repeated engine frame checks
        if (proj.getCustomData() != null && proj.getCustomData().containsKey(DEFLECTED_KEY)) return;

        // Safe vector copy prevents manipulation mutations across downstream rendering frames
        Vector2f impactPoint = new Vector2f(proj.getLocation());
        if (!shield.isWithinArc(impactPoint)) return;

        Vector2f velocity = proj.getVelocity();
        if (velocity == null) return;

        float speed = velocity.length();
        if (speed <= 1f) return;

        proj.setCustomData(DEFLECTED_KEY, true);

        // Reverse handling vectors
        float angle = (float) Math.toDegrees(Math.atan2(velocity.y, velocity.x)) + 180f;
        angle += MathUtils.getRandomNumberInRange(-RANDOM_ANGLE_OFFSET, RANDOM_ANGLE_OFFSET);

        Vector2f newVel = MathUtils.getPoint(new Vector2f(), speed, angle);
        velocity.set(newVel);
        proj.setFacing(angle);

        // Ownership translation protocols
        proj.setOwner(ship.getOwner());
        proj.setSource(ship);
        proj.getDamage().setDamage(proj.getDamage().getDamage() * DAMAGE_REDUCTION_ON_DEFLECT);

        if (proj instanceof MissileAPI) {
            MissileAPI missile = (MissileAPI) proj;
            missile.setNoFlameoutOnFizzling(true);
            missile.setMaxFlightTime(missile.getMaxFlightTime() + MISSILE_EXTRA_LIFE);

            float arming = missile.getArmingTime();
            if (missile.getFlightTime() < arming) {
                missile.setFlightTime(arming);
            }
            missile.setJitter(ship, Color.CYAN, 1f, 3, 10f);
        }

        engine.addHitParticle(impactPoint, newVel, 20f, 1f, 0.5f, Color.CYAN);
        engine.addFloatingText(impactPoint, "Deflected!", 12f, Color.CYAN, ship, 0.5f, 0.5f);
    }

    private void redirectBeams(ShipAPI ship, ShieldAPI shield, CombatEngineAPI engine, float amount) {
        float maxDist = ship.getShieldRadiusEvenIfNoShield() + BEAM_DIST_PADDING;
        float maxDistSq = maxDist * maxDist;
        int arcsSpawnedThisFrame = 0;

        List<BeamAPI> activeBeams = engine.getBeams();
        for (int i = 0, size = activeBeams.size(); i < size; i++) {
            BeamAPI beam = activeBeams.get(i);
            if (beam == null || beam.getDamageTarget() != ship) continue;

            Vector2f impactPoint = beam.getTo();
            if (impactPoint == null) continue;

            float dx = impactPoint.x - ship.getLocation().x;
            float dy = impactPoint.y - ship.getLocation().y;
            if ((dx * dx + dy * dy) > maxDistSq) continue;
            if (!shield.isWithinArc(impactPoint)) continue;

            // RESTORED: Visual coloring executes even during spooling/fading phases (< 1f brightness)
            beam.setCoreColor(Color.CYAN);
            beam.setFringeColor(Color.BLUE);

            if (beam.getBrightness() < 1f) continue;

            ShipAPI attacker = beam.getSource();
            if (attacker == null || !attacker.isAlive()) continue;
            if (arcsSpawnedThisFrame >= MAX_ARCS_PER_FRAME) break;

            WeaponAPI weapon = beam.getWeapon();
            if (weapon == null) continue;

            // RESTORED: Combined String key protects intended weapon group sync throttling
            String key = attacker.getId() + ":" + weapon.getId();
            Map<Object, Float> times = getArcTimes(engine);
            float dt = times.getOrDefault(key, 0f) + amount;

            if (dt < ARC_INTERVAL) {
                times.put(key, dt);
                continue;
            }
            times.put(key, 0f);

            DamageAPI beamDamage = beam.getDamage();
            if (beamDamage == null) continue;

            float energyDamage = beamDamage.computeDamageDealt(dt) * ARC_DAMAGE_MULT;
            float empDamage = beamDamage.computeFluxDealt(dt);
            if (energyDamage < 1f && empDamage < 1f) continue;

            engine.spawnEmpArc(
                    ship, impactPoint, ship, attacker,
                    DamageType.ENERGY, energyDamage, empDamage,
                    weapon.getRange(), "tachyon_lance_emp_impact",
                    12f, Color.CYAN, Color.BLUE
            );

            arcsSpawnedThisFrame++;
            engine.addHitParticle(impactPoint, new Vector2f(), 10f, 1f, 0.2f, Color.CYAN);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Float> getArcTimes(CombatEngineAPI engine) {
        Map<Object, Float> map = (Map<Object, Float>) engine.getCustomData().get(ARC_TIME_KEY);
        if (map == null) {
            map = new HashMap<>();
            engine.getCustomData().put(ARC_TIME_KEY, map);
        }
        return map;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        ShieldAPI shield = ship.getShield();
        if (shield != null) {
            // RESTORED: Hardcoded base faction colors as originally designed
            shield.setInnerColor(new Color(164, 115, 208, 150));
            shield.setRingColor(new Color(68, 106, 168, 200));
        }
        stats.getBeamShieldDamageTakenMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.ACTIVE) {
            if (index == 0) return new StatusData("Deflecting projectiles & missiles", false);
            if (index == 1) return new StatusData("Beam redirect active (EMP arcs)", false);
        }
        return null;
    }

    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) { return true; }
    @Override public float getActiveOverride(ShipAPI ship) { return -1f; }
    @Override public float getInOverride(ShipAPI ship) { return -1f; }
    @Override public float getOutOverride(ShipAPI ship) { return -1f; }
    @Override public int getUsesOverride(ShipAPI ship) { return -1; }
    @Override public float getRegenOverride(ShipAPI ship) { return -1f; }
    @Override public String getDisplayNameOverride(State state, float effectLevel) { return null; }
}