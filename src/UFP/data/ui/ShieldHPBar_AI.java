package UFP.data.ui;

import UFP.data.logger.ScriptPerformanceReader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicUI;

import java.awt.Color;
import java.util.List;
import java.util.Map;

public class ShieldHPBar_AI implements EveryFrameCombatPlugin {

    public static final String SHOW_SHIELD_HP_BAR_KEY = "ufp_show_shield_hp_bar";
    private static final String SHIELD_HP_KEY = "universal_shield_hp", SHIELD_HP_MAX_KEY = "universal_shield_hp_max", SHIELD_DISABLED_KEY = "universal_shield_disabled";

    public static final String UFP_BAR_DATA_CONTAINER_KEY = "ufp_shieldbar_data_container";

    private static final float BAR_WIDTH = 55f, BAR_HEIGHT = 10f, Y_OFFSET_PX = 28f;
    // Lowering the max tracking distance tightly bounds off-screen scaling overhead
    private static final float NEAR_VIEWPORT_DIST_SQR = 1000f * 1000f;

    private static final Color DEFAULT_INNER = new Color(210, 210, 210, 200), DEFAULT_BORDER = new Color(245, 245, 245, 230), DISABLED_BORDER = new Color(255, 80, 80, 240);
    private static Color cachedFriendInner = DEFAULT_INNER, cachedFriendBorder = DEFAULT_BORDER, cachedEnemyInner = DEFAULT_INNER, cachedEnemyBorder = DEFAULT_BORDER;
    private static boolean colorsCached = false;

    private CombatEngineAPI engine;
    private final MagicUI ui = new MagicUI();
    private final Vector2f screenPosCache = new Vector2f();

    public static class ShieldBarState {
        public boolean isEligible;
        public boolean showBar = true;
        public float currentHp = 0f;
        public float maxHp = 0f;
        public boolean isDisabled = false;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        cacheGlobalColors();
    }

