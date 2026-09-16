package UFP.data.ui;

import UFP.data.util.ShieldHitpointManager;
import UFP.data.logger.ScriptPerformanceReader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.magiclib.util.MagicUI;

import java.awt.Color;

public class ShieldHPStatusBar {

    private static final String BAR_KEY = "universal_shield_hp_bar";
    private static final String LABEL_TEXT = "Shields"; // Avoids runtime string creation costs
    private static final Color DEFAULT_INNER_COLOR = new Color(111, 158, 96, 230);
    private static final Color DEFAULT_BORDER_COLOR = new Color(50, 50, 50, 200);

    private static boolean battleStartedLogged = false;
    private static boolean battleEndedLogged = false;

    // Standardized registry keys matching your profiler's naming convention
    public static final String METRIC_VALIDATE = "ShieldHPStatusBar.validateDataAndMaps";
    public static final String METRIC_DRAW = "ShieldHPStatusBar.magicUiDrawCall";
    public static final String METRIC_STATE = "ShieldHPStatusBar.checkBattleState";
    public static final String SHIP_CONTEXT_ID = "shieldhpbar_pership";

    private static final String RUNTIME_STATE_KEY = "ufp_shieldhp_bar_state";

    // Frame throttle interval: Processes data changes every 3 frames (~40 updates/sec at 120 FPS)
    private static final int DATA_UPDATE_INTERVAL_FRAMES = 3;

    // Lightweight, flat data holder stored directly in customData to eliminate autoboxing overhead
    private static class BarRuntimeState {
        final boolean isHpShield;
        final Color fillColor;
        final Color borderColor;

        // Pure primitives cached locally to bypass repetitive map lookup layers completely
        float cachedFill = 0f;
        int cachedHpDisplay = 0;
        int frameCounter = 0;
        boolean firstRun = true;

        BarRuntimeState(ShipAPI ship) {
            this.isHpShield = ship.getCustomData().containsKey("universal_shield_hp");
            Color fill = (Color) ship.getCustomData().get("universal_shield_hp_bar_color");
            Color border = (Color) ship.getCustomData().get("universal_shield_hp_border_color");
            this.fillColor = (fill != null) ? fill : DEFAULT_INNER_COLOR;
            this.borderColor = (border != null) ? border : DEFAULT_BORDER_COLOR;
        }
    }

    public static void render(ShipAPI ship, CombatEngineAPI engine) {
        // FAST PATH: Enforce exclusion immediately with a single base memory reference check.
        if (ship == null || engine == null || ship != engine.getPlayerShip() || !ship.isAlive() || ship.getShield() == null) {
            return;
        }

        ScriptPerformanceReader.startShipTrack(SHIP_CONTEXT_ID, ship);

        // One-time startup log (no disk I/O required)
        if (!battleStartedLogged) {
            Global.getLogger(ShieldHPStatusBar.class).info("Battle started: ShieldHPStatusBar active.");
            battleStartedLogged = true;
        }

        // 1. High-Performance Structural Validation & Primitives Layer Check
        ScriptPerformanceReader.startTrack(METRIC_VALIDATE);
        ScriptPerformanceReader.countOperation(METRIC_VALIDATE);

        BarRuntimeState state = null;
        boolean passedValidation = false;

        try {
            Object stateObj = ship.getCustomData().get(RUNTIME_STATE_KEY);
            if (stateObj instanceof BarRuntimeState) {
                state = (BarRuntimeState) stateObj;
            } else {
                state = new BarRuntimeState(ship);
                ship.getCustomData().put(RUNTIME_STATE_KEY, state);
            }

            if (state.isHpShield) {
                state.frameCounter++;
                // Throttle structural updates to clear out continuous unboxing latency every single frame
                if (state.firstRun || state.frameCounter >= DATA_UPDATE_INTERVAL_FRAMES) {
                    state.frameCounter = 0;
                    state.firstRun = false;

                    // Fetch cleanly via your manager class API
                    float currentHP = ShieldHitpointManager.getShieldHP(ship);
                    float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);

                    if (maxHP > 0f && currentHP >= 0f) {
                        state.cachedFill = currentHP / maxHP;
                        state.cachedHpDisplay = (int) (state.cachedFill * 100f);
                    }
                }
                passedValidation = true;
            }
        } finally {
            ScriptPerformanceReader.endTrack(METRIC_VALIDATE);
        }

        if (!passedValidation) {
            ScriptPerformanceReader.endShipTrack(SHIP_CONTEXT_ID, ship);
            return;
        }

        // 2. UI Status Bar Render Cost Capture
        ScriptPerformanceReader.startTrack(METRIC_DRAW);
        ScriptPerformanceReader.countOperation(METRIC_DRAW);
        try {
            MagicUI.drawInterfaceStatusBar(
                    ship,
                    BAR_KEY,
                    state.cachedFill,     // Primitive float read
                    state.fillColor,
                    state.borderColor,
                    0f,
                    LABEL_TEXT,           // Bypasses local string instantiation costs
                    state.cachedHpDisplay // Primitive int read
            );
        } finally {
            ScriptPerformanceReader.endTrack(METRIC_DRAW);
        }

        // 3. Combat State Termination Log Block
        if (!battleEndedLogged && engine.isCombatOver()) {
            ScriptPerformanceReader.startTrack(METRIC_STATE);
            ScriptPerformanceReader.countOperation(METRIC_STATE);
            try {
                Global.getLogger(ShieldHPStatusBar.class).info("Battle ended: ShieldHPStatusBar completed.");
                battleEndedLogged = true;
            } finally {
                ScriptPerformanceReader.endTrack(METRIC_STATE);
            }
        }

        ScriptPerformanceReader.endShipTrack(SHIP_CONTEXT_ID, ship);
    }
}