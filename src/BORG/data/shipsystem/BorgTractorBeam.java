package BORG.data.shipsystem;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicAnim;

import java.awt.Color;
import java.util.*;

public class BorgTractorBeam extends BaseShipSystemScript {

    private static final int MAX_AUTO_TARGETS = 5;
    private static final float MAX_TRACTOR_RANGE = 3000f; // game units (SU)
    private static final float VELOCITY_DAMP_FACTOR = 0.05f; // same idea as TractorBeam.java


    private static final Color BORGC_CORE_COLOR   = new Color(120, 255, 120, 255);
    private static final Color BORGC_FRINGE_COLOR = new Color( 60, 200,  60, 200);

    // =========================
    // Runtime state
    // =========================
    private BorgMultiTractorVisual visualPlugin;

    // Tracks current targets -> per-target stat modifier key
    private final Map<ShipAPI, String> activeKeys = new HashMap<>();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI sourceShip = (ShipAPI) stats.getEntity();
        if (sourceShip == null) return;

        // If system isn't really active, cleanup everything.
        if (effectLevel <= 0f || state == State.COOLDOWN) {
            cleanupAll(id);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        // Count emitters (limits beam count)
        List<WeaponSlotAPI> emitters = getTractorEmitters(sourceShip);
        int emitterCount = emitters.size();
        if (emitterCount <= 0) {
            cleanupAll(id);
            return;
        }

        int maxBeams = Math.min(MAX_AUTO_TARGETS, emitterCount);

        // Select targets: always include player target if valid, then fill automatically.
        List<ShipAPI> targets = selectTargets(sourceShip, maxBeams);

        if (targets.isEmpty()) {
            cleanupAll(id);
            return;
        }

        // Remove effects from targets no longer selected
        cleanupDroppedTargets(id, targets);

        // Apply effects to selected targets
        for (ShipAPI targetShip : targets) {
            if (targetShip == null) continue;
            if (!isValidTarget(sourceShip, targetShip)) continue;

            float dist = distance(sourceShip.getLocation(), targetShip.getLocation());
            if (dist > MAX_TRACTOR_RANGE) continue;

            // Unique per-target key so multiple targets don’t collide
            String key = activeKeys.computeIfAbsent(targetShip, t -> id + "_" + System.identityHashCode(t));

            float appliedPower = computeAppliedPower(sourceShip, targetShip, effectLevel);

            // Apply movement penalties (same structure as TractorBeam.java)
            MutableShipStatsAPI targetStats = targetShip.getMutableStats();
            float mult = 1f - appliedPower;

            targetStats.getMaxSpeed().modifyMult(key, mult);
            targetStats.getAcceleration().modifyMult(key, mult);
            targetStats.getDeceleration().modifyMult(key, mult);
            targetStats.getMaxTurnRate().modifyMult(key, mult);
            targetStats.getTurnAcceleration().modifyMult(key, mult);

            // Small continuous damp
            targetShip.getVelocity().scale(1f - appliedPower * VELOCITY_DAMP_FACTOR);
        }

        // Ensure the multi-visual plugin exists while active
        if (visualPlugin == null) {
            visualPlugin = new BorgMultiTractorVisual(
                    sourceShip,
                    this,
                    BORGC_CORE_COLOR,
                    BORGC_FRINGE_COLOR
            );
            engine.addPlugin(visualPlugin);
        } else {
            // update colors dynamically if you ever want to (optional)
            visualPlugin.setColors(BORGC_CORE_COLOR, BORGC_FRINGE_COLOR);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        cleanupAll(id);
    }

    // ======================================
    // Targeting rules
    // ======================================

    // Player can designate at least one target (ship.getShipTarget()).
    // Remaining targets are auto-selected by nearest enemy ships.
    private List<ShipAPI> selectTargets(ShipAPI source, int maxBeams) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return Collections.emptyList();

        List<ShipAPI> out = new ArrayList<>(maxBeams);

        // 1) Player-selected target (if valid)
        CombatEntityAPI playerT = source.getShipTarget();
        ShipAPI playerTarget = (playerT instanceof ShipAPI) ? (ShipAPI) playerT : null;
        if (playerTarget != null && isValidTarget(source, playerTarget)) {
            float d = distance(source.getLocation(), playerTarget.getLocation());
            if (d <= MAX_TRACTOR_RANGE) {
                out.add(playerTarget);
            }
        }

        if (out.size() >= maxBeams) return out;

        // 2) Auto targets: nearest valid enemy ships (exclude playerTarget)
        List<ShipAPI> candidates = new ArrayList<>();
        for (ShipAPI other : engine.getShips()) {
            if (other == null) continue;
            if (other == source) continue;
            if (!isValidTarget(source, other)) continue;

            if (playerTarget != null && other == playerTarget) continue;

            float d = distance(source.getLocation(), other.getLocation());
            if (d > MAX_TRACTOR_RANGE) continue;

            candidates.add(other);
        }

        candidates.sort(Comparator.comparingDouble(s -> distance(source.getLocation(), s.getLocation())));

        for (ShipAPI c : candidates) {
            if (out.size() >= maxBeams) break;
            out.add(c);
        }

        return out;
    }

