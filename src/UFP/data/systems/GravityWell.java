
package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.FlickerUtilV2;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GravityWell extends BaseShipSystemScript {

    private static final float VORTEX_SIZE = 1500f;
    private static final float VORTEX_OPACITY = 0.7f;
    private static final float POWER_MULTIPLIER = 0.75f;

    public static final float RADIUS_WEAK = 4200f;
    public static final float RADIUS_MEDIUM = 3200f;
    public static final float RADIUS_STRONG = 2200f;
    public static final float RADIUS_MAX = 1200f;
    public static final float MIN_DISTANCE = 125f;
    public static final float BASE_STRENGTH = 1.0f;

    private float lastLogTime = 0f;
    private float empArcTimer = 0f;
    private float activationTimer = 0f;

    private SpriteAPI[] vortexFrames;
    private int frameCount = 73;
    private String prefix = "vortex_";
    private float frameRate = 30f;
    private float animationTimer = 0f;
    private int currentFrame = 0;
    private boolean initialized = false;

    private float rotationAngle = 0f;
    private float pulseTimer = 0f;

    private final Set<Integer> loggedFrames = new HashSet<>();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI emitter = (ShipAPI) stats.getEntity();
        if (emitter == null || effectLevel <= 0f) return;

        float activeTime = emitter.getSystem().getChargeActiveDur();
        activationTimer += Global.getCombatEngine().getElapsedInLastFrame();

        if (!initialized) {
            initAnimation();
            initialized = true;
        }

        // Flash tear shimmer effect when system activates
        if (state == State.IN && activeTime > 10f && activationTimer < 0.1f) {
            spawnFlashTear(emitter, 3f); // lasts 3s, then vortex takes over
        }

        // Engine visuals
        ShipEngineControllerAPI engines = emitter.getEngineController();
        engines.extendFlame(this, 1.2f, 1.2f, 1.5f);
        engines.fadeToOtherColor(this, new Color(100, 0, 200), new Color(50, 0, 100), effectLevel, 0.5f);

        // Animation frame update
        animationTimer += Global.getCombatEngine().getElapsedInLastFrame();
        if (animationTimer >= 1f / frameRate) {
            animationTimer = 0f;
            currentFrame = (currentFrame + 1) % frameCount;
        }

        rotationAngle += Global.getCombatEngine().getElapsedInLastFrame() * 20f;
        pulseTimer += Global.getCombatEngine().getElapsedInLastFrame();
        float pulseScale = 1f + 0.05f * (float) Math.sin(pulseTimer * 2f);

        // Render vortex after tear ends (after 3s)
        if (activationTimer > 3f) {
            renderGravityWell(emitter, rotationAngle, pulseScale, effectLevel);
        }

        // Ripple distortion
        addRippleDistortion(emitter.getLocation(), 300f + (200f * effectLevel), 50f * effectLevel);

        // EMP arcs every 2s
        empArcTimer += Global.getCombatEngine().getElapsedInLastFrame();
        if (empArcTimer >= 2f) {
            empArcTimer = 0f;
            spawnEmpArcs(emitter);
        }

        // Particle flicker
        spawnParticles(emitter);

        float currentTime = Global.getCombatEngine().getTotalElapsedTime(false);
        float safeRadius = emitter.getCollisionRadius() + MIN_DISTANCE;

        for (ShipAPI target : Global.getCombatEngine().getShips()) {
            if (isEligible(emitter, target)) {
                float distance = MathUtils.getDistance(emitter, target);

                if (distance < safeRadius) {
                    applyPush(target, emitter, effectLevel);
                    showIndicator(target, "Push Back!");
                } else if (distance < RADIUS_WEAK) {
                    float strength = calculatePullStrength(distance, emitter, target, effectLevel);
                    applyPull(target, emitter, strength);
                    showIndicator(target, "Gravity Well!");
                }
            }
        }

        if (currentTime - lastLogTime >= 5f) {
            lastLogTime = currentTime;
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        loggedFrames.clear();
        activationTimer = 0f;

        // Apply damping to prevent slingshotting
        for (ShipAPI target : Global.getCombatEngine().getShips()) {
            if (isEligible((ShipAPI) stats.getEntity(), target)) {
                Vector2f vel = target.getVelocity();
                vel.scale(0.5f); // reduce velocity by half
            }
        }
    }

    private boolean isEligible(ShipAPI emitter, ShipAPI target) {
        return target != emitter && target.isAlive() && !target.isHulk();
    }

    private float calculatePullStrength(float distance, ShipAPI emitter, ShipAPI target, float effectLevel) {
        float baseStrength;
        if (distance <= RADIUS_MAX) baseStrength = 0.4f;
        else if (distance <= RADIUS_STRONG) baseStrength = 0.25f;
        else if (distance <= RADIUS_MEDIUM) baseStrength = 0.2f;
        else baseStrength = 0.1f;

        float massFactor = Math.min(emitter.getMass() / target.getMass(), 3f);
        float speedFactor = (target.getMaxSpeed() <= emitter.getMaxSpeed()) ? 1.5f : 1.0f;
        float distanceFactor = distance / RADIUS_WEAK;

        float strength = baseStrength * BASE_STRENGTH * massFactor * speedFactor * effectLevel;
        strength *= distanceFactor;
        strength *= POWER_MULTIPLIER;
        return Math.max(strength, 0.05f);
    }

    private void applyPull(ShipAPI target, ShipAPI emitter, float strength) {
        Vector2f dir = org.lazywizard.lazylib.VectorUtils.getDirectionalVector(target.getLocation(), emitter.getLocation());
        dir.scale(strength);
        target.getVelocity().x += dir.x;
        target.getVelocity().y += dir.y;
    }

    private void applyPush(ShipAPI target, ShipAPI emitter, float effectLevel) {
        Vector2f dir = org.lazywizard.lazylib.VectorUtils.getDirectionalVector(emitter.getLocation(), target.getLocation());
        dir.scale(500f * effectLevel); // scale push by effectLevel
        target.getVelocity().x += dir.x;
        target.getVelocity().y += dir.y;
    }

    private void showIndicator(ShipAPI target, String text) {
        Global.getCombatEngine().addFloatingText(target.getLocation(), text, 20f, Color.WHITE, target, 0.5f, 1.0f);
    }

    private void initAnimation() {
        try {
            JSONObject settings = Global.getSettings().loadJSON("data/config/UFPSettings.json")
                    .getJSONObject("graphics")
                    .getJSONObject("GravityWellGraphics");

            prefix = settings.optString("prefix", prefix);
            frameCount = settings.optInt("frameCount", frameCount);
            frameRate = (float) settings.optDouble("frameRate", frameRate);
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("GravityWell: Failed to load UFPSettings.json, using defaults.", e);
        }

        vortexFrames = new SpriteAPI[frameCount];
        for (int i = 0; i < frameCount; i++) {
            vortexFrames[i] = Global.getSettings().getSprite("gravity_well_fx", prefix + (i + 1));
        }
    }

    private void renderGravityWell(ShipAPI emitter, float rotationAngle, float pulseScale, float effectLevel) {
        SpriteAPI sprite = vortexFrames[currentFrame];

        float minSize = emitter.getCollisionRadius() + 200f;
        float currentSize = minSize + (VORTEX_SIZE - minSize) * effectLevel;
        Color color = new Color(255, 255, 255, (int) (255 * VORTEX_OPACITY * effectLevel));

        MagicRender.battlespace(
                sprite,
                emitter.getLocation(),
                new Vector2f(),
                new Vector2f(currentSize, currentSize),
                new Vector2f(),
                rotationAngle,
                0f,
                color,
                false,
                0f, 0f, 0f, 0f, 0f,
                0.1f,
                1f,
                0.1f,
                CombatEngineLayers.BELOW_SHIPS_LAYER
        );
    }

    private void addRippleDistortion(Vector2f location, float size, float intensity) {
        RippleDistortion ripple = new RippleDistortion(location, new Vector2f());
        ripple.setSize(size);
        ripple.setIntensity(intensity);
        ripple.setLifetime(0.5f);
        ripple.setAutoFadeSizeTime(0.5f);
        ripple.setAutoFadeIntensityTime(0.5f);
        DistortionShader.addDistortion(ripple);
    }

    private void spawnEmpArcs(ShipAPI emitter) {
        int arcCount = (int) (Math.random() * 5) + 1;
        for (int i = 0; i < arcCount; i++) {
            ShipAPI target = findNearestEnemy(emitter.getLocation());
            Vector2f arcTargetLoc;

            if (target != null && MathUtils.getDistance(emitter, target) <= 1200f) {
                arcTargetLoc = target.getLocation();
                Global.getCombatEngine().spawnEmpArc(
                        emitter,
                        emitter.getLocation(),
                        emitter,
                        target,
                        DamageType.ENERGY,
                        250f,
                        1000f,
                        1250f,
                        "tachyon_lance_emp_impact",
                        20f,
                        new Color(209, 181, 228),
                        new Color(175, 188, 253)
                );
            } else {
                arcTargetLoc = MathUtils.getPointOnCircumference(emitter.getLocation(),
                        (float) (Math.random() * 1250f), (float) (Math.random() * 360f));
                Global.getCombatEngine().spawnEmpArcVisual(
                        emitter.getLocation(),
                        emitter,
                        arcTargetLoc,
                        null,
                        20f,
                        new Color(209, 181, 228),
                        new Color(175, 188, 253)
                );
            }
        }
    }

    private ShipAPI findNearestEnemy(Vector2f loc) {
        ShipAPI closest = null;
        float minDist = Float.MAX_VALUE;
        for (ShipAPI ship : Global.getCombatEngine().getShips()) {
            if (ship.isAlive() && ship.getOwner() != Global.getCombatEngine().getPlayerShip().getOwner()) {
                float dist = MathUtils.getDistance(loc, ship.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    closest = ship;
                }
            }
        }
        return closest;
    }

    private void spawnFlashTear(ShipAPI emitter, float duration) {
        SpriteAPI tearSprite = Global.getSettings().getSprite("graphics/fx/tear.png");
        Color[] shimmerColors = {
                new Color(150, 208, 225, 200),
                new Color(202, 182, 213, 200),
                new Color(220, 220, 230, 180)
        };

        Global.getCombatEngine().addPlugin(new BaseEveryFrameCombatPlugin() {
            float elapsed = 0f;
            int colorIndex = 0;
            FlickerUtilV2 flicker = new FlickerUtilV2();

            @Override
            public void advance(float amount, List<com.fs.starfarer.api.input.InputEventAPI> events) {
                if (emitter == null || !emitter.isAlive()) return;

                elapsed += amount;
                flicker.advance(amount);

                if (elapsed % 0.5f < amount) {
                    colorIndex = (colorIndex + 1) % shimmerColors.length;
                }

                Color baseColor = shimmerColors[colorIndex];
                int brightness = (int) (255 * flicker.getBrightness());
                Color flickerColor = new Color(
                        Math.min(baseColor.getRed() + brightness, 255),
                        Math.min(baseColor.getGreen() + brightness, 255),
                        Math.min(baseColor.getBlue() + brightness, 255),
                        baseColor.getAlpha()
                );

                tearSprite.setColor(flickerColor);

                // Render tear following emitter
                MagicRender.battlespace(
                        tearSprite,
                        emitter.getLocation(),
                        new Vector2f(),
                        new Vector2f(emitter.getCollisionRadius() + 225f, emitter.getCollisionRadius() + 225f),
                        new Vector2f(),
                        0f,
                        0f,
                        flickerColor,
                        false,
                        0f, 0f, 0f, 0f, 0f,
                        0.1f,
                        0.1f,
                        0.1f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );

                if (elapsed >= duration) {
                    Global.getCombatEngine().removePlugin(this);
                }
            }

            public boolean runWhilePaused() { return false; }
        });
    }

    private void spawnParticles(ShipAPI emitter) {
        for (int i = 0; i < 5; i++) {
            Vector2f loc = MathUtils.getPointOnCircumference(emitter.getLocation(),
                    (float) (Math.random() * VORTEX_SIZE), (float) (Math.random() * 360f));
            Color color = Math.random() < 0.33 ? Color.WHITE : (Math.random() < 0.66 ? new Color(202, 182, 213) : new Color(170, 194, 204));
            Global.getCombatEngine().addSmoothParticle(loc, new Vector2f(), 10f, 1f, 0.5f, color);
        }
    }
}
