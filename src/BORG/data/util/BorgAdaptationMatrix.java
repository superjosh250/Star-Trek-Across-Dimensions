package BORG.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.*;

public class BorgAdaptationMatrix implements DamageTakenModifier {

    // --- EXPOSED CONFIGURATION ---
    public static float GCD_RESISTANCE_SEC = 2.0f;
    public static float GCD_BEAM_TICK_SEC = 3.0f;
    public static float GCD_FULL_ADAPT_ROLL = 25.0f;
    public static float GLOBAL_ADAPT_COOLDOWN = 1.5f;

    // --- ROUTE 3: TYPE RESISTANCE CONFIG ---
    public static final float TYPE_RES_SAME_WEAPON = 0.05f; // +5% on reset
    public static final float TYPE_RES_NEW_WEAPON = 0.075f; // +7.5% on reset if weapon is new
    public static final float TYPE_RES_MAX = 0.50f;         // Cap global type res at 50%

    // --- HULL SIZE CAPACITY KEYS ---
    public static int SLOTS_FRIGATE = 1;
    public static int SLOTS_DESTROYER = 2;
    public static int SLOTS_CRUISER = 3;
    public static int SLOTS_CAPITAL = 4;
    public static int SLOTS_DEFAULT = 1;

    public static final String DATA_KEY = "BORG_ADAPTATION_MAP";
    public static final String TYPE_RES_KEY = "BORG_TYPE_RESISTANCE_MAP";
    public static final String WEAPONS_LEARNED_KEY = "BORG_WEAPONS_CONTRIBUTED";

    private static final String GLOBAL_TICK_KEY = "BORG_LAST_ADAPT_TICK";
    private static final String HUD_ANALYSIS_ID = "BORG_HUD_ANALYSIS";
    private static final String HUD_IMMUNITY_ID = "BORG_HUD_IMMUNITY";
    private static final String HUD_TYPE_ID = "BORG_HUD_TYPE_RES";
    private static final String HUD_TARGET_ADAPT_ID = "BORG_HUD_TARGET_ADAPT";

    private static final String CSV_PATH = "data/config/UFPConfig/borgAdaptation_data.csv";
    private static final Map<String, AdaptationConfig> CONFIG_MAP = new HashMap<>();
    private static final Random RNG = new Random();

    static {
        loadConfig();
    }

    private record AdaptationConfig(float perHit, float beamPerHit, float adaptChanceFull, float learningMult) {}

    private static void loadConfig() {
        try {
            var data = Global.getSettings().getMergedSpreadsheetDataForMod("weapon_id", CSV_PATH, "BORG");
            for (int i = 0; i < data.length(); i++) {
                var row = data.getJSONObject(i);
                String id = row.getString("weapon_id");
                if (id == null || id.isEmpty()) continue;

                float learningMult = (float) row.optDouble("learning_multiplier", 1.0);
                CONFIG_MAP.put(id, new AdaptationConfig(
                        (float) row.getDouble("per_hit_resistance"),
                        (float) row.getDouble("beam_per_hit_resistance"),
                        (float) row.getDouble("adapt_chance_full"),
                        learningMult
                ));
            }
        } catch (Exception e) {
            Global.getLogger(BorgAdaptationMatrix.class).error("Borg Matrix: Error loading CSV", e);
        }
    }

