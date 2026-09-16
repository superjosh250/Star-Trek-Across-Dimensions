package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BaseShieldHitpointsRecovery (optimized version)
 */
public class BaseShieldHitpointsRecovery implements EveryFrameCombatPlugin {

    public static final String SHIELD_ON_TIMER_KEY  = "UFP_SH_REC_ON_T";
    public static final String SHIELD_OFF_TIMER_KEY = "UFP_SH_REC_OFF_T";
    public static final String UNIVERSAL_SHIELD_RECOVERY_FLAG = "UFP_SH_REC_ENABLED";
    public static final String UNIVERSAL_SHIELD_HP_KEY        = "UFP_SH_HP";
    public static final String CUSTOM_SHIELD_ON_RECOVERY_HANDLER_KEY = "UFP_SH_REC_ON_HANDLER";
    public static final String BURST_COOLDOWN_KEY = "UFP_SH_BURST_CD";
    private static final String WAS_VENTING_KEY = "UFP_SH_REC_WAS_VENT";

    // Keys for new flexible recovery methods
    public static final String CONTINUOUS_ACCUMULATOR_KEY = "UFP_SH_CONT_ACCUM";
    public static final String INTERVAL_TIMER_KEY = "UFP_SH_INT_TIMER";

    private final Map<ShipAPI, Float> lastMetric = new HashMap<>();
    private boolean diag = false;
    private float logAccumulator = 0f;
    private boolean battleStarted = false;
    private boolean battleEnded   = false;

    // --- NEW: FLEXIBLE RECOVERY METHODS ---

