package UFP.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import UFP.data.util.UFPSettings_Manager;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class HolographicFleetEmitters extends BaseHullMod {

    private static final int NUM_SPAWNS = 4;

    // Flux Costs & Timing Configuration
    private static final float BURST_FLUX_COST = 4000f;
    private static final float FLUX_PER_SECOND = 25f;
    private static final float MIN_DURATION = 75f;
    private static final float MAX_DURATION = 120f;
    private static final float COOLDOWN_DURATION = 60f;

    // Zero-allocation static timers
    private static final IntervalUtil LOCAL_CHECK_INTERVAL = new IntervalUtil(0.3f, 0.4f);
    private static final IntervalUtil GRID_SCAN_INTERVAL = new IntervalUtil(2.0f, 3.0f);

    // Visuals
    private static final Color HOLO_TINT   = new Color(120, 200, 255, 120);
    private static final Color HOLO_JITTER = new Color(140, 220, 255, 160);

    // Spawn geometry (fallback if launch bays aren't present)
    private static final float SPAWN_MIN_DIST = 120f;
    private static final float SPAWN_MAX_DIST = 250f;

    // CustomData keys (on SOURCE)
    private static final String KEY_SPAWNED_LIST      = "ufp_holofleet_spawnedShips";
    private static final String KEY_ACTIVE_LATCH     = "ufp_holofleet_activeLatch";
    private static final String KEY_ACTIVE_TIMER      = "ufp_holofleet_activeTimer";
    private static final String KEY_ACTIVE_MAX_TIME  = "ufp_holofleet_activeMaxTime";
    private static final String KEY_COOLDOWN_TIMER    = "ufp_holofleet_cooldownTimer";
    private static final String KEY_GRID_TRIGGER_MET = "ufp_holofleet_gridTriggerMet";

    // CustomData keys (on EACH HOLO SHIP)
    private static final String KEY_HOLO_ORIG_COLLISION = "ufp_holofleet_origCollision";

    private static final Random RNG = new Random();

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // Process active cooldown window
        Object oCooldown = ship.getCustomData().get(KEY_COOLDOWN_TIMER);
        if (oCooldown instanceof Float) {
            float cd = (Float) oCooldown - amount;
            if (cd > 0f) {
                ship.setCustomData(KEY_COOLDOWN_TIMER, cd);
                if (isEmitting(ship)) {
                    deactivateEmitters(engine, ship);
                }
                return;
            } else {
                ship.getCustomData().remove(KEY_COOLDOWN_TIMER);
            }
        }

        // Flux Check: Forced shutoff on vent or overload
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            if (isEmitting(ship)) {
                startCooldownAndDeactivate(engine, ship);
            }
            return;
        }

        // Advance active hologram timer and drain flux per second
        if (isEmitting(ship)) {
            float activeTimer = (Float) ship.getCustomData().get(KEY_ACTIVE_TIMER) + amount;
            float maxDuration = (Float) ship.getCustomData().get(KEY_ACTIVE_MAX_TIME);

            ship.setCustomData(KEY_ACTIVE_TIMER, activeTimer);

            // Apply flux upkeep per frame
            ship.getFluxTracker().increaseFlux(FLUX_PER_SECOND * amount, true);

            // Check if active duration window reached max lifespan
            if (activeTimer >= maxDuration) {
                startCooldownAndDeactivate(engine, ship);
                return;
            }

            maintainHologramVisuals(engine, ship);
            maintainTemporaryNoCollision(engine, ship, amount);
        }

        // Fast-check AI evaluation on interval tick
        LOCAL_CHECK_INTERVAL.advance(amount);
        if (LOCAL_CHECK_INTERVAL.intervalElapsed()) {
            boolean conditionsMet = evaluateAIActivation(engine, ship, LOCAL_CHECK_INTERVAL.getElapsed());

            if (!isEmitting(ship) && conditionsMet) {
                float currFlux = ship.getFluxTracker().getCurrFlux();
                float maxFlux = ship.getFluxTracker().getMaxFlux();
                if ((maxFlux - currFlux) >= BURST_FLUX_COST) {
                    ship.getFluxTracker().increaseFlux(BURST_FLUX_COST, true);
                    activateEmitters(engine, ship);
                }
            } else if (isEmitting(ship) && !conditionsMet) {
                startCooldownAndDeactivate(engine, ship);
            }
        }
    }

    private boolean isEmitting(ShipAPI ship) {
        return Boolean.TRUE.equals(ship.getCustomData().get(KEY_ACTIVE_LATCH));
    }

    private void activateEmitters(CombatEngineAPI engine, ShipAPI ship) {
        float maxDuration = MIN_DURATION + RNG.nextFloat() * (MAX_DURATION - MIN_DURATION);
        ship.setCustomData(KEY_ACTIVE_LATCH, Boolean.TRUE);
        ship.setCustomData(KEY_ACTIVE_TIMER, 0f);
        ship.setCustomData(KEY_ACTIVE_MAX_TIME, maxDuration);

        despawnAll(engine, ship);
        spawnHolograms(engine, ship);
    }

    private void deactivateEmitters(CombatEngineAPI engine, ShipAPI ship) {
        ship.getCustomData().remove(KEY_ACTIVE_LATCH);
        ship.getCustomData().remove(KEY_ACTIVE_TIMER);
        ship.getCustomData().remove(KEY_ACTIVE_MAX_TIME);
        despawnAll(engine, ship);
    }

    private void startCooldownAndDeactivate(CombatEngineAPI engine, ShipAPI ship) {
        ship.setCustomData(KEY_COOLDOWN_TIMER, COOLDOWN_DURATION);
        deactivateEmitters(engine, ship);
    }

    private boolean evaluateAIActivation(CombatEngineAPI engine, ShipAPI ship, float elapsedSeconds) {
        boolean isStation = isStationLike(ship);
        float fluxLevel = ship.getFluxLevel();

        if (!isStation && fluxLevel > 0.60f) return false;

        GRID_SCAN_INTERVAL.advance(elapsedSeconds);
        if (GRID_SCAN_INTERVAL.intervalElapsed()) {
            boolean triggerMet = checkGridDensity(engine, ship);
            ship.setCustomData(KEY_GRID_TRIGGER_MET, triggerMet);
        }

        if (Boolean.TRUE.equals(ship.getCustomData().get(KEY_GRID_TRIGGER_MET))) {
            return true;
        }

        if (ship.getHullLevel() < 0.75f) {
            return true;
        }

        ShipAPI target = ship.getShipTarget();
        if (target != null && target.isAlive() && target.getOwner() != ship.getOwner()) {
            float distSq = Misc.getDistanceSq(ship.getLocation(), target.getLocation());
            if (distSq < 4000000f) {
                if (target.getFluxLevel() > 0.40f) {
                    return true;
                }
            }
        }

        return fluxLevel > 0.30f;
    }

    private boolean checkGridDensity(CombatEngineAPI engine, ShipAPI ship) {
        int capitalCount = 0;
        int enemyCount = 0;
        int owner = ship.getOwner();

        List<ShipAPI> shipsOnMap = engine.getShips();
        for (int i = 0; i < shipsOnMap.size(); i++) {
            ShipAPI s = shipsOnMap.get(i);
            if (s == null || !s.isAlive() || s.isFighter() || s.isHulk()) continue;

            if (s.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
                capitalCount++;
            }
            if (s.getOwner() != owner) {
                enemyCount++;
            }
            if (capitalCount >= 4 || enemyCount >= 10) {
                return true;
            }
        }
        return false;
    }

    private boolean isStationLike(ShipAPI ship) {
        if (ship == null) return false;
        if (ship.isStation() || ship.isStationModule()) return true;
        ShipHullSpecAPI spec = ship.getHullSpec();
        if (spec == null) return false;
        if (spec.getHints() != null && spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) {
            return true;
        }
        return spec.getTags() != null && spec.getTags().contains(Tags.STATION);
    }

    private void spawnHolograms(CombatEngineAPI engine, ShipAPI source) {
        CombatFleetManagerAPI fm = engine.getFleetManager(source.getOwner());
        if (fm == null) return;

        List<String> pool = UFPSettings_Manager.getValidHologramVariants();
        List<ShipAPI> spawned = new ArrayList<>();
        List<Vector2f> spawnPoints = getLaunchBaySpawnPoints(source);
        if (!spawnPoints.isEmpty()) Collections.shuffle(spawnPoints, RNG);

        for (int i = 0; i < NUM_SPAWNS; i++) {
            String variantId = pool.get(RNG.nextInt(pool.size()));
            Vector2f spawnLoc;
            if (!spawnPoints.isEmpty()) {
                spawnLoc = new Vector2f(spawnPoints.get(i % spawnPoints.size()));
                nudgeForward(spawnLoc, source.getFacing(), 30f + RNG.nextFloat() * 40f);
            } else {
                spawnLoc = pickSpawnLocation(source);
            }
            ShipAPI holo = fm.spawnShipOrWing(variantId, spawnLoc, source.getFacing());
            if (holo == null) continue;
            holo.setOwner(source.getOwner());
            holo.setOriginalOwner(source.getOwner());
            makeHolographic(holo);

            // Store original collision class and turn off collision initially
            holo.setCustomData(KEY_HOLO_ORIG_COLLISION, holo.getCollisionClass());
            holo.setCollisionClass(CollisionClass.NONE);

            spawned.add(holo);
        }
        source.setCustomData(KEY_SPAWNED_LIST, spawned);
    }

    private List<Vector2f> getLaunchBaySpawnPoints(ShipAPI source) {
        if (source == null || !source.hasLaunchBays()) return Collections.emptyList();
        List<FighterLaunchBayAPI> bays = source.getLaunchBaysCopy();
        if (bays == null || bays.isEmpty()) return Collections.emptyList();

        List<Vector2f> points = new ArrayList<>();
        for (FighterLaunchBayAPI bay : bays) {
            if (bay == null) continue;
            WeaponSlotAPI slot = bay.getWeaponSlot();
            if (slot == null) continue;
            Vector2f base = slot.computePosition(source);
            if (base == null) continue;

            List<Vector2f> offsets = slot.getLaunchPointOffsets();
            if (offsets != null && !offsets.isEmpty()) {
                for (Vector2f off : offsets) {
                    if (off == null) continue;
                    Vector2f rotated = rotate(off, source.getFacing());
                    points.add(Vector2f.add(base, rotated, null));
                }
            } else {
                points.add(base);
            }
        }
        return points;
    }

    private Vector2f pickSpawnLocation(ShipAPI source) {
        float angle = (float) (RNG.nextFloat() * Math.PI * 2f);
        float dist = SPAWN_MIN_DIST + RNG.nextFloat() * (SPAWN_MAX_DIST - SPAWN_MIN_DIST);
        float x = source.getLocation().x + (float) Math.cos(angle) * dist;
        float y = source.getLocation().y + (float) Math.sin(angle) * dist;
        return new Vector2f(x, y);
    }

    private void makeHolographic(ShipAPI holo) {
        holo.setExtraAlphaMult(0.45f);
        holo.setApplyExtraAlphaToEngines(true);
        holo.fadeToColor(this, HOLO_TINT, 0.2f, 0.2f, 0.6f);
    }

    private void maintainHologramVisuals(CombatEngineAPI engine, ShipAPI source) {
        List<ShipAPI> spawned = getSpawned(source);
        if (spawned.isEmpty()) return;
        float t = engine.getTotalElapsedTime(false);
        boolean flickerOn = (((int) t) % 2) == 0;
        for (ShipAPI holo : spawned) {
            if (holo == null || holo.isHulk() || !holo.isAlive()) continue;
            if (flickerOn) {
                holo.setJitterUnder(this, HOLO_JITTER, 1f, 6, 0f, 10f);
            }
            holo.fadeToColor(this, HOLO_TINT, 0.1f, 0.1f, 0.5f);
        }
    }

    private void maintainTemporaryNoCollision(CombatEngineAPI engine, ShipAPI source, float amount) {
        if (engine == null || source == null) return;
        List<ShipAPI> spawned = getSpawned(source);
        if (spawned.isEmpty()) return;

        for (ShipAPI holo : spawned) {
            if (holo == null || holo.isHulk() || !holo.isAlive()) continue;

            Object oOrig = holo.getCustomData().get(KEY_HOLO_ORIG_COLLISION);
            if (oOrig == null) continue; // Collision already fully restored

            // Dynamic safe clearance distance calculation
            float requiredDist = source.getCollisionRadius() + holo.getCollisionRadius() + 25f;
            float currentDistSq = Misc.getDistanceSq(holo.getLocation(), source.getLocation());

            if (!source.isAlive() || currentDistSq > (requiredDist * requiredDist)) {
                // Safely clear of parent bounds: restore original collision class
                if (oOrig instanceof CollisionClass) {
                    holo.setCollisionClass((CollisionClass) oOrig);
                }
                holo.removeCustomData(KEY_HOLO_ORIG_COLLISION);
            } else {
                // Still within parent bounds: hold NONE collision and push outward
                if (holo.getCollisionClass() != CollisionClass.NONE) {
                    holo.setCollisionClass(CollisionClass.NONE);
                }

                Vector2f pushDir = Vector2f.sub(holo.getLocation(), source.getLocation(), new Vector2f());
                if (pushDir.lengthSquared() > 0f) {
                    pushDir.normalise();
                } else {
                    float rad = (float) Math.toRadians(holo.getFacing());
                    pushDir.set((float) Math.cos(rad), (float) Math.sin(rad));
                }

                // Accelerate away from parent center frame-by-frame
                pushDir.scale(75f * amount);
                Vector2f.add(holo.getVelocity(), pushDir, holo.getVelocity());
            }
        }
    }

    private void despawnAll(CombatEngineAPI engine, ShipAPI source) {
        List<ShipAPI> spawned = getSpawned(source);
        if (spawned.isEmpty()) return;
        CombatFleetManagerAPI fm = engine.getFleetManager(source.getOwner());
        for (ShipAPI holo : spawned) {
            if (holo == null) continue;
            if (fm != null) fm.removeDeployed(holo, true);
            if (engine.isEntityInPlay(holo)) engine.removeEntity(holo);
        }
        source.getCustomData().remove(KEY_SPAWNED_LIST);
    }

    @SuppressWarnings("unchecked")
    private List<ShipAPI> getSpawned(ShipAPI source) {
        Object o = source.getCustomData().get(KEY_SPAWNED_LIST);
        if (o instanceof List) return (List<ShipAPI>) o;
        return new ArrayList<>();
    }

    private static void nudgeForward(Vector2f loc, float facingDeg, float dist) {
        float rad = (float) Math.toRadians(facingDeg);
        loc.x += (float) Math.cos(rad) * dist;
        loc.y += (float) Math.sin(rad) * dist;
    }

    private static Vector2f rotate(Vector2f v, float facingDeg) {
        float rad = (float) Math.toRadians(facingDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vector2f(v.x * cos - v.y * sin, v.x * sin + v.y * cos);
    }
}