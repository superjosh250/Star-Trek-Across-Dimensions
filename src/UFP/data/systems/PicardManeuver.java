package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.WaveDistortion;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class PicardManeuver extends BaseShipSystemScript {

    private static final float ARMOR_DAMAGE_MULT = 0.5f;
    private static final float HULL_DAMAGE_MULT = 0.75f;
    private static final float ENERGY_DAMAGE_MULT = 1.5f;
    private static final float MISSILE_DAMAGE_MULT = 1.75f;
    private static final float BALLISTIC_DAMAGE_MULT = 1.25f;
    private static final float ENGINE_DISABLE_MULT = 999f;

    // EMP immunity multiplier (0 = immune)
    private static final float EMP_IMMUNITY_MULT = 0f;

    private static final float OFFSET_DISTANCE = 300f;
    private static final Color WARP_COLOR = Color.CYAN;
    private static final String WARP_SOUND = "system_phase_cloak_activate";

    private boolean hasJumped = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // Reset jump flag at cooldown start
        if (state == State.COOLDOWN && effectLevel == 0f) {
            hasJumped = false;
        }

        // Core buffs during ACTIVE
        if (state == State.ACTIVE) {
            stats.getArmorDamageTakenMult().modifyMult(id, ARMOR_DAMAGE_MULT);
            stats.getHullDamageTakenMult().modifyMult(id, HULL_DAMAGE_MULT);
            stats.getEnergyWeaponDamageMult().modifyMult(id, ENERGY_DAMAGE_MULT);
            stats.getMissileWeaponDamageMult().modifyMult(id, MISSILE_DAMAGE_MULT);
            stats.getBallisticWeaponDamageMult().modifyMult(id, BALLISTIC_DAMAGE_MULT);
            stats.getEngineDamageTakenMult().modifyMult(id, ENGINE_DISABLE_MULT);

            // Trigger the teleport once effect fully active
            if (!hasJumped && effectLevel == 1f) {
                hasJumped = true;
                doTeleport(ship);
            }
        }

        // EMP immunity ONLY during ACTIVE and OUT; otherwise remove it.
        if (state == State.ACTIVE || state == State.OUT) {
            stats.getEmpDamageTakenMult().modifyMult(id, EMP_IMMUNITY_MULT);
        } else {
            stats.getEmpDamageTakenMult().unmodify(id);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Remove all stat modifications
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getEnergyWeaponDamageMult().unmodify(id);
        stats.getMissileWeaponDamageMult().unmodify(id);
        stats.getBallisticWeaponDamageMult().unmodify(id);
        stats.getEngineDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id); // ensure EMP immunity is cleared
    }

    public void advance(float amount, ShipAPI ship, String id) {
        // No timer-based EMP; state-based only
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.ACTIVE) {
            switch (index) {
                case 0: return new StatusData("Picard Maneuver engaged", false);
                case 1: return new StatusData("+50% Energy, +75% Missiles", false);
                case 2: return new StatusData("-50% Armor, -25% Hull dmg taken", false);
                case 3: return new StatusData("EMP immunity active", false);
            }
        }
        // Show EMP immunity during OUT as well
        if ((state == State.OUT) && index == 0) {
            return new StatusData("EMP immunity (disengaging)", false);
        }
        return null;
    }

    // ==============================================================
    // TELEPORT + VFX
    // ==============================================================

    private void doTeleport(ShipAPI ship) {
        Vector2f originalPos = new Vector2f(ship.getLocation());
        Vector2f targetLocation = computeTargetLocation(ship);

        // VFX at origin
        spawnEntryWarpRing(originalPos);
        spawnDistortionWave(originalPos);
        spawnChromaticFlash(originalPos);

        Global.getSoundPlayer().playSound(WARP_SOUND, 1f, 1f, ship.getLocation(), ship.getVelocity());

        // Teleport — mutate the internal vector, do NOT replace it
        ship.getLocation().x = targetLocation.x;
        ship.getLocation().y = targetLocation.y;
        ship.getVelocity().set(0, 0);

        if (ship.getShield() != null && ship.getShield().isOn()) {
            ship.getShield().toggleOff();
        }

        // VFX at destination
        spawnExitWarpRing(targetLocation);
        spawnDistortionWave(targetLocation);
        spawnChromaticFlash(targetLocation);

        // AFTERIMAGE TRAIL MUST BE ADDED AFTER TELEPORT
        // Offsets are now relative to the ship's new location, so the trail is behind.
        addAfterimageTrail(ship, originalPos, targetLocation);
    }

    // ==============================================================
    // TARGET LOCATION LOGIC
    // ==============================================================

    private Vector2f computeTargetLocation(ShipAPI ship) {
        if (ship.getShipTarget() != null) {
            ShipAPI target = ship.getShipTarget();
            float angle = target.getFacing();
            float dist = target.getCollisionRadius() + OFFSET_DISTANCE;

            float x = (float) Math.cos(Math.toRadians(angle)) * dist;
            float y = (float) Math.sin(Math.toRadians(angle)) * dist;

            return new Vector2f(target.getLocation().x + x, target.getLocation().y + y);
        }

        ViewportAPI v = Global.getCombatEngine().getViewport();
        float wx = v.convertScreenXToWorldX(Global.getSettings().getMouseX());
        float wy = v.convertScreenYToWorldY(Global.getSettings().getMouseY());
        return new Vector2f(wx, wy);
    }

    // ==============================================================
    // AFTERIMAGE TRAIL — BEHIND the ship (destination → origin)
    // ==============================================================

    private void addAfterimageTrail(ShipAPI ship, Vector2f from, Vector2f to) {
        // ship is already at 'to' when this is called
        final int steps = 20;
        final float spacing = 1f / steps;

        for (int i = 1; i <= steps; i++) {
            float f = i * spacing;

            // Interpolate FROM destination (to) BACK to origin (from)
            float x = Misc.interpolate(to.x, from.x, f);
            float y = Misc.interpolate(to.y, from.y, f);

            float alpha = 1f - f;

            // Offsets relative to the ship's *current* location (already at 'to')
            ship.addAfterimage(
                    WARP_COLOR,
                    x - ship.getLocation().x,
                    y - ship.getLocation().y,
                    0f, 0f,
                    15f,
                    alpha,
                    0.5f,
                    0.5f,
                    true,
                    true,
                    true
            );
        }
    }

    // ==============================================================
    // DISTORTION WAVE (circular spacetime ripple)
    // ==============================================================

    private void spawnDistortionWave(Vector2f loc) {
        WaveDistortion wave = new WaveDistortion();

        wave.setLocation(loc);
        wave.setIntensity(30f);
        wave.setSize(40f);
        wave.fadeInSize(0.1f);
        wave.fadeOutSize(1.2f);
        wave.fadeOutIntensity(1.2f);
        wave.setArc(0f, 360f);
        wave.setLifetime(1.2f);

        DistortionShader.addDistortion(wave);
    }

    // ==============================================================
    // CHROMATIC FLASH
    // ==============================================================

    private void spawnChromaticFlash(Vector2f loc) {
        CombatEngineAPI engine = Global.getCombatEngine();

        engine.addHitParticle(
                loc,
                new Vector2f(0, 0),
                200f,
                1.0f,
                0.25f,
                new Color(100, 200, 255, 255)
        );
    }

    // ==============================================================
    // WARP RINGS (entry/exit circles)
    // ==============================================================

    private void spawnEntryWarpRing(Vector2f loc) {
        spawnWarpRing(loc, new Color(0, 255, 255, 150), 250f);
    }

    private void spawnExitWarpRing(Vector2f loc) {
        spawnWarpRing(loc, new Color(255, 255, 255, 200), 300f);
    }

    private void spawnWarpRing(Vector2f loc, Color color, float radius) {
        CombatEngineAPI engine = Global.getCombatEngine();

        engine.addSmoothParticle(
                loc,
                new Vector2f(),
                radius,
                1f,
                0.8f,
                color
        );
    }
}