    private boolean isValidTarget(ShipAPI source, ShipAPI target) {
        if (target == null) return false;
        if (!target.isAlive() || target.isHulk()) return false;

        // Optional exclusions (uncomment if desired)
        // if (target.isFighter() || target.isDrone()) return false;

        // Enemy check
        return target.getOwner() != source.getOwner();
    }

    // ======================================
    // Emitter counting (limits beams)
    // ======================================
    private List<WeaponSlotAPI> getTractorEmitters(ShipAPI source) {
        if (source.getVariant() == null || source.getVariant().getHullSpec() == null) return Collections.emptyList();

        List<WeaponSlotAPI> emitters = new ArrayList<>();
        for (WeaponSlotAPI slot : source.getVariant().getHullSpec().getAllWeaponSlotsCopy()) {
            // Mirrors TractorBeamVisual’s emitter detection rule:
            // system slot id contains "Tractor_Emitter"
            if (slot.isSystemSlot() && slot.getId() != null && slot.getId().contains("Tractor_Emitter")) {
                emitters.add(slot);
            }
        }
        return emitters;
    }

    // ======================================
    // Effect strength logic (from TractorBeam.java)
    // ======================================

    private float computeAppliedPower(ShipAPI sourceShip, ShipAPI targetShip, float effectLevel) {
        float sourceMass = sourceShip.getMass();
        float targetMass = targetShip.getMass();
        if (targetMass <= 0f) targetMass = 1f;

        float ratio = sourceMass / targetMass;

        float stoppingPower;
        if (ratio >= 1f) stoppingPower = 0.95f;
        else if (ratio >= 0.8f) stoppingPower = 0.9f;
        else if (ratio >= 0.5f) stoppingPower = 0.7f;
        else stoppingPower = 0.5f;

        float appliedPower = stoppingPower * effectLevel;
        if (appliedPower < 0f) appliedPower = 0f;
        if (appliedPower > 0.95f) appliedPower = 0.95f;

        return appliedPower;
    }

    // ======================================
    // Cleanup
    // ======================================

