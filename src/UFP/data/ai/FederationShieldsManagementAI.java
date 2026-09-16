package UFP.data.ai;

import UFP.data.logger.ShieldManagementAI_Logger;
import UFP.data.util.ShieldHitpointManager;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

public class FederationShieldsManagementAI implements EveryFrameCombatPlugin {

    // --- Engine key: register plugin once per combat ---
    public static final String KEY_PLUGIN_REGISTERED = "ufp_fedshield_ai_plugin_registered";

    // --- Ship key: hullmod sets this to opt-in the ship for management ---
    public static final String KEY_ENABLED_ON_SHIP = "ufp_fedshield_ai_enabled";

    // --- Station-only latches ---
    private static final String KEY_PREV_DISABLED = "ufp_fedshield_prev_disabled";
    private static final String KEY_BROKEN_RAISE_ATTEMPTED = "ufp_fedshield_broken_raise_attempted";
    private static final String KEY_LOG_BROKEN_ACTIVE = "ufp_fedshield_log_broken_active";

    private static final float FLAG_TICK = 0.25f;

    @Override
    public void init(CombatEngineAPI engine) {
        // no-op
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        ShipAPI player = engine.getPlayerShip();

        for (ShipAPI ship : engine.getShips()) {
            if (ship == null) continue;
            if (!ship.isAlive()) continue;
            if (ship == player) continue;
            if (ship.getShield() == null) continue;
            if (!Boolean.TRUE.equals(ship.getCustomData().get(KEY_ENABLED_ON_SHIP))) continue;

            // ✅ ONLY manage stations
            if (!isStationShip(ship)) continue;

            handleStationShieldLogic(ship);
        }
    }

    // =========================
    // STATION LOGIC (KEEP SHIELDS UP)
    // =========================
    private void handleStationShieldLogic(ShipAPI ship) {
        ShieldAPI shield = ship.getShield();
        ShipwideAIFlags flags = ship.getAIFlags();

        boolean disabled = ShieldHitpointManager.isShieldDisabled(ship);
        boolean prevDisabled = getBool(ship, KEY_PREV_DISABLED);

        // -------------------------
        // Shield is BROKEN
        // -------------------------
        if (disabled) {

            if (!getBool(ship, KEY_LOG_BROKEN_ACTIVE)) {
                ship.setCustomData(KEY_LOG_BROKEN_ACTIVE, Boolean.TRUE);
                ShieldManagementAI_Logger.brokenEntered(ship, true);
            }

            if (!prevDisabled && !getBool(ship, KEY_BROKEN_RAISE_ATTEMPTED)) {
                boolean toggled = false;
                if (shield != null && !shield.isOn()) {
                    shield.toggleOn();
                    toggled = true;
                }
                ship.setCustomData(KEY_BROKEN_RAISE_ATTEMPTED, Boolean.TRUE);
                ShieldManagementAI_Logger.brokenToggleAttempt(ship, true, 1, toggled);
            }

            ship.setCustomData(KEY_PREV_DISABLED, Boolean.TRUE);
            return;
        }

        // -------------------------
        // Shield RESTORED
        // -------------------------
        if (prevDisabled) {
            ship.getCustomData().remove(KEY_BROKEN_RAISE_ATTEMPTED);
            ship.getCustomData().remove(KEY_LOG_BROKEN_ACTIVE);
            ShieldManagementAI_Logger.brokenRestored(ship, true);
        }
        ship.setCustomData(KEY_PREV_DISABLED, Boolean.FALSE);

        // -------------------------
        // Normal operation:
        // Keep station shields ON
        // -------------------------
        if (flags != null) {
            flags.setFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON, FLAG_TICK);
        }

        if (shield != null && !shield.isOn()) {
            shield.toggleOn();
        }
    }

    // =========================
    // Helpers
    // =========================
    private boolean isStationShip(ShipAPI ship) {
        if (ship == null) return false;
        if (ship.isStation() || ship.isStationModule()) return true;
        if (ship.getHullSpec() != null && ship.getHullSpec().getTags() != null) {
            return ship.getHullSpec().getTags().contains(Tags.STATION);
        }
        return false;
    }

    private boolean getBool(ShipAPI ship, String key) {
        Object o = ship.getCustomData().get(key);
        return (o instanceof Boolean) && (Boolean) o;
    }

    @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) { }
    @Override public void renderInWorldCoords(ViewportAPI viewport) { }
    @Override public void renderInUICoords(ViewportAPI viewport) { }
}