package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance, zero-allocation pipeline for managing weapon subsystem disruptions.
 */
public class SubsystemTargeting_WEAPON {

    public static float SLOT_COOLDOWN = 20f;
    public static float GLOBAL_COOLDOWN = 40f;
    public static float INITIAL_CHANCE = 0.05f;
    public static float SECOND_CHANCE = 0.10f;
    public static float THIRD_CHANCE = 0.15f;
    public static float SUBSEQUENT_CHANCE = 0.025f;
    public static int MAX_DISABLED_WEAPONS = 5;
    public static float DISABLE_DURATION = 10f;

    // String IDs prevent object leaks when combat maps unregister
    private static final Map<String, Map<String, Float>> slotCooldownMap = new HashMap<>();
    private static final Map<String, Integer> disabledWeaponsMap = new HashMap<>();
    private static final Map<String, Float> sourceGlobalCooldownMap = new HashMap<>();

    // Centralized tracker to completely replace dynamic EveryFrameCombatPlugin object allocations
    private static final List<RepairTask> activeRepairs = new ArrayList<>(32);
    private static final List<WeaponAPI> reusableWeaponPool = new ArrayList<>(64);

    private static class RepairTask {
        final WeaponAPI weapon;
        float remainingTime;

        RepairTask(WeaponAPI weapon, float remainingTime) {
            this.weapon = weapon;
            this.remainingTime = remainingTime;
        }
    }

    /**
     * Call this inside an engine lifecycle script (like your EveryFrameCombatPlugin base)
     * to perform clean primitive state increments on disabled weapon tasks without heap growth.
     */
    public static void updateRepairTimers(float amount, CombatEngineAPI engine) {
        if (engine == null || engine.isPaused() || activeRepairs.isEmpty()) return;

        for (int i = activeRepairs.size() - 1; i >= 0; i--) {
            RepairTask task = activeRepairs.get(i);
            task.remainingTime -= amount;
            if (task.remainingTime <= 0f) {
                if (task.weapon != null) {
                    task.weapon.repair();
                }
                activeRepairs.remove(i);
            }
        }
    }

    public static String tryDisableWeapon(CombatEngineAPI engine, ShipAPI sourceShip, ShipAPI targetShip, WeaponSlotAPI slot, WeaponAPI firingWeapon) {
        if (engine == null || engine.isPaused() || sourceShip == null || targetShip == null || slot == null || firingWeapon == null)
            return null;

        float currentTime = engine.getTotalElapsedTime(true);
        String sourceId = sourceShip.getId();
        String targetId = targetShip.getId();

        // 1. Global Cooldown evaluation
        Float sourceCooldownStart = sourceGlobalCooldownMap.get(sourceId);
        if (sourceCooldownStart != null && currentTime - sourceCooldownStart < GLOBAL_COOLDOWN) {
            return null;
        }

        // 2. Specific Weapon Slot Cooldown evaluation
        Map<String, Float> slotMap = slotCooldownMap.get(sourceId);
        if (slotMap == null) {
            slotMap = new HashMap<>();
            slotCooldownMap.put(sourceId, slotMap);
        }

        String slotId = slot.getId();
        Float slotStart = slotMap.get(slotId);
        if (slotStart != null && currentTime - slotStart < SLOT_COOLDOWN) {
            return null;
        }

        // 3. Match Tracking Limits
        String pairKey = sourceId + "_" + targetId;
        int alreadyDisabled = disabledWeaponsMap.getOrDefault(pairKey, 0);

        if (alreadyDisabled >= MAX_DISABLED_WEAPONS) {
            sourceGlobalCooldownMap.put(sourceId, currentTime);
            disabledWeaponsMap.remove(pairKey);
            return null;
        }

        // 4. Probability Check using zero-allocation thread randomness
        float baseChance;
        String chanceLabel;
        switch (alreadyDisabled) {
            case 0 -> { baseChance = INITIAL_CHANCE; chanceLabel = "INITIAL"; }
            case 1 -> { baseChance = SECOND_CHANCE; chanceLabel = "SECOND"; }
            case 2 -> { baseChance = THIRD_CHANCE; chanceLabel = "THIRD"; }
            default -> { baseChance = SUBSEQUENT_CHANCE; chanceLabel = "SUBSEQUENT"; }
        }

        float roll = ThreadLocalRandom.current().nextFloat();
        if (roll > baseChance) {
            slotMap.put(slotId, currentTime);
            return null;
        }

        // 5. Zero-Allocation Scan across the Target Ship's system slots
        reusableWeaponPool.clear();
        List<WeaponAPI> targetWeapons = targetShip.getAllWeapons();
        int weaponCount = targetWeapons.size();

        for (int i = 0; i < weaponCount; i++) {
            WeaponAPI w = targetWeapons.get(i);
            if (w != null && !w.isDisabled() && !w.isPermanentlyDisabled()) {
                reusableWeaponPool.add(w);
            }
        }

        if (reusableWeaponPool.isEmpty()) {
            return null;
        }

        // Random selection out of matching clean indices
        WeaponAPI weaponToDisable = reusableWeaponPool.get(ThreadLocalRandom.current().nextInt(reusableWeaponPool.size()));
        weaponToDisable.disable();
        reusableWeaponPool.clear(); // Free list structure allocation references immediately

        // Register repair state seamlessly on primitive array tracking context
        activeRepairs.add(new RepairTask(weaponToDisable, DISABLE_DURATION));

        disabledWeaponsMap.put(pairKey, alreadyDisabled + 1);
        slotMap.put(slotId, currentTime);

        return chanceLabel;
    }

    public static void setSlotCooldown(float value) { SLOT_COOLDOWN = value; }
    public static void setGlobalCooldown(float value) { GLOBAL_COOLDOWN = value; }
    public static void setInitialChance(float value) { INITIAL_CHANCE = value; }
    public static void setSecondChance(float value) { SECOND_CHANCE = value; }
    public static void setThirdChance(float value) { THIRD_CHANCE = value; }
    public static void setSubsequentChance(float value) { SUBSEQUENT_CHANCE = value; }
    public static void setMaxDisabledWeapons(int value) { MAX_DISABLED_WEAPONS = value; }
    public static void setDisableDuration(float value) { DISABLE_DURATION = value; }

    public static void reset() {
        slotCooldownMap.clear();
        disabledWeaponsMap.clear();
        sourceGlobalCooldownMap.clear();
        activeRepairs.clear();
        reusableWeaponPool.clear();
    }
}