    public static int getMaxLearningSlots(ShipAPI ship) {
        if (ship.getHullSize() == ShipAPI.HullSize.FRIGATE) return SLOTS_FRIGATE;
        if (ship.getHullSize() == ShipAPI.HullSize.DESTROYER) return SLOTS_DESTROYER;
        if (ship.getHullSize() == ShipAPI.HullSize.CRUISER) return SLOTS_CRUISER;
        if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) return SLOTS_CAPITAL;
        return SLOTS_DEFAULT;
    }

    public static void renderHUD(ShipAPI ship, CombatEngineAPI engine) {
        ShipAPI playerShip = engine.getPlayerShip();
        if (playerShip == null) return;

        // 1. Render Target Adaptation Warning (When player is targeting a Borg ship)
        if (ship == playerShip) {
            renderTargetAdaptationHUD(playerShip, engine);
        }

        // 2. Render Self Adaptation Status (When player is piloting a Borg ship)
        if (ship != playerShip) return;

        Map<String, AdaptationState> map = getFullAdaptationMap(ship);
        Map<DamageType, Float> typeMap = (Map<DamageType, Float>) ship.getCustomData().get(TYPE_RES_KEY);

        // --- Defensive HUD Logic ---
        if (typeMap != null && !typeMap.isEmpty()) {
            StringBuilder typeInfo = new StringBuilder();
            for (Map.Entry<DamageType, Float> entry : typeMap.entrySet()) {
                if (entry.getValue() <= 0) continue;
                String typeName = entry.getKey().name().substring(0, 1) + entry.getKey().name().substring(1).toLowerCase();
                typeInfo.append(typeName).append(": ").append(Math.round(entry.getValue() * 100f)).append("%  ");
            }
            if (typeInfo.length() > 0) {
                engine.maintainStatusForPlayerShip(HUD_TYPE_ID, "graphics/icons/hullsys/damper_field.png",
                        "Global Redundancy", typeInfo.toString().trim(), false);
            }
        }

        if (map == null || map.isEmpty()) {
            engine.maintainStatusForPlayerShip(HUD_ANALYSIS_ID, "graphics/icons/hullsys/target_analysis.png",
                    "Borg Matrix", "Analyzing incoming signatures...", false);
            return;
        }

        int immuneCount = 0;
        int activeLearningCount = 0;
        float highestResist = 0f;
        String highestWeaponId = "";

        for (Map.Entry<String, AdaptationState> entry : map.entrySet()) {
            AdaptationState state = entry.getValue();
            if (state.isFullyImmune) {
                immuneCount++;
            } else if (state.currentResistance > 0) {
                activeLearningCount++;
                if (state.currentResistance > highestResist) {
                    highestResist = state.currentResistance;
                    highestWeaponId = entry.getKey();
                }
            }
        }

        if (immuneCount > 0) {
            engine.maintainStatusForPlayerShip(HUD_IMMUNITY_ID, "graphics/icons/hullsys/high_energy_focus.png",
                    "Adaptive Immunity", immuneCount + " signatures neutralized", false);
        }

        if (highestResist > 0) {
            int progress = Math.round(highestResist * 100f);
            String label = "Unknown Signature";
            try { label = Global.getSettings().getWeaponSpec(highestWeaponId).getWeaponName(); } catch (Exception ignored) {}
            String activeText = activeLearningCount > 1 ? progress + "%: " + label + " (+" + (activeLearningCount-1) + ")" : progress + "%: " + label;

            engine.maintainStatusForPlayerShip(HUD_ANALYSIS_ID, "graphics/icons/hullsys/target_analysis.png",
                    "Borg Analysis", activeText, false);
        }
    }

    private static void renderTargetAdaptationHUD(ShipAPI playerShip, CombatEngineAPI engine) {
        ShipAPI target = playerShip.getShipTarget();
        if (target == null || !target.isAlive()) return;

        Map<String, AdaptationState> targetAdaptMap = getFullAdaptationMap(target);
        Map<DamageType, Float> targetTypeMap = (Map<DamageType, Float>) target.getCustomData().get(TYPE_RES_KEY);

        if (targetAdaptMap == null && targetTypeMap == null) return;

        float highestTotalRes = 0f;
        String highestWeaponName = "";
        boolean highestIsImmune = false;

        for (WeaponAPI weapon : playerShip.getAllWeapons()) {
            // Updated check: Starsector identifies system weapons via slot type
            if (weapon.isDecorative() || (weapon.getSlot() != null && weapon.getSlot().isSystemSlot()) || weapon.getSpec() == null) {
                continue;
            }

            String weaponId = weapon.getId();
            float weaponRes = 0f;
            boolean immune = false;

            if (targetAdaptMap != null && targetAdaptMap.containsKey(weaponId)) {
                AdaptationState state = targetAdaptMap.get(weaponId);
                weaponRes = state.currentResistance;
                immune = state.isFullyImmune;
            }

            float typeRes = 0f;
            if (targetTypeMap != null) {
                typeRes = targetTypeMap.getOrDefault(weapon.getDamageType(), 0f);
            }

            // Multiplicative resistance math matching modifyDamageTaken
            float totalMult = (1f - weaponRes) * (1f - typeRes);
            float totalRes = 1f - totalMult;

            if (immune || totalRes > highestTotalRes) {
                highestTotalRes = totalRes;
                highestWeaponName = weapon.getDisplayName();
                if (immune) highestIsImmune = true;
            }
        }

        if (highestTotalRes > 0f) {
            int resPercent = Math.round(highestTotalRes * 100f);
            String text = highestIsImmune
                    ? highestWeaponName + " ineffective (Immune)"
                    : highestWeaponName + " -" + resPercent + "% effectiveness";

            engine.maintainStatusForPlayerShip(
                    HUD_TARGET_ADAPT_ID,
                    "graphics/icons/hullsys/target_analysis.png",
                    "Target Adapted",
                    text,
                    true // Renders red warning frame
            );
        }
    }

    public static void register(ShipAPI ship) {
        if (!ship.hasListenerOfClass(BorgAdaptationMatrix.class)) {
            ship.addListener(new BorgAdaptationMatrix());
        }
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (!(target instanceof ShipAPI ship) || shieldHit) return null;

        WeaponAPI weapon = null;
        if (param instanceof DamagingProjectileAPI proj) weapon = proj.getWeapon();
        else if (param instanceof BeamAPI beam) weapon = beam.getWeapon();

        if (weapon == null) return null;

        String id = weapon.getId();
        AdaptationConfig config = CONFIG_MAP.getOrDefault(id, CONFIG_MAP.get("default"));
        if (config == null) return null;

        AdaptationState state = getAdaptationState(ship, id);
        processAdaptation(ship, state, config, weapon, damage.getType());

        // Multiplicative stacking: (1 - weaponRes) * (1 - typeRes)
        float weaponRes = state.currentResistance;
        float typeRes = getTypeResistance(ship, damage.getType());
        float totalMult = (1f - weaponRes) * (1f - typeRes);

        if (totalMult < 1.0f) {
            damage.getModifier().modifyMult("borg_matrix", totalMult);
            return "borg_matrix";
        }
        return null;
    }

    private void processAdaptation(ShipAPI ship, AdaptationState state, AdaptationConfig config, WeaponAPI weapon, DamageType type) {
        if (state.isFullyImmune) return;

        float currentTime = Global.getCombatEngine().getTotalElapsedTime(false);

        // 1. RNG Roll
        if (currentTime - state.lastRngRollTime >= GCD_FULL_ADAPT_ROLL) {
            state.lastRngRollTime = currentTime;
            if (RNG.nextFloat() < config.adaptChanceFull) {
                applyTypeResistanceBonus(ship, weapon.getId(), type);
                state.isFullyImmune = true;
                state.currentResistance = 1.0f;
                visualFeedback(ship, "ADAPTATION COMPLETE: " + weapon.getDisplayName(), Color.GREEN);
                return;
            }
        }

        // 2. Global Cooldown
        Float lastGlobalTick = (Float) ship.getCustomData().get(GLOBAL_TICK_KEY);
        if (lastGlobalTick != null && currentTime - lastGlobalTick < GLOBAL_ADAPT_COOLDOWN) return;

        // 3. Capacity Check
        if (state.currentResistance <= 0) {
            int currentLearning = 0;
            Map<String, AdaptationState> map = getFullAdaptationMap(ship);
            if (map != null) {
                for (AdaptationState otherState : map.values()) {
                    if (otherState.currentResistance > 0 && !otherState.isFullyImmune) currentLearning++;
                }
            }
            if (currentLearning >= getMaxLearningSlots(ship)) return;
        }

        // 4. Timers
        float timerLimit = weapon.isBeam() ? GCD_BEAM_TICK_SEC : GCD_RESISTANCE_SEC;
        if (currentTime - state.lastStackTime < timerLimit) return;

        // 5. Ammo Check
        boolean readyToLearn = true;
        if (weapon.usesAmmo() && weapon.getAmmo() == state.lastAmmoCount) readyToLearn = false;

        if (readyToLearn) {
            float learningBonus = 1f + (state.failureStage * (config.learningMult - 1f));
            float baseGain = weapon.isBeam() ? config.beamPerHit : config.perHit;

            state.currentResistance += (baseGain * learningBonus);
            if (state.currentResistance > 1.0f) state.currentResistance = 1.0f;

            state.lastStackTime = currentTime;
            ship.setCustomData(GLOBAL_TICK_KEY, currentTime);
            if (weapon.usesAmmo()) state.lastAmmoCount = weapon.getAmmo();

            checkResets(ship, state, weapon, type);
        }
    }

    private void checkResets(ShipAPI ship, AdaptationState state, WeaponAPI weapon, DamageType type) {
        // RESET 1: 50% progress -> Reset to 20% Floor
        if (state.failureStage == 0 && state.currentResistance >= 0.50f) {
            applyTypeResistanceBonus(ship, weapon.getId(), type);
            triggerReset(ship, state, 1, 0.20f, "RECALIBRATING...");
        }
        // RESET 2: 75% progress -> Reset to 45% Floor
        else if (state.failureStage == 1 && state.currentResistance >= 0.75f) {
            applyTypeResistanceBonus(ship, weapon.getId(), type);
            triggerReset(ship, state, 2, 0.45f, "RE-ANALYZING THREAT: " + weapon.getDisplayName());
        }
        // FINAL: 100% progress -> Full Immunity
        else if (state.failureStage == 2 && state.currentResistance >= 1.0f) {
            applyTypeResistanceBonus(ship, weapon.getId(), type);
            state.currentResistance = 1.0f;
            state.isFullyImmune = true;
            state.failureStage = 3;
            visualFeedback(ship, "ADAPTATION COMPLETE: " + weapon.getDisplayName(), Color.GREEN);
        }
    }

    private void applyTypeResistanceBonus(ShipAPI ship, String weaponId, DamageType type) {
        Map<DamageType, Float> typeMap = (Map<DamageType, Float>) ship.getCustomData().get(TYPE_RES_KEY);
        if (typeMap == null) {
            typeMap = new HashMap<>();
            ship.setCustomData(TYPE_RES_KEY, typeMap);
        }

        Set<String> contributedWeapons = (Set<String>) ship.getCustomData().get(WEAPONS_LEARNED_KEY);
        if (contributedWeapons == null) {
            contributedWeapons = new HashSet<>();
            ship.setCustomData(WEAPONS_LEARNED_KEY, contributedWeapons);
        }

        float currentRes = typeMap.getOrDefault(type, 0f);
        boolean isNewWeapon = !contributedWeapons.contains(weaponId);
        float bonus = isNewWeapon ? TYPE_RES_NEW_WEAPON : TYPE_RES_SAME_WEAPON;

        float nextRes = Math.min(TYPE_RES_MAX, currentRes + bonus);
        typeMap.put(type, nextRes);
        contributedWeapons.add(weaponId);

        visualFeedback(ship, type.name() + " ADAPTATION: +" + (int)(bonus * 100) + "%", Color.CYAN);
    }

    private float getTypeResistance(ShipAPI ship, DamageType type) {
        Map<DamageType, Float> typeMap = (Map<DamageType, Float>) ship.getCustomData().get(TYPE_RES_KEY);
        if (typeMap == null) return 0f;
        return typeMap.getOrDefault(type, 0f);
    }

    private void triggerReset(ShipAPI ship, AdaptationState state, int nextStage, float floor, String text) {
        state.currentResistance = floor;
        state.failureStage = nextStage;
        visualFeedback(ship, text, Color.ORANGE);
    }

    private void visualFeedback(ShipAPI ship, String text, Color color) {
        if (Global.getCombatEngine() == null) return;
        Global.getCombatEngine().addFloatingText(ship.getLocation(), text, 20f, color, ship, 1f, 2f);
    }

    public static AdaptationState getAdaptationState(ShipAPI ship, String weaponId) {
        Map<String, AdaptationState> shipData = (Map<String, AdaptationState>) ship.getCustomData().get(DATA_KEY);
        if (shipData == null) {
            shipData = new HashMap<>();
            ship.setCustomData(DATA_KEY, shipData);
        }
        return shipData.computeIfAbsent(weaponId, k -> new AdaptationState());
    }

    public static Map<String, AdaptationState> getFullAdaptationMap(ShipAPI ship) {
        return (Map<String, AdaptationState>) ship.getCustomData().get(DATA_KEY);
    }

    public static class AdaptationState {
        public float currentResistance = 0f;
        public int failureStage = 0;
        public boolean isFullyImmune = false;
        float lastStackTime = -100f;
        float lastRngRollTime = -100f;
        int lastAmmoCount = -1;
    }
}