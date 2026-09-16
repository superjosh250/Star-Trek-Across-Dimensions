package BORG.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.*;

/**
 * Utility class for Borg Hive Mind synchronization.
 * Facilitates shared learning, reset syncing, and collective immunity.
 */
public class SharedKnowledge {

    // --- EXPOSED CONFIGURATION ---
    public static float SHARED_CONTRIBUTION_MULT = 0.50f;
    public static int MAX_SHIPS_IN_LINK = 3;
    public static boolean SYNC_IMMUNITY_GLOBALLY = true;

    // --- NEW HARD-LOCK CONFIGURATION ---
    public static int BASE_LEARNING_SLOTS = 1;           // Default limit
    public static int BONUS_LEARNING_SLOTS = 1;          // Additional slots granted by special hull
    public static String HULL_ID_BONUS_KEY = "BORG_CORE_PROCESSOR_HULL_ID"; // Key for external class to define hull

    public static final String HIVE_LEARNING_KEY = "BORG_HIVE_LEARNING_MAP";
    public static final String HIVE_IMMUNITY_KEY = "BORG_HIVE_IMMUNITY_SET";
    public static final String ACTIVE_FOCUS_KEY = "BORG_HIVE_ACTIVE_FOCUS"; // Tracking current weapon IDs

    private static final Random RNG = new Random();

    /**
     * Updates collective hive knowledge with slot hard-locks.
     */
    public static void synchronizeProgress(ShipAPI ship, String weaponId, float amountGained) {
        if (isGloballyImmune(weaponId)) return;

        List<String> activeFocus = getActiveFocusList();

        // Check if we are already learning this weapon
        if (!activeFocus.contains(weaponId)) {
            int maxSlots = BASE_LEARNING_SLOTS + (isSpecialHullPresent() ? BONUS_LEARNING_SLOTS : 0);

            // Hard-lock: If slots are full, we cannot learn a new weapon yet
            if (activeFocus.size() >= maxSlots) {
                return;
            }

            // Random Choice: If a slot is free, 50% chance to lock onto this new signature
            if (RNG.nextFloat() < 0.5f) {
                activeFocus.add(weaponId);
            } else {
                return;
            }
        }

        Map<String, Float> hiveProgress = getHiveLearningMap();
        float currentPool = hiveProgress.getOrDefault(weaponId, 0f);

        float assistanceCount = Math.max(0, MAX_SHIPS_IN_LINK - 1);
        float collectiveBonus = amountGained * SHARED_CONTRIBUTION_MULT * assistanceCount;
        float totalNewProgress = currentPool + amountGained + collectiveBonus;

        if (totalNewProgress >= 1.0f) {
            totalNewProgress = 1.0f;
            if (SYNC_IMMUNITY_GLOBALLY) {
                grantGlobalImmunity(weaponId);
                activeFocus.remove(weaponId); // Clear slot upon immunity
            }
        }

        hiveProgress.put(weaponId, totalNewProgress);
        syncFleetToHive(weaponId, totalNewProgress);
    }

    /**
     * Checks if the special hull (defined by external key) is on the field.
     */
    private static boolean isSpecialHullPresent() {
        String targetHullId = (String) Global.getCombatEngine().getCustomData().get(HULL_ID_BONUS_KEY);
        if (targetHullId == null) return false;

        for (ShipAPI s : Global.getCombatEngine().getShips()) {
            if (s.isAlive() && s.getHullSpec().getHullId().equals(targetHullId)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getActiveFocusList() {
        var data = Global.getCombatEngine().getCustomData().get(ACTIVE_FOCUS_KEY);
        if (data == null) {
            List<String> list = new ArrayList<>();
            Global.getCombatEngine().getCustomData().put(ACTIVE_FOCUS_KEY, list);
            return list;
        }
        return (List<String>) data;
    }

    /**
     * Propagates a weapon reset and ensures the Hive stays focused.
     */
    public static void syncReset(String weaponId, int stage, float floor) {
        // Logic omitted for brevity, remains same as provided in source
        List<ShipAPI> ships = Global.getCombatEngine().getShips();
        int count = 0;
        for (ShipAPI s : ships) {
            if (s.isFighter() || !s.isAlive()) continue;
            if (count >= MAX_SHIPS_IN_LINK) break;
            if (s.hasListenerOfClass(BorgAdaptationMatrix.class)) {
                BorgAdaptationMatrix.AdaptationState state = BorgAdaptationMatrix.getAdaptationState(s, weaponId);
                if (state.failureStage < stage) {
                    state.failureStage = stage;
                    state.currentResistance = Math.max(state.currentResistance, floor);
                    count++;
                }
            }
        }
        getHiveLearningMap().put(weaponId, floor);
    }

    private static void syncFleetToHive(String weaponId, float progress) {
        for (ShipAPI s : Global.getCombatEngine().getShips()) {
            if (s.isFighter() || !s.isAlive() || !s.hasListenerOfClass(BorgAdaptationMatrix.class)) continue;
            BorgAdaptationMatrix.AdaptationState state = BorgAdaptationMatrix.getAdaptationState(s, weaponId);
            if (isGloballyImmune(weaponId)) {
                state.isFullyImmune = true;
                state.currentResistance = 1.0f;
            } else {
                state.currentResistance = progress;
            }
        }
    }

    public static void grantGlobalImmunity(String weaponId) {
        getHiveImmunitySet().add(weaponId);
    }

    public static boolean isGloballyImmune(String weaponId) {
        return getHiveImmunitySet().contains(weaponId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Float> getHiveLearningMap() {
        var data = Global.getCombatEngine().getCustomData().get(HIVE_LEARNING_KEY);
        if (data == null) {
            Map<String, Float> map = new HashMap<>();
            Global.getCombatEngine().getCustomData().put(HIVE_LEARNING_KEY, map);
            return map;
        }
        return (Map<String, Float>) data;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getHiveImmunitySet() {
        var data = Global.getCombatEngine().getCustomData().get(HIVE_IMMUNITY_KEY);
        if (data == null) {
            Set<String> set = new HashSet<>();
            Global.getCombatEngine().getCustomData().put(HIVE_IMMUNITY_KEY, set);
            return set;
        }
        return (Set<String>) data;
    }
}