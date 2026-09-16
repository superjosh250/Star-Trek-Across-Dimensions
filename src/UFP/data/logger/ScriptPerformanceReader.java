package UFP.data.logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.log4j.Logger;

public class ScriptPerformanceReader extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(ScriptPerformanceReader.class);

    private static final Map<String, Long> classTimings = new HashMap<>();
    private static final Map<String, Long> classStartTimes = new HashMap<>();
    private static final Map<String, Long> persistentSetupTimings = new HashMap<>();

    private static final Map<String, Long> perShipTimings = new HashMap<>();
    private static final Map<String, Long> perShipStartTimes = new HashMap<>();
    private static final Map<String, Long> aiShipTimings = new HashMap<>();
    private static final Map<String, Long> aiShipStartTimes = new HashMap<>();
    private static final Map<String, Long> operationCounters = new HashMap<>();
    private static final Map<String, Boolean> logToggles = new HashMap<>();

    private static boolean configLoaded = false;
    private static final String CSV_SETTINGS_PATH = "data/config/UFPConfig/logger_settings.csv";

    private float reportTimer = 0f;
    private static final float REPORT_INTERVAL = 3.0f;

    private int frameCount = 0;
    private float fpsTimer = 0f;
    private float currentFps = 0f;

    private long totalModTimeInWindowNanos = 0L;

    // A predefined registry mapping of your known methods to guarantee they appear even if idle
    private static final String[] TRACKED_IDENTIFIER_REGISTRY = new String[] {
            "FederationShields.getShieldHPMap",
            "FederationShields.applyEffectsAfterShipCreation",
            "FederationShields.advanceInCombat",
            "FederationShields.addPostDescriptionSection",
            "FederationShields.getConfiguredShieldHPOrFlux",
            "FederationShields.loadShieldHPDataIfNeeded",
            "FederationShields.splitRow",
            "FederationShields.findColumnIndex",
            "FederationShields.applyVisuals",
            "FederationShields.getSelectedShieldStyle",
            "FederationShields.applyHullStyleColors",
            "FederationShields.EmpDeflectWhenShieldUp.modifyDamageTaken",
            "ShieldHPStatusBar.setupAndSettings",
            "ShieldHPStatusBar.validateDataAndMaps",
            "ShieldHPStatusBar.magicUiDrawCall",
            "ShieldHPStatusBar.checkBattleState",
            "ImpulseEngineMounts.advanceInCombat",
            "ImpulseEngineMounts.applyPenalty",
            "ImpulseEngineMounts.applyBonus",
            "ImpulseEngineMounts.protectThrusters",
    };

    public ScriptPerformanceReader() {
        loadLogTogglesFromCSV();
        prePopulateMetricsPool();
    }

    private void prePopulateMetricsPool() {
        for (String id : TRACKED_IDENTIFIER_REGISTRY) {
            if (id.contains("applyEffects") || id.contains("loadShieldHP") || id.contains("Description")) {
                if (!persistentSetupTimings.containsKey(id)) {
                    persistentSetupTimings.put(id, 0L);
                }
            } else {
                if (!classTimings.containsKey(id)) {
                    classTimings.put(id, 0L);
                }
            }
        }
    }

    public static void countOperation(String counterName) {
        if (!isLoggingEnabled(counterName)) return;
        Long current = operationCounters.get(counterName);
        operationCounters.put(counterName, (current != null ? current : 0L) + 1L);
    }

    private static boolean isLoggingEnabled(String key) {
        if (key == null) return false;
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (logToggles.containsKey(lowerKey)) return logToggles.get(lowerKey);

        for (Map.Entry<String, Boolean> entry : logToggles.entrySet()) {
            if (lowerKey.contains(entry.getKey())) return entry.getValue();
        }
        return true;
    }

    private static void loadLogTogglesFromCSV() {
        if (configLoaded) return;
        configLoaded = true;
        try (InputStream is = Global.getSettings().openStream(CSV_SETTINGS_PATH)) {
            if (is == null) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String header = br.readLine();
                if (header == null) return;
                String[] headerCols = header.split("\\t|\\s*,\\s*");
                int idIdx = -1, boolIdx = -1;
                for (int i = 0; i < headerCols.length; i++) {
                    if (headerCols[i] == null) continue;
                    String norm = headerCols[i].toLowerCase(Locale.ROOT).trim();
                    if (norm.equals("logger_id")) idIdx = i;
                    if (norm.equals("boolean")) boolIdx = i;
                }
                if (idIdx < 0 || boolIdx < 0) return;
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] cols = line.split("\\t|\\s*,\\s*");
                    if (cols.length <= Math.max(idIdx, boolIdx)) continue;

                    String loggerId = cols[idIdx].trim().toLowerCase(Locale.ROOT);
                    boolean isEnabled = !"false".equals(cols[boolIdx].trim().toLowerCase(Locale.ROOT));
                    logToggles.put(loggerId, isEnabled);
                }
            }
        } catch (Exception e) {
            log.warn("ScriptPerformanceReader: Failed loading csv config.");
        }
    }

    public static void startTrack(String identifier) {
        if (!isLoggingEnabled(identifier)) return;
        classStartTimes.put(identifier, System.nanoTime());
    }

    public static void endTrack(String identifier) {
        if (!isLoggingEnabled(identifier)) return;
        Long start = classStartTimes.get(identifier);
        if (start == null) return;

        long elapsed = System.nanoTime() - start;

        if (identifier.contains("applyEffects") || identifier.contains("loadShieldHP") || identifier.contains("Description")) {
            Long currentTotal = persistentSetupTimings.get(identifier);
            persistentSetupTimings.put(identifier, (currentTotal != null ? currentTotal : 0L) + elapsed);
        } else {
            Long currentTotal = classTimings.get(identifier);
            classTimings.put(identifier, (currentTotal != null ? currentTotal : 0L) + elapsed);
        }
    }

    public static void startShipTrack(String identifier, ShipAPI ship) {
        if (ship == null || !isLoggingEnabled(identifier)) return;
        perShipStartTimes.put(ship.getId() + "_" + identifier, System.nanoTime());
    }

    public static void endShipTrack(String identifier, ShipAPI ship) {
        if (ship == null || !isLoggingEnabled(identifier)) return;
        String key = ship.getId() + "_" + identifier;
        Long start = perShipStartTimes.get(key);
        if (start == null) return;

        long elapsed = System.nanoTime() - start;
        Long currentTotal = perShipTimings.get(key);
        perShipTimings.put(key, (currentTotal != null ? currentTotal : 0L) + elapsed);
    }

    public static void startAiShipTrack(String blockName, ShipAPI ship) {
        if (ship == null || !isLoggingEnabled(blockName) || ship == Global.getCombatEngine().getPlayerShip()) return;
        aiShipStartTimes.put(blockName + " (AI Ship: " + ship.getHullSpec().getHullId() + ")", System.nanoTime());
    }

    public static void endAiShipTrack(String blockName, ShipAPI ship) {
        if (ship == null || !isLoggingEnabled(blockName) || ship == Global.getCombatEngine().getPlayerShip()) return;
        String key = blockName + " (AI Ship: " + ship.getHullSpec().getHullId() + ")";
        Long startTime = aiShipStartTimes.get(key);
        if (startTime == null) return;

        long duration = System.nanoTime() - startTime;
        Long currentSum = aiShipTimings.get(key);
        aiShipTimings.put(key, (currentSum != null ? currentSum : 0L) + duration);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        frameCount++;
        fpsTimer += amount;
        if (fpsTimer >= 1.0f) {
            currentFps = frameCount / fpsTimer;
            frameCount = 0;
            fpsTimer = 0f;
        }

        if (engine.isPaused()) return;

        reportTimer += amount;
        if (reportTimer >= REPORT_INTERVAL) {
            totalModTimeInWindowNanos = 0L;
            for (long timing : classTimings.values()) {
                totalModTimeInWindowNanos += timing;
            }

            logPerformanceMetrics();
            reportTimer = 0f;
        }
    }

    private void logPerformanceMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        double totalWindowBudgetMs = REPORT_INTERVAL * 1000.0;
        double totalModTimeMs = totalModTimeInWindowNanos / 1_000_000.0;
        double modFrameImpactPercent = (totalModTimeMs / totalWindowBudgetMs) * 100.0;

        log.info("============== UFP PERFORMANCE PROFILE REPORT ==============");
        log.info(String.format("Global Engine State -> FPS: %.1f | Used RAM: %dMB / %dMB", currentFps, usedMemory, totalMemory));
        log.info(String.format("Thread Cost Profile -> Total Tracked Mod Overhead: %.3f ms over 3s window", totalModTimeMs));
        log.info(String.format("                    -> Direct Mod Frame Impact: %.2f%% of Engine Thread Budget", modFrameImpactPercent));
        log.info("------------------------------------------------------------");
        log.info("Active Mod Metrics (Captured over last 3 seconds):");

        for (Map.Entry<String, Long> entry : classTimings.entrySet()) {
            double milliTime = entry.getValue() / 1_000_000.0;
            // REMOVED THE > 0.001 GATE GATEWAY ENTIRELY TO FORCE IDLE OUTPUTS
            log.info(String.format("   > %s: %.3f ms", entry.getKey(), milliTime));
        }

        if (!persistentSetupTimings.isEmpty()) {
            log.info("------------------------------------------------------------");
            log.info("One-Time HullMod Setup & Parsing Metrics (Persistent Across Windows):");
            for (Map.Entry<String, Long> entry : persistentSetupTimings.entrySet()) {
                log.info(String.format("   > [Setup] %s: %.3f ms total accumulative", entry.getKey(), entry.getValue() / 1_000_000.0));
            }
        }

        if (!operationCounters.isEmpty()) {
            log.info("------------------------------------------------------------");
            log.info("Diagnostic Counters:");
            for (Map.Entry<String, Long> counter : operationCounters.entrySet()) {
                log.info(String.format("   > [Counter] %s: %d total calls (~%.1f ops/sec)",
                        counter.getKey(), counter.getValue(), counter.getValue() / (double) REPORT_INTERVAL));
            }
        }

        if (!aiShipTimings.isEmpty()) {
            log.info("------------------------------------------------------------");
            log.info("Isolated Performance Metrics for Active Non-Player Hulls:");
            for (Map.Entry<String, Long> aiEntry : aiShipTimings.entrySet()) {
                log.info(String.format("   > %s: %.3f ms", aiEntry.getKey(), aiEntry.getValue() / 1_000_000.0));
            }
        }

        if (!perShipTimings.isEmpty()) {
            log.info("------------------------------------------------------------");
            log.info("Top Ship-Specific Culprits (Worst Performance Sinks):");
            perShipTimings.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(3)
                    .forEach(entry -> log.info(String.format("   > Ship Context [%s] -> %.3f ms", entry.getKey(), entry.getValue() / 1_000_000.0)));
        }
        log.info("============================================================");

        // Reset runtime values back to 0ms entries instead of clearing entirely
        classTimings.clear();
        classStartTimes.clear();
        prePopulateMetricsPool();

        perShipTimings.clear();
        perShipStartTimes.clear();
        operationCounters.clear();
        aiShipTimings.clear();
        aiShipStartTimes.clear();
    }
}