    private void cleanupDroppedTargets(String id, List<ShipAPI> stillSelected) {
        Set<ShipAPI> keep = new HashSet<>(stillSelected);

        Iterator<Map.Entry<ShipAPI, String>> it = activeKeys.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ShipAPI, String> e = it.next();
            ShipAPI target = e.getKey();
            String key = e.getValue();

            if (!keep.contains(target) || target == null || !target.isAlive() || target.isHulk()) {
                cleanupTargetByKey(target, key);
                it.remove();
            }
        }
    }

    private void cleanupAll(String id) {
        // Remove all stat mods from all targets
        for (Map.Entry<ShipAPI, String> e : activeKeys.entrySet()) {
            cleanupTargetByKey(e.getKey(), e.getValue());
        }
        activeKeys.clear();

        // Remove visual plugin
        if (visualPlugin != null && Global.getCombatEngine() != null) {
            Global.getCombatEngine().removePlugin(visualPlugin);
            visualPlugin = null;
        }
    }

    private void cleanupTargetByKey(ShipAPI targetShip, String key) {
        if (targetShip == null) return;
        MutableShipStatsAPI ts = targetShip.getMutableStats();
        ts.getMaxSpeed().unmodify(key);
        ts.getAcceleration().unmodify(key);
        ts.getDeceleration().unmodify(key);
        ts.getMaxTurnRate().unmodify(key);
        ts.getTurnAcceleration().unmodify(key);
    }

    // ======================================
    // Usability + info text
    // ======================================

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null) return false;

        // Usable if there is at least one valid target in range OR player has a valid ship target.
        int emitters = getTractorEmitters(ship).size();
        if (emitters <= 0) return false;

        CombatEntityAPI t = ship.getShipTarget();
        if (t instanceof ShipAPI) {
            ShipAPI ts = (ShipAPI) t;
            if (isValidTarget(ship, ts) && distance(ship.getLocation(), ts.getLocation()) <= MAX_TRACTOR_RANGE) return true;
        }

        // Otherwise, see if any enemy ship is in range (so AI can still use it)
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return false;
        for (ShipAPI other : engine.getShips()) {
            if (isValidTarget(ship, other) && distance(ship.getLocation(), other.getLocation()) <= MAX_TRACTOR_RANGE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null) return null;
        if (system != null && system.isActive()) {
            return "TRACTOR NET";
        }
        // if player has no target, show ready anyway (auto-target will still work)
        return "READY";
    }

    @Override
    public StatusData getStatusData(int index, ShipSystemStatsScript.State state, float effectLevel) {
        if (index == 0 && effectLevel > 0f && state != State.COOLDOWN) {
            int count = activeKeys.size();
            return new StatusData("Tractoring " + count + " target(s)", false);
        }
        return null;
    }

    // ======================================
    // Helpers
    // ======================================

    private static float distance(Vector2f a, Vector2f b) {
        if (a == null || b == null) return Float.MAX_VALUE;
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ============================================================
    // Visual plugin: multi-beam, color-configurable
    // (Derived from TractorBeamVisual structure)
    // ============================================================
    private static class BorgMultiTractorVisual implements EveryFrameCombatPlugin {

        private final ShipAPI source;
        private final BorgTractorBeam systemScript;

        // Texture IDs (same as TractorBeamVisual)
        private static final String CORE_TEXTURE_ID = "tractorBeam";
        private static final String FRINGE_TEXTURE_ID = "beamPhaser";

        private static final float PIXELS_PER_TEXEL = 256f;
        private static final float SCROLL_SPEED = 1024f;

        private Color coreColor;
        private Color fringeColor;

        // cached
        private float scrollOffset;

        BorgMultiTractorVisual(ShipAPI source, BorgTractorBeam systemScript, Color core, Color fringe) {
            this.source = source;
            this.systemScript = systemScript;
            this.coreColor = core;
            this.fringeColor = fringe;
        }

        void setColors(Color core, Color fringe) {
            if (core != null) this.coreColor = core;
            if (fringe != null) this.fringeColor = fringe;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (Global.getCombatEngine() == null) return;
            if (Global.getCombatEngine().isPaused()) return;
            if (source == null) return;

            // update scroll
            scrollOffset = MagicAnim.cycle(
                    Global.getCombatEngine().getTotalElapsedTime(false) * SCROLL_SPEED,
                    0f,
                    PIXELS_PER_TEXEL
            );
        }

        @Override
        public void renderInWorldCoords(ViewportAPI viewport) {
            if (Global.getCombatEngine() == null) return;
            if (source == null) return;
            if (source.getSystem() == null || !source.getSystem().isActive()) return;

            // Get current targets from system script
            List<ShipAPI> targets = new ArrayList<>(systemScript.activeKeys.keySet());
            if (targets.isEmpty()) return;

            // Compute emitter positions (up to number of targets)
            List<WeaponSlotAPI> emitters = systemScript.getTractorEmitters(source);
            if (emitters.isEmpty()) return;

            int beams = Math.min(targets.size(), emitters.size());
            if (beams <= 0) return;

            // Pulse beam width similar to TractorBeamVisual
            float pulseFactor = MagicAnim.smoothReturnToRange(
                    Global.getCombatEngine().getTotalElapsedTime(false),
                    0f, 2f, 1f, 1.2f
            );
            float widthIn = 15f * pulseFactor;
            float widthOut = 60f * pulseFactor;

            for (int i = 0; i < beams; i++) {
                ShipAPI target = targets.get(i);
                if (target == null || !target.isAlive() || target.isHulk()) continue;

                Vector2f from = emitters.get(i).computePosition(source);
                if (from == null) continue;

                Vector2f to = target.getLocation();
                if (to == null) continue;

                float angle = VectorUtils.getAngle(from, to);
                float range = Vector2f.sub(to, from, null).length();

                renderScrollingConeBeam(from, to, widthIn, widthOut, coreColor, fringeColor, angle, range, scrollOffset);
            }
        }

        private void renderScrollingConeBeam(Vector2f from, Vector2f to, float widthIn, float widthOut,
                                             Color coreColor, Color fringeColor, float angle, float range, float scrollOffset) {

            float angleRad = (float) Math.toRadians(angle);
            Vector2f dir = new Vector2f((float) Math.cos(angleRad), (float) Math.sin(angleRad));
            Vector2f perp = new Vector2f(-dir.y, dir.x);

            Vector2f srcLeft = new Vector2f(from.x + perp.x * (widthIn / 2), from.y + perp.y * (widthIn / 2));
            Vector2f srcRight = new Vector2f(from.x - perp.x * (widthIn / 2), from.y - perp.y * (widthIn / 2));
            Vector2f tgtLeft = new Vector2f(to.x + perp.x * (widthOut / 2), to.y + perp.y * (widthOut / 2));
            Vector2f tgtRight = new Vector2f(to.x - perp.x * (widthOut / 2), to.y - perp.y * (widthOut / 2));

            // Core
            Global.getSettings().getSprite("fx", CORE_TEXTURE_ID).bindTexture();
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(coreColor.getRed() / 255f, coreColor.getGreen() / 255f, coreColor.getBlue() / 255f, coreColor.getAlpha() / 255f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 0f); GL11.glVertex2f(srcLeft.x, srcLeft.y);
            GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 1f); GL11.glVertex2f(srcRight.x, srcRight.y);
            GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 1f); GL11.glVertex2f(tgtRight.x, tgtRight.y);
            GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 0f); GL11.glVertex2f(tgtLeft.x, tgtLeft.y);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glPopMatrix();

            // Fringe
            Global.getSettings().getSprite("fx", FRINGE_TEXTURE_ID).bindTexture();
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(fringeColor.getRed() / 255f, fringeColor.getGreen() / 255f, fringeColor.getBlue() / 255f, fringeColor.getAlpha() / 255f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 0f); GL11.glVertex2f(srcLeft.x, srcLeft.y);
            GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 1f); GL11.glVertex2f(srcRight.x, srcRight.y);
            GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 1f); GL11.glVertex2f(tgtRight.x, tgtRight.y);
            GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 0f); GL11.glVertex2f(tgtLeft.x, tgtLeft.y);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glPopMatrix();
        }

        @Override public void init(CombatEngineAPI engine) {}
        @Override public void renderInUICoords(ViewportAPI viewport) {}
        @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
    }
}