    private static void cacheGlobalColors() {
        if (colorsCached) return;
        try {
            Color friend = Global.getSettings().getColor("textFriendColor");
            if (friend != null) {
                cachedFriendInner = new Color(friend.getRed(), friend.getGreen(), friend.getBlue(), 200);
                cachedFriendBorder = new Color(Math.min(255, (int)(friend.getRed()*1.2f)), Math.min(255, (int)(friend.getGreen()*1.2f)), Math.min(255, (int)(friend.getBlue()*1.2f)), 230);
            }
            Color enemy = Global.getSettings().getColor("textEnemyColor");
            if (enemy != null) {
                cachedEnemyInner = new Color(enemy.getRed(), enemy.getGreen(), enemy.getBlue(), 200);
                cachedEnemyBorder = new Color(Math.min(255, (int)(enemy.getRed()*1.2f)), Math.min(255, (int)(enemy.getGreen()*1.2f)), Math.min(255, (int)(enemy.getBlue()*1.2f)), 230);
            }
            colorsCached = true;
        } catch (Throwable ignored) {}
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        ScriptPerformanceReader.startTrack("shieldhpbar_advance");
        ScriptPerformanceReader.endTrack("shieldhpbar_advance");
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        ScriptPerformanceReader.startTrack("shieldhpbar_input");
        ScriptPerformanceReader.endTrack("shieldhpbar_input");
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        ScriptPerformanceReader.startTrack("shieldhpbar_world_render");
        ScriptPerformanceReader.endTrack("shieldhpbar_world_render");
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        ScriptPerformanceReader.startTrack("shieldhpbar_total");
        if (engine == null || engine.isPaused()) { ScriptPerformanceReader.endTrack("shieldhpbar_total"); return; }

        ShipAPI playerShip = engine.getPlayerShip();
        int playerOwner = (playerShip != null) ? playerShip.getOwner() : 0;
        List<ShipAPI> ships = engine.getShips();
        if (ships == null || ships.isEmpty()) { ScriptPerformanceReader.endTrack("shieldhpbar_total"); return; }

        float offsetWorld = viewport.convertScreenHeightToWorldHeight(Y_OFFSET_PX);
        float halfBarWidth = BAR_WIDTH * 0.5f;
        Vector2f viewCenter = viewport.getCenter();

        // BATCH START: Initialize OpenGL configurations once globally
        ScriptPerformanceReader.startTrack("shieldhpbar_render_api");
        boolean batchOpened = false;
        try {
            MagicUI.openGLForMiscWithinViewport();
            ui.setViewportOpenGLCalls(false);
            batchOpened = true;
        } catch (Throwable ignored) {}
        ScriptPerformanceReader.endTrack("shieldhpbar_render_api");

        // High-frequency loop optimized via early exit fast-paths
        for (int i = 0, size = ships.size(); i < size; i++) {
            ShipAPI ship = ships.get(i);

            // 1. Primitive Engine-Level Short Circuits (Bypasses Map Lookups entirely)
            if (ship == null || !ship.isAlive() || ship == playerShip || ship.isFighter()) { continue; }

            // 2. Spatial Square-Distance Check (Extremely fast, prevents processing off-screen ships)
            float dx = ship.getLocation().x - viewCenter.x;
            float dy = ship.getLocation().y - viewCenter.y;
            if ((dx * dx + dy * dy) > NEAR_VIEWPORT_DIST_SQR) { continue; }

            Map<String, Object> customData = ship.getCustomData();
            if (customData == null || customData.isEmpty()) { continue; }

            Object containerObj = customData.get(UFP_BAR_DATA_CONTAINER_KEY);
            ShieldBarState state;

            if (containerObj instanceof ShieldBarState) {
                state = (ShieldBarState) containerObj;
                if (!state.isEligible) { continue; }
            } else {
                state = new ShieldBarState();
                state.isEligible = ship.getAIFlags() != null;

                Object legacyShow = customData.get(SHOW_SHIELD_HP_BAR_KEY);
                if (legacyShow instanceof Boolean) state.showBar = (Boolean) legacyShow;
                Object legacyMax = customData.get(SHIELD_HP_MAX_KEY);
                if (legacyMax instanceof Number) state.maxHp = ((Number) legacyMax).floatValue();
                Object legacyCur = customData.get(SHIELD_HP_KEY);
                if (legacyCur instanceof Number) state.currentHp = ((Number) legacyCur).floatValue();
                Object legacyDis = customData.get(SHIELD_DISABLED_KEY);
                if (legacyDis instanceof Boolean) state.isDisabled = (Boolean) legacyDis;

                customData.put(UFP_BAR_DATA_CONTAINER_KEY, state);
                if (!state.isEligible) { continue; }
            }

            if (!state.showBar) { continue; }
            if (state.maxHp <= 0f) {
                Object maxObj = customData.get(SHIELD_HP_MAX_KEY);
                state.maxHp = (maxObj instanceof Number) ? ((Number) maxObj).floatValue() : 0f;
                if (state.maxHp <= 0f) { continue; }
            }

            // Stripped out inner per-ship tracking to eliminate tracking initialization overhead completely
            float fill = clamp01(state.currentHp / state.maxHp);

            screenPosCache.x = viewport.convertWorldXtoScreenX(ship.getLocation().x) - halfBarWidth;
            screenPosCache.y = viewport.convertWorldYtoScreenY(ship.getLocation().y + offsetWorld);

            boolean friendlyToPlayer = (ship.getOwner() == playerOwner);
            Color inner = friendlyToPlayer ? cachedFriendInner : cachedEnemyInner;
            Color border = state.isDisabled ? DISABLED_BORDER : (friendlyToPlayer ? cachedFriendBorder : cachedEnemyBorder);

            // Directly invoke UI bar injection natively inside the master batch environment
            ui.addBar(ship, fill, inner, border, 0f, screenPosCache, BAR_HEIGHT, BAR_WIDTH, true);
        }

        // BATCH END: Tear down batch matrix environment safely
        if (batchOpened) {
            ScriptPerformanceReader.startTrack("shieldhpbar_render_api");
            try {
                ui.setViewportOpenGLCalls(true);
                MagicUI.closeGLForMiscWithinViewport();
            } catch (Throwable ignored) {}
            ScriptPerformanceReader.endTrack("shieldhpbar_render_api");
        }

        ScriptPerformanceReader.endTrack("shieldhpbar_total");
    }

    public static void enableForShip(ShipAPI ship) {
        if (ship == null) return;
        ship.setCustomData(SHOW_SHIELD_HP_BAR_KEY, true);
        Object obj = ship.getCustomData().get(UFP_BAR_DATA_CONTAINER_KEY);
        if (obj instanceof ShieldBarState) ((ShieldBarState) obj).showBar = true;
    }

    public static void disableForShip(ShipAPI ship) {
        if (ship == null) return;
        ship.setCustomData(SHOW_SHIELD_HP_BAR_KEY, false);
        Object obj = ship.getCustomData().get(UFP_BAR_DATA_CONTAINER_KEY);
        if (obj instanceof ShieldBarState) ((ShieldBarState) obj).showBar = false;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}