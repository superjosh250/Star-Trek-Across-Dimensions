package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

// NEW: import the shield HP manager
import UFP.data.util.ShieldHitpointManager;

public class ReactorBreach extends BaseEveryFrameCombatPlugin {

    // --- Configuration ---
    public static final float CORE_RADIUS   = 1000f;
    public static final float CORE_DAMAGE   = 21000f;
    public static final float MAX_RADIUS    = 9000f;
    public static final float COUNTDOWN_SEC = 5f;

    private static final Color EXPLOSION_COLOR      = new Color(126, 161, 161, 255);
    private static final float EXPLOSION_VISUAL_R   = 700f;
    private static final float EXPLOSION_VISUAL_DUR = 1.75f;
    // private static final String EXPLOSION_SFX_ID  = "explosion_large"; // currently disabled to avoid NPE if missing

    // --- UFP Shield HP integration ---
    private static final String UFP_SHIELD_HP_PRESENCE_KEY = "UFP_SH_HP";
    private static final float REACTOR_SHIELD_PERCENT_LOSS = 0.80f;
    private static final Color SHIELD_SHOCK_TEXT_COLOR = new Color(140, 230, 255, 255);

    private final ShipAPI parent;       // the ejecting ship
    private final ShipAPI core;         // the spawned warp core (ShipAPI)
    private final float triggerDistance;

    private enum State { WAIT_FOR_CLEARANCE, COUNTDOWN, DETONATED, DONE }
    private State state = State.WAIT_FOR_CLEARANCE;
    private float countdownRemaining = COUNTDOWN_SEC;
    private Vector2f detonationAt = null; // final detonation point (captured on detonation)

