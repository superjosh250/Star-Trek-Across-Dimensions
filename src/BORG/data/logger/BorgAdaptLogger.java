package BORG.data.logger;

import BORG.data.util.BorgAdaptationMatrix;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

import java.util.HashMap;
import java.util.Map;

/**
 * TEMP DEBUG LOGGER for BorgAdaptationMatrix.
 *
 * What it verifies (without log spam):
 *  1) Logger enabled + class reachable (loaded)
 *  2) Combat detected and script appears to be running
 *  3) Listener presence on ships using AdaptationMatrix
 *  4) Key matrix milestones:
 *      - stage transitions (failureStage increases)
 *      - immunity achieved (isFullyImmune flips true)
 *      - periodic summary (throttled)
 *
 * REMOVE AFTER VALIDATION.
 */
public final class BorgAdaptLogger {

    // =========================
    // MASTER SWITCH
    // =========================
    private static boolean ENABLED = true;

    // =========================
    // THROTTLE (avoid spam)
    // =========================
    public static float SUMMARY_LOG_INTERVAL_SEC = 5f;

    // =========================
    // INTERNAL TRACKING
    // =========================
    private static boolean loggedLoaded = false;
    private static boolean loggedCombatStart = false;

    // Per-ship tracking: shipKey -> last values
    private static final Map<Integer, ShipSnapshot> SNAPSHOTS = new HashMap<>();

    private BorgAdaptLogger() {}

    // =========================================================
    // CONTROL
    // =========================================================
    public static void enable() {
        ENABLED = true;
        log("BorgAdaptLogger ENABLED");
    }

    public static void disable() {
        ENABLED = false;
        log("BorgAdaptLogger DISABLED");
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    // =========================================================
    // LOAD PHASE
    // =========================================================
    public static void logLoaded() {
        if (!ENABLED || loggedLoaded) return;
        loggedLoaded = true;

        // Just confirming the class is reachable (no behavior changes)
        log("BorgAdaptationMatrix reachable. DATA_KEY=" + BorgAdaptationMatrix.DATA_KEY);
    }

    // =========================================================
    // COMBAT PHASE — call this from hullmod advanceInCombat, or a combat plugin.
    // =========================================================
    public static void logCombatState(ShipAPI ship) {
        if (!ENABLED || ship == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        if (!loggedCombatStart) {
            loggedCombatStart = true;
            log("Combat detected — BorgAdaptLogger is monitoring adaptation activity");
        }

        int shipKey = System.identityHashCode(ship);
        ShipSnapshot snap = SNAPSHOTS.computeIfAbsent(shipKey, k -> new ShipSnapshot());

        // 1) Listener presence (log once)
        if (!snap.loggedListener && ship.hasListenerOfClass(BorgAdaptationMatrix.class)) {
            snap.loggedListener = true;
            log("Listener present on ship hullId=" + safeHullId(ship));
        }

        // 2) Read full adaptation map (source of truth in your implementation)
        Map<String, BorgAdaptationMatrix.AdaptationState> map = BorgAdaptationMatrix.getFullAdaptationMap(ship);
        if (map == null || map.isEmpty()) {
            // throttle "no data" logs, otherwise it spams before first hit
            maybeLogThrottled(engine, snap, "No adaptation data yet for hullId=" + safeHullId(ship));
            return;
        }

        // Compute important summary stats (same style as renderHUD)
        int immuneCount = 0;
        float highestResist = 0f;
        String highestWeaponId = null;
        int maxStage = 0;

        for (Map.Entry<String, BorgAdaptationMatrix.AdaptationState> e : map.entrySet()) {
            String weaponId = e.getKey();
            BorgAdaptationMatrix.AdaptationState st = e.getValue();
            if (st == null) continue;

            if (st.isFullyImmune) immuneCount++;
            if (!st.isFullyImmune && st.currentResistance > highestResist) {
                highestResist = st.currentResistance;
                highestWeaponId = weaponId;
            }
            if (st.failureStage > maxStage) maxStage = st.failureStage;
        }

        // 3) Stage transitions (log only when stage increases)
        if (maxStage > snap.lastMaxStage) {
            snap.lastMaxStage = maxStage;
            log("Stage increased on hullId=" + safeHullId(ship)
                    + " -> stage=" + maxStage
                    + " (highestResist=" + pct(highestResist)
                    + (highestWeaponId != null ? ", weapon=" + highestWeaponId : "")
                    + ")");
        }

        // 4) Immunity count changes (log only on change)
        if (immuneCount != snap.lastImmuneCount) {
            snap.lastImmuneCount = immuneCount;
            log("Immunity count changed on hullId=" + safeHullId(ship)
                    + " -> immuneWeaponTypes=" + immuneCount);
        }

        // 5) When a ship *first* gets any meaningful resistance, log once
        if (!snap.loggedFirstResist && highestResist > 0f) {
            snap.loggedFirstResist = true;
            log("First resistance observed on hullId=" + safeHullId(ship)
                    + " (highestResist=" + pct(highestResist)
                    + (highestWeaponId != null ? ", weapon=" + highestWeaponId : "")
                    + ")");
        }

        // 6) Periodic summary (throttled)
        maybeLogThrottled(engine, snap,
                "Summary hullId=" + safeHullId(ship)
                        + " stage=" + maxStage
                        + " immune=" + immuneCount
                        + " topResist=" + pct(highestResist)
                        + (highestWeaponId != null ? " weapon=" + highestWeaponId : ""));
    }

    // =========================================================
    // Helpers
    // =========================================================
    private static void maybeLogThrottled(CombatEngineAPI engine, ShipSnapshot snap, String msg) {
        float now = engine.getTotalElapsedTime(false);
        if (now - snap.lastSummaryLogTime < SUMMARY_LOG_INTERVAL_SEC) return;
        snap.lastSummaryLogTime = now;
        log(msg);
    }

    private static String safeHullId(ShipAPI ship) {
        try {
            return ship.getHullSpec() != null ? ship.getHullSpec().getHullId() : "unknownHull";
        } catch (Throwable t) {
            return "unknownHull";
        }
    }

    private static String pct(float v) {
        int p = Math.round(v * 100f);
        if (p < 0) p = 0;
        if (p > 100) p = 100;
        return p + "%";
    }

    private static void log(String msg) {
        try {
            Global.getLogger(BorgAdaptLogger.class).info("[BORG-ADAPT-DEBUG] " + msg);
        } catch (Throwable ignored) {}
    }

    private static final class ShipSnapshot {
        boolean loggedListener = false;
        boolean loggedFirstResist = false;

        int lastMaxStage = 0;
        int lastImmuneCount = 0;

        float lastSummaryLogTime = -999f;
    }
}