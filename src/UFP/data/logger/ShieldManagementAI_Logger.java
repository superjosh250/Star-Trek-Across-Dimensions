package UFP.data.logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * ShieldManagementAI_Logger
 *
 * Lightweight, non-spammy combat logger for FederationShieldsManagementAI.
 * - Logs only when called by the AI (does not self-run).
 * - Per-ship + per-event cooldown to prevent spam.
 * - Designed for Starsector 0.98 / Java 17.
 */
public final class ShieldManagementAI_Logger {

    private static final Logger LOG = Global.getLogger(ShieldManagementAI_Logger.class);

    /** Hard kill-switch */
    public static boolean ENABLED = true;

    /** Minimum seconds between logs for the same ship+event key */
    private static final float EVENT_COOLDOWN = 1.25f;

    /** shipId:eventKey -> lastLoggedTime */
    private static final Map<String, Float> LAST_LOG_TIME = new HashMap<>();

    private ShieldManagementAI_Logger() { }

    // ------------------------
    // Public logging entrypoints
    // ------------------------

    public static void lowShieldSuggestion(ShipAPI ship, boolean station, float frac, float fluxLevel,
                                           boolean unsetDoNotVent, boolean setOkCancelToVent, boolean setNeedsHelp,
                                           boolean forcedEvaluation) {
        if (!ENABLED || ship == null) return;

        logOnce(ship, station ? "station_low_shield" : "ship_low_shield",
                String.format("[ShieldAI] %s (%s) low ShieldHP: %.1f%% | flux=%.2f | " +
                                "unset_DO_NOT_VENT=%s, set_OK_CANCEL_TO_VENT=%s, set_NEEDS_HELP=%s, forceEval=%s",
                        safeName(ship),
                        station ? "STATION" : "SHIP",
                        frac * 100f,
                        fluxLevel,
                        unsetDoNotVent, setOkCancelToVent, setNeedsHelp, forcedEvaluation
                ));
    }

    public static void brokenEntered(ShipAPI ship, boolean station) {
        if (!ENABLED || ship == null) return;

        logOnce(ship, station ? "station_broken_enter" : "ship_broken_enter",
                String.format("[ShieldAI] %s (%s) SHIELD BROKEN: entered disabled state",
                        safeName(ship), station ? "STATION" : "SHIP"));
    }

    public static void brokenToggleAttempt(ShipAPI ship, boolean station, int attemptNumber, boolean toggledOn) {
        if (!ENABLED || ship == null) return;

        logOnce(ship, (station ? "station_toggle_" : "ship_toggle_") + attemptNumber,
                String.format("[ShieldAI] %s (%s) broken-toggle attempt #%d | toggledOn=%s",
                        safeName(ship), station ? "STATION" : "SHIP", attemptNumber, toggledOn));
    }

    public static void brokenToggleBlocked(ShipAPI ship, boolean station, String reason) {
        if (!ENABLED || ship == null) return;

        logOnce(ship, station ? "station_toggle_blocked" : "ship_toggle_blocked",
                String.format("[ShieldAI] %s (%s) broken-toggle BLOCKED: %s",
                        safeName(ship), station ? "STATION" : "SHIP", reason));
    }

    public static void brokenRestored(ShipAPI ship, boolean station) {
        if (!ENABLED || ship == null) return;

        logOnce(ship, station ? "station_broken_restored" : "ship_broken_restored",
                String.format("[ShieldAI] %s (%s) shield restored: disabled cleared / latches reset",
                        safeName(ship), station ? "STATION" : "SHIP"));
    }

    public static void enemyPressureNudge(ShipAPI brokenTarget, int nudgedCount) {
        if (!ENABLED || brokenTarget == null) return;

        logOnce(brokenTarget, "enemy_pressure_nudge",
                String.format("[ShieldAI] %s (TARGET) enemy pressure nudge applied to %d attackers",
                        safeName(brokenTarget), nudgedCount));
    }

    // ------------------------
// Additional AI nudge logging
// ------------------------

    public static void shieldDropNudge(
            ShipAPI ship,
            float shieldFrac,
            float fluxLevel,
            boolean forcedEvaluation
    ) {
        if (!ENABLED || ship == null) return;

        logOnce(ship, "shield_drop_nudge",
                String.format(
                        "[ShieldAI] %s SHIELD DROP NUDGE: shield=%.1f%% flux=%.2f forcedEval=%s",
                        safeName(ship),
                        shieldFrac * 100f,
                        fluxLevel,
                        forcedEvaluation
                )
        );
    }

    public static void forcedVentCommand(ShipAPI ship, float fluxLevel) {
        if (!ENABLED || ship == null) return;

        logOnce(ship, "forced_vent",
                String.format(
                        "[ShieldAI] %s FORCED VENT COMMAND: flux=%.2f",
                        safeName(ship),
                        fluxLevel
                )
        );
    }

    // ------------------------
    // Internals (throttling)
    // ------------------------

    private static void logOnce(ShipAPI ship, String eventKey, String msg) {
        try {
            if (!ENABLED) return;
            if (Global.getCombatEngine() == null) return;

            float now = Global.getCombatEngine().getTotalElapsedTime(false);
            String key = ship.getId() + ":" + eventKey;

            Float last = LAST_LOG_TIME.get(key);
            if (last != null && (now - last) < EVENT_COOLDOWN) return;

            LAST_LOG_TIME.put(key, now);
            LOG.info(msg);
        } catch (Throwable t) {
            // Never break combat because of logging
        }
    }

    private static String safeName(ShipAPI ship) {
        try {
            String hull = ship.getHullSpec() != null ? ship.getHullSpec().getHullName() : "UnknownHull";
            return hull + " [" + ship.getId() + "]";
        } catch (Throwable t) {
            return "UnknownShip";
        }
    }
}