    public ReactorBreach(ShipAPI parent, ShipAPI core, float triggerDistance) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.core = Objects.requireNonNull(core, "core");
        this.triggerDistance = Math.max(0f, triggerDistance);
    }

    public static ReactorBreach withDefaultTrigger(ShipAPI parent, ShipAPI core) {
        float trigger = (parent != null ? parent.getCollisionRadius() : 0f) + CORE_RADIUS;
        return new ReactorBreach(parent, core, trigger);
    }

    // ---------------------------
    // Damage source marker object
    // ---------------------------
    private static final class RBDamageSource {
        public final ShipAPI parent;   // ejecting ship
        public final ShipAPI core;     // core at detonation time
        public final float time;       // engine time at creation

        public RBDamageSource(ShipAPI parent, ShipAPI core, float time) {
            this.parent = parent;
            this.core = core;
            this.time = time;
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void advance(float amount, List events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // If combat ends or either entity disappears unexpectedly, clean up.
        if (engine.isCombatOver() || parent == null || core == null) {
            cleanup(engine);
            return;
        }

        switch (state) {
            case WAIT_FOR_CLEARANCE: {
                if (!isAlive(parent) || !isAlive(core)) {
                    cleanup(engine);
                    return;
                }
                float dist = Misc.getDistance(parent.getLocation(), core.getLocation());
                if (dist >= triggerDistance) {
                    state = State.COUNTDOWN;
                    countdownRemaining = COUNTDOWN_SEC;
                }
                break;
            }
            case COUNTDOWN: {
                if (!isAlive(core)) { cleanup(engine); return; }
                countdownRemaining -= Math.max(0f, amount);
                if (countdownRemaining <= 0f) detonate(engine);
                break;
            }
            case DETONATED: {
                state = State.DONE;
                cleanup(engine);
                break;
            }
            case DONE:
            default:
                break;
        }
    }

    // --- Core detonation ---
    private void detonate(CombatEngineAPI engine) {
        if (state == State.DETONATED || state == State.DONE) return;

        detonationAt = new Vector2f(core.getLocation());
        engine.spawnExplosion(detonationAt, new Vector2f(), EXPLOSION_COLOR, EXPLOSION_VISUAL_R, EXPLOSION_VISUAL_DUR);

        // Use a dedicated reactor-breach damage source (NOT the parent ship)
        final Object damageSource = new RBDamageSource(parent, core, engine.getTotalElapsedTime(false));

        // Apply AoE first (so the source exists), but DON'T damage the core itself (no death ⇒ no hulk)
        for (ShipAPI target : engine.getShips()) {
            if (target == null || !target.isAlive()) continue;
            if (target == core) continue;

            float d = Misc.getDistance(detonationAt, target.getLocation());
            if (d > MAX_RADIUS) continue;

            float dmg = damageAtDistance(d);
            if (dmg <= 0f) continue;

            boolean managedShield =
                    Boolean.TRUE.equals(target.getCustomData().get(UFP_SHIELD_HP_PRESENCE_KEY));
            boolean shieldOn = target.getShield() != null && target.getShield().isOn();

            if (managedShield && shieldOn) {
                applyUFPShieldLoss(target, engine);
                continue;
            }


            Vector2f impactPoint = computeShieldImpactPoint(target, detonationAt);
            engine.applyDamage(
                    target,
                    impactPoint,
                    dmg,
                    DamageType.HIGH_EXPLOSIVE,
                    0f,
                    false,                     // do NOT bypass shields
                    false,
                    damageSource
            );
        }

        // Remove the core WITHOUT death pipeline (no hulk)
        try {
            core.setCollisionClass(com.fs.starfarer.api.combat.CollisionClass.NONE);
            core.setAlphaMult(0f);
            core.setPhased(true);
        } catch (Throwable ignored) {}
        engine.removeEntity(core);
        state = State.DETONATED;
    }

    private void applyUFPShieldLoss(ShipAPI target, CombatEngineAPI engine) {
        try {
            float maxHp = ShieldHitpointManager.getMaxShieldHP(target);
            if (maxHp <= 0f) return;

            float current = Math.max(0f, ShieldHitpointManager.getShieldHP(target));
            float removal = maxHp * REACTOR_SHIELD_PERCENT_LOSS;
            float newHp = Math.max(0f, current - removal);

            ShieldHitpointManager.setShieldHP(target, newHp);
            engine.addFloatingText(
                    target.getLocation(),
                    "-" + Math.round(removal) + " Shield HP (Reactor shock)",
                    16f,
                    SHIELD_SHOCK_TEXT_COLOR,
                    target,
                    0.6f,
                    0.6f
            );

            if (newHp <= 0f) {
                ShieldHitpointManager.update(target, 0f);
            }
        } catch (Throwable t) {
            Global.getLogger(ReactorBreach.class).error("Failed to apply UFP shield loss", t);
        }
    }

    // --- Falloff: linear 100% @ 1000f to 25% @ 9000f ---
    public static float damageAtDistance(float distance) {
        if (distance <= CORE_RADIUS) return CORE_DAMAGE;
        if (distance >= MAX_RADIUS) return CORE_DAMAGE * 0.25f;
        float t = (distance - CORE_RADIUS) / (MAX_RADIUS - CORE_RADIUS); // 0..1
        float mult = 1f - (0.75f * t); // 1.00 -> 0.25
        return CORE_DAMAGE * mult;
    }

    private static boolean isAlive(ShipAPI s) {
        return s != null && s.isAlive();
    }

    private void cleanup(CombatEngineAPI engine) {
        try { engine.removePlugin(this); } catch (Throwable ignored) {}
    }


    private static Vector2f computeShieldImpactPoint(ShipAPI target, Vector2f explosionAt) {
        if (target == null) return explosionAt;
        if (target.getShield() == null || !target.getShield().isOn()) {
            return target.getLocation();
        }
        Vector2f toExplosion = Vector2f.sub(explosionAt, target.getLocation(), null);
        if (toExplosion.lengthSquared() <= 0f) {
            return target.getLocation();
        }
        toExplosion.normalise();
        float r = target.getShield().getRadius();
        return new Vector2f(
                target.getLocation().x + toExplosion.x * r,
                target.getLocation().y + toExplosion.y * r
        );
    }
}