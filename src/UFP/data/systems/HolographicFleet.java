package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class HolographicFleet extends BaseShipSystemScript {

    private static final int NUM_SPAWNS = 4;

    // Flux penalty multiplier for NON-stations (net becomes base * 15)
    private static final float FLUX_USE_MULT_NON_STATION = 15f;

    // Visuals (temporary/hologram look)
    private static final Color HOLO_TINT   = new Color(120, 200, 255, 120);
    private static final Color HOLO_JITTER = new Color(140, 220, 255, 160);

    // Spawn geometry (fallback)
    private static final float SPAWN_MIN_DIST = 600f;
    private static final float SPAWN_MAX_DIST = 900f;

    // "Fighter collision" window (lets ships behave like fighters and not get stuck inside station shields)
    private static final float NO_COLLISION_SECONDS = 30f;

    // Config
    private static final String SETTINGS_PATH = "data/config/UFPSettings.json";
    private static final String SETTINGS_ROOT = "HolographicFleet_variants";
    private static final String SETTINGS_LIST = "variant_ids";

    // Fallback variant ids (used if settings missing/invalid/empty)
    private static final String[] FALLBACK_VARIANTS = new String[] {
            "astral_Elite",
            "valkyrie_Elite",
            "aurora_Support",
            "hammerhead_Support",
            "invictus_Support",
            "wolf_hegemony_PD"
    };

    // CustomData keys (on SOURCE)
    private static final String KEY_SPAWNED_LIST      = "ufp_holofleet_spawnedShips";
    private static final String KEY_ACTIVATION_LATCH  = "ufp_holofleet_activationLatch";

    // CustomData keys (on EACH HOLO SHIP)
    private static final String KEY_HOLO_ORIG_COLLISION = "ufp_holofleet_origCollision";
    private static final String KEY_HOLO_NO_COLLISION_UNTIL = "ufp_holofleet_noCollisionUntil";

    // Cache the loaded variant pool so we don't parse JSON every frame
    private static boolean VARIANTS_LOADED = false;
    private static List<String> CACHED_VARIANTS = Collections.emptyList();

    private static final Random RNG = new Random();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI source = (ShipAPI) stats.getEntity();
        if (source == null) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        if (state != State.ACTIVE) {
            despawnAll(engine, source);
            source.getCustomData().remove(KEY_ACTIVATION_LATCH);
            return;
        }
        if (!source.getCustomData().containsKey(KEY_ACTIVATION_LATCH)) {
            source.setCustomData(KEY_ACTIVATION_LATCH, Boolean.TRUE);
            despawnAll(engine, source);
            spawnHolograms(engine, source);
        }
        float dt = engine.getElapsedInLastFrame();
        if (dt > 0f) {
            applyFluxPerSecondPenalty(engine, source, dt);
        }
        maintainHologramVisuals(engine, source);
        maintainTemporaryNoCollision(engine, source);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI source = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (source == null || engine == null) return;
        despawnAll(engine, source);
        source.getCustomData().remove(KEY_ACTIVATION_LATCH);
    }

    // Config: load variants
    private static List<String> getVariantPool() {
        if (!VARIANTS_LOADED) {
            VARIANTS_LOADED = true;
            CACHED_VARIANTS = loadVariantsFromSettings();
        }
        if (CACHED_VARIANTS != null && !CACHED_VARIANTS.isEmpty()) {
            return CACHED_VARIANTS;
        }
        List<String> fb = new ArrayList<>();
        Collections.addAll(fb, FALLBACK_VARIANTS);
        return fb;
    }

    private static List<String> loadVariantsFromSettings() {
        try {
            JSONObject root = Global.getSettings().loadJSON(SETTINGS_PATH);
            if (root == null) return Collections.emptyList();
            JSONObject block = root.optJSONObject(SETTINGS_ROOT);
            if (block == null) return Collections.emptyList();
            JSONArray arr = block.optJSONArray(SETTINGS_LIST);
            if (arr == null || arr.length() == 0) return Collections.emptyList();
            List<String> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.optString(i, null);
                if (id != null && !id.trim().isEmpty()) out.add(id.trim());
            }
            return out;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    // Flux/sec penalty (NON-stations only)
    private void applyFluxPerSecondPenalty(CombatEngineAPI engine, ShipAPI source, float dt) {
        if (engine == null || source == null) return;
        if (isStationLike(source)) return;
        ShipSystemAPI sys = source.getSystem();
        if (sys == null) return;
        float baseFluxPerSecond = sys.getFluxPerSecond();
        if (baseFluxPerSecond <= 0f) return;

        // Want net base*15 => add base*(15-1) per second
        float extraPerSecond = baseFluxPerSecond * (FLUX_USE_MULT_NON_STATION - 1f);
        float extraThisFrame = extraPerSecond * dt;

        FluxTrackerAPI flux = source.getFluxTracker();
        if (flux != null && extraThisFrame > 0f) {
            flux.increaseFlux(extraThisFrame, true); // hard flux
        }
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
        List<String> pool = getVariantPool();
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
            applyTemporaryFighterCollision(engine, holo);
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
                    Vector2f port = Vector2f.add(base, rotated, null);
                    points.add(port);
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

    // Visuals
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

    // Temporary fighter-collision for spawned ships
    private void applyTemporaryFighterCollision(CombatEngineAPI engine, ShipAPI holo) {
        if (engine == null || holo == null) return;

        CollisionClass orig = holo.getCollisionClass();
        holo.setCustomData(KEY_HOLO_ORIG_COLLISION, orig);

        float until = engine.getTotalElapsedTime(false) + NO_COLLISION_SECONDS;
        holo.setCustomData(KEY_HOLO_NO_COLLISION_UNTIL, until);

        holo.setCollisionClass(CollisionClass.FIGHTER);
    }

    private void maintainTemporaryNoCollision(CombatEngineAPI engine, ShipAPI source) {
        if (engine == null || source == null) return;

        List<ShipAPI> spawned = getSpawned(source);
        if (spawned.isEmpty()) return;

        float now = engine.getTotalElapsedTime(false);

        for (ShipAPI holo : spawned) {
            if (holo == null || holo.isHulk() || !holo.isAlive()) continue;

            Object oUntil = holo.getCustomData().get(KEY_HOLO_NO_COLLISION_UNTIL);
            if (!(oUntil instanceof Float)) continue;

            float until = (Float) oUntil;
            if (now < until) {
                if (holo.getCollisionClass() != CollisionClass.FIGHTER) {
                    holo.setCollisionClass(CollisionClass.FIGHTER);
                }
            } else {
                Object oOrig = holo.getCustomData().get(KEY_HOLO_ORIG_COLLISION);
                if (oOrig instanceof CollisionClass) {
                    holo.setCollisionClass((CollisionClass) oOrig);
                }
                holo.removeCustomData(KEY_HOLO_NO_COLLISION_UNTIL);
                holo.removeCustomData(KEY_HOLO_ORIG_COLLISION);
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