    // 1. Continuous Recovery: Per-second shield HP recovery.
    public static void applyContinuousSelfRecovery(ShipAPI ship, float amountPerSecond, float dt) {
        float accum = getFloat(ship, CONTINUOUS_ACCUMULATOR_KEY);
        accum += (amountPerSecond * dt);

        if (accum >= 1f) {
            float restore = (float) Math.floor(accum);
            ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + restore);

            // Re-enable shield visually if it was broken
            if (!ship.getShield().isOn()) ship.getShield().toggleOn();

            accum -= restore;
        }
        setFloat(ship, CONTINUOUS_ACCUMULATOR_KEY, accum);
    }

    // 2. Interval Recovery: Recovers a specific amount every X seconds.
    public static void applyIntervalSelfRecovery(ShipAPI ship, float amount, float interval, float dt) {
        float timer = getFloat(ship, INTERVAL_TIMER_KEY);
        timer += dt;
        if (timer >= interval) {
            ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + amount);

            // Re-enable shield visually if it was broken
            if (!ship.getShield().isOn()) ship.getShield().toggleOn();

            timer = 0f;
        }
        setFloat(ship, INTERVAL_TIMER_KEY, timer);
    }

    // 3. Ally Burst: Instant restoration for all allies in range.
    public static void applyAllyBurstRecovery(ShipAPI source, float range, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        float rangeSq = range * range;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() != source.getOwner() || ship == source || !ship.isAlive()) continue;

            // Corrected: Use getDistanceSq
            if (com.fs.starfarer.api.util.Misc.getDistanceSq(ship.getLocation(), source.getLocation()) <= rangeSq) {

                ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + amount);
                if (!ship.getShield().isOn()) ship.getShield().toggleOn();
            }
        }
    }

    // 4. Ally Continuous: Per-second restoration for allies in range.
    public static void applyAllyContinuousRecovery(ShipAPI source, float range, float amountPerSecond, float dt) {
        CombatEngineAPI engine = Global.getCombatEngine();
        float rangeSq = range * range;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() != source.getOwner() || ship == source || !ship.isAlive()) continue;

            // Corrected: Use getDistanceSq
            if (com.fs.starfarer.api.util.Misc.getDistanceSq(ship.getLocation(), source.getLocation()) <= rangeSq) {
                applyContinuousSelfRecovery(ship, amountPerSecond, dt);
            }
        }
    }

    // 5. Constraint Method
    public static boolean isSystemActiveForRecovery(ShipAPI ship) {
        if (ship == null || ship.getSystem() == null) return false;
        ShipSystemAPI.SystemState state = ship.getSystem().getState();
        // Return true if active, or starting up (IN).
        // This prevents recovery during cooldown (OUT/IDLE).
        return state == ShipSystemAPI.SystemState.IN || state == ShipSystemAPI.SystemState.ACTIVE;
    }

    /**
     * Triggers a shield HP burst. Cooldown-tracked.
     * @param amount    Restore value or percentage (e.g., 0.1 for 10%).
     * @param cooldown  Seconds between bursts.
     * @param dt        Frame delta time.
     * @param isPercent Whether amount is a percentage.
     * @return True if applied, false if on cooldown.
     */
    public static boolean applyBurstRecovery(ShipAPI ship, float amount, float cooldown, float dt, boolean isPercent) {
        float timer = getFloat(ship, BURST_COOLDOWN_KEY);

        // If timer is active, decrease it and return false (cannot trigger yet)
        if (timer > 0f) {
            timer -= dt;
            setFloat(ship, BURST_COOLDOWN_KEY, timer);
            return false;
        }

        // Timer is <= 0, perform the restoration
        float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
        float actualRestore = isPercent ? (maxHP * amount) : amount;

        ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + actualRestore);

        // Re-enable shield visually if it was broken
        if (!ship.getShield().isOn()) {
            ship.getShield().toggleOn();
        }

        // Reset timer to full cooldown
        setFloat(ship, BURST_COOLDOWN_KEY, cooldown);
        return true;
    }

    // --- EXISTING LOGIC ---

    public interface ShieldOnRecoveryHandler {
        boolean handleShieldOnRecovery(ShipAPI ship, CombatEngineAPI engine, float amount, float maxHP);
    }

    @Override
    public void init(CombatEngineAPI engine) { /* no-op */ }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        logAccumulator += amount;

        if (!battleStarted) {
            battleStarted = true;
            this.diag = UFPSettings_Manager.isBaseShieldRecoveryDiagnosticEnabled();
        }

        List<ShipAPI> allShips = engine.getShips();
        for (ShipAPI ship : allShips) {
            if (ship == null || ship.isFighter() || ship.isDrone() || !isTarget(ship)) continue;

            float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);

            if (!isRecoveryAllowed(ship)) {
                setFloat(ship, SHIELD_ON_TIMER_KEY, 0f);
                setFloat(ship, SHIELD_OFF_TIMER_KEY, 0f);
                lastMetric.put(ship, ship.getFluxTracker().getCurrFlux());
                continue;
            }

            boolean venting = ship.getFluxTracker().isVenting();
            boolean wasVenting = Boolean.TRUE.equals(ship.getCustomData().get(WAS_VENTING_KEY));

            BaseShieldHitpointsRecoveryFALLBACK.advanceForShip(ship, engine, amount, venting, wasVenting, lastMetric, diag);
            ship.setCustomData(WAS_VENTING_KEY, venting);

            boolean shieldOn = ship.getShield().isOn();
            if (shieldOn) {
                advanceShieldOnRecovery(ship, engine, amount, maxHP);
            } else {
                float offT = getFloat(ship, SHIELD_OFF_TIMER_KEY);
                setFloat(ship, SHIELD_ON_TIMER_KEY, 0f);
                offT += amount;

                if (offT >= 10f) {
                    applyRestore(engine, ship, maxHP, 0.15f, "+15% Shield HP", Color.CYAN);
                    offT = 0f;
                }
                setFloat(ship, SHIELD_OFF_TIMER_KEY, offT);
            }

            lastMetric.put(ship, ship.getFluxTracker().getCurrFlux());
        }
    }

    public void advanceShieldOnRecovery(ShipAPI ship, CombatEngineAPI engine, float amount, float maxHP) {
        setFloat(ship, SHIELD_OFF_TIMER_KEY, 0f);

        Object handlerObj = ship.getCustomData().get(CUSTOM_SHIELD_ON_RECOVERY_HANDLER_KEY);
        if (handlerObj instanceof ShieldOnRecoveryHandler) {
            if (((ShieldOnRecoveryHandler) handlerObj).handleShieldOnRecovery(ship, engine, amount, maxHP)) {
                return;
            }
        }

        float onT = getFloat(ship, SHIELD_ON_TIMER_KEY);
        onT += amount;

        if (crossed(onT, amount, 5f)) {
            applyRestore(engine, ship, maxHP, 0.01f, "+1% Shield HP", Color.WHITE);
        }
        if (onT >= 15f) {
            applyRestore(engine, ship, maxHP, 0.06f, "+6% Shield HP", Color.WHITE);
            onT = 0f;
        }

        setFloat(ship, SHIELD_ON_TIMER_KEY, onT);
    }

    public static boolean isRecoveryAllowed(ShipAPI ship) {
        return ship != null && ship.getShield() != null && !ShieldHitpointManager.isShieldDisabled(ship);
    }

    private boolean isTarget(ShipAPI ship) {
        if (ship == null || !ship.isAlive() || ship.getShield() == null) return false;
        if (!ship.getCustomData().containsKey(UNIVERSAL_SHIELD_HP_KEY)) return false;
        return Boolean.TRUE.equals(ship.getCustomData().get(UNIVERSAL_SHIELD_RECOVERY_FLAG));
    }

    public static void applyRestore(CombatEngineAPI engine, ShipAPI ship, float maxHP, float pct, String label, Color color) {
        float amt = maxHP * pct;
        ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + amt);
        engine.addFloatingText(ship.getLocation(), label, 12f, color, ship, 0.5f, 0.5f);
    }

    private boolean crossed(float t, float dt, float threshold) {
        return t >= threshold && (t - dt) < threshold;
    }

    public static float getFloat(ShipAPI ship, String key) {
        Object o = ship.getCustomData().get(key);
        return (o instanceof Float) ? (Float) o : 0f;
    }

    public static void setFloat(ShipAPI ship, String key, float val) {
        ship.setCustomData(key, val);
    }

    // --- Boilerplate ---
    private float logInterval(CombatEngineAPI engine) { return (engine.getTotalElapsedTime(false) > 180f) ? 45f : 30f; }
    private boolean isBattleOver(CombatEngineAPI engine) { return engine.isCombatOver() || engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy().isEmpty(); }
    private void log(String msg) { if (diag) Global.getLogger(getClass()).info(msg); }
    private String statusString(ShipAPI ship) { return "ShieldHP=" + ShieldHitpointManager.getShieldHP(ship); }
    public ShipSystemStatsScript.StatusData getStatusData(ShipAPI ship, int index) { return null; }
    @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
    @Override public void renderInWorldCoords(ViewportAPI viewport) {}
    @Override public void renderInUICoords(ViewportAPI viewport) {}

    public static final class BaseShieldHitpointsRecoveryFALLBACK {
        private static final String VENTING_DELTA_ACCUM_KEY = "UFP_SH_REC_VENT_ACCUM";

        public static void advanceForShip(ShipAPI ship, CombatEngineAPI engine, float amount,
                                          boolean venting, boolean wasVenting,
                                          Map<ShipAPI, Float> lastMetric, boolean diag) {
            if (!isRecoveryAllowed(ship)) {
                ship.setCustomData(VENTING_DELTA_ACCUM_KEY, 0f);
                lastMetric.put(ship, ship.getFluxTracker().getCurrFlux());
                return;
            }

            float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
            float currentHP = ShieldHitpointManager.getShieldHP(ship);
            float fluxNow = ship.getFluxTracker().getCurrFlux();
            float fluxPrev = lastMetric.getOrDefault(ship, fluxNow);

            float delta = (venting && wasVenting) ? (fluxPrev - fluxNow) : 0f;
            float accum = getFloat(ship, VENTING_DELTA_ACCUM_KEY);
            float total = accum + Math.max(0f, delta);

            int chunks = (int) (total / 3000f);
            if (chunks > 0) {
                float restorePct = chunks * 0.20f;
                float restoreAmt = maxHP * restorePct;
                ShieldHitpointManager.setShieldHP(ship, currentHP + restoreAmt);
                engine.addFloatingText(ship.getLocation(), "+" + (int)(restorePct * 100) + "% Shield HP (Venting)",
                        12f, new Color(0, 255, 180, 230), ship, 0.5f, 0.5f);
                total = total % 3000f;
            }
            ship.setCustomData(VENTING_DELTA_ACCUM_KEY, total);
            lastMetric.put(ship, fluxNow);
        }
    }
}