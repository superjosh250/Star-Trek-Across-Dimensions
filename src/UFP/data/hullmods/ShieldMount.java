package UFP.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import UFP.data.logger.ScriptPerformanceReader;
import UFP.data.util.UFPSettings_Manager;

import java.awt.Color;
import java.util.Locale;
import java.util.Set;

public class ShieldMount extends BaseHullMod {

    private static final String MOD_ID = "ShieldMount";
    private static final String VALIDATION_CACHE_KEY = "ufp_shieldmount_is_valid_cache";
    private static final String SHIELD_DISABLED_FLATTENED_KEY = "ufp_shield_disabled_flattened";

    private static final String GLOBAL_MSG_COUNT_KEY = "ufp_shieldmount_global_msg_count";
    private static final String SHIP_LAST_MSG_PREFIX = "ufp_shieldmount_last_msg_";

    private static final float PER_SHIP_MESSAGE_COOLDOWN = 12f;
    private static final int MAX_GLOBAL_MESSAGES_PER_COMBAT = 30;

    private static void logStart(String func, ShipAPI ship) {
        ScriptPerformanceReader.startTrack("UFP.data.hullmods.ShieldMount." + func);
        if (ship != null) {
            ScriptPerformanceReader.startShipTrack("ShieldMount." + func, ship);
        }
    }

    private static void logEnd(String func, ShipAPI ship) {
        if (ship != null) {
            ScriptPerformanceReader.endShipTrack("ShieldMount." + func, ship);
        }
        ScriptPerformanceReader.endTrack("UFP.data.hullmods.ShieldMount." + func);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;

        // Smart Short-Circuit: If a valid shield installation is verified, instantly step out
        if (hasValidShieldMount(ship)) {
            return;
        }

        logStart("advanceInCombat", ship);

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            logEnd("advanceInCombat", ship);
            return;
        }

        ShieldAPI shield = ship.getShield();
        if (shield != null) {
            Object isFlattened = ship.getCustomData().get(SHIELD_DISABLED_FLATTENED_KEY);

            if (isFlattened == null || !(Boolean) isFlattened) {
                MutableShipStatsAPI stats = ship.getMutableStats();
                if (stats != null) {
                    stats.getShieldArcBonus().modifyMult(MOD_ID, 0f);
                    stats.getShieldUpkeepMult().modifyMult(MOD_ID, 0f);
                }

                if (shield.isOn()) {
                    shield.toggleOff();
                }

                ship.getCustomData().put(SHIELD_DISABLED_FLATTENED_KEY, true);

                if (ship.isAlive()) {
                    ScriptPerformanceReader.countOperation("ShieldMount.WarningsDisplayed");
                    displayShieldOfflineWarning(engine, ship);
                }
            } else {
                if (shield.isOn()) {
                    shield.toggleOff();
                }
            }
        }

        logEnd("advanceInCombat", ship);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String hullId) {
        if (ship == null) return;
        logStart("applyEffectsAfterShipCreation", ship);

        // Pre-cache structural status evaluation once during initial state layout processing
        hasValidShieldMount(ship);

        logEnd("applyEffectsAfterShipCreation", ship);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String hullId) {
        ScriptPerformanceReader.startTrack("UFP.data.hullmods.ShieldMount.applyEffectsBeforeShipCreation");
        super.applyEffectsBeforeShipCreation(hullSize, stats, hullId);
        ScriptPerformanceReader.endTrack("UFP.data.hullmods.ShieldMount.applyEffectsBeforeShipCreation");
    }

    public boolean hasValidShieldMount(ShipAPI ship) {
        if (ship == null) return false;
        if (ship.isFighter() || ship.isDrone()) return true;

        Object cachedResult = ship.getCustomData().get(VALIDATION_CACHE_KEY);
        if (cachedResult instanceof Boolean) {
            return (Boolean) cachedResult;
        }

        logStart("hasValidShieldMount", ship);

        Set<String> validShieldMods = UFPSettings_Manager.getValidShieldMods();
        if (validShieldMods == null || validShieldMods.isEmpty()) {
            ship.getCustomData().put(VALIDATION_CACHE_KEY, true);
            logEnd("hasValidShieldMount", ship);
            return true;
        }

        boolean isValid = false;
        if (ship.getVariant() != null && ship.getVariant().getHullMods() != null) {
            for (String modId : ship.getVariant().getHullMods()) {
                ScriptPerformanceReader.countOperation("ShieldMount.HullmodChecks");
                if (modId != null && validShieldMods.contains(modId.toLowerCase(Locale.ROOT))) {
                    isValid = true;
                    break;
                }
            }
        }

        ship.getCustomData().put(VALIDATION_CACHE_KEY, isValid);
        logEnd("hasValidShieldMount", ship);
        return isValid;
    }

    private void displayShieldOfflineWarning(CombatEngineAPI engine, ShipAPI ship) {
        if (engine == null || ship == null) return;
        logStart("displayShieldOfflineWarning", ship);

        int count = getGlobalMessageCount(engine);
        if (count >= MAX_GLOBAL_MESSAGES_PER_COMBAT) {
            logEnd("displayShieldOfflineWarning", ship);
            return;
        }

        String shipKey = getShipKey(ship);
        String cooldownKey = SHIP_LAST_MSG_PREFIX + shipKey;
        Object lastObj = engine.getCustomData().get(cooldownKey);
        float now = engine.getTotalElapsedTime(false);
        float lastTime = (lastObj instanceof Float) ? (Float) lastObj : -9999f;

        if (now - lastTime < PER_SHIP_MESSAGE_COOLDOWN) {
            logEnd("displayShieldOfflineWarning", ship);
            return;
        }

        engine.getCustomData().put(cooldownKey, now);
        engine.getCustomData().put(GLOBAL_MSG_COUNT_KEY, count + 1);

        Color bad = Misc.getNegativeHighlightColor();
        String text = "Shield Mount: no valid shield installation - shields offline";

        if (engine.getCombatUI() != null) {
            engine.getCombatUI().addMessage(1, ship, bad, text);
        }

        engine.addFloatingText(
                ship.getLocation(),
                "SHIELDS OFFLINE",
                22f,
                bad,
                ship,
                0.8f,
                1.2f
        );

        logEnd("displayShieldOfflineWarning", ship);
    }

    private static int getGlobalMessageCount(CombatEngineAPI engine) {
        if (engine == null) return 0;
        Object obj = engine.getCustomData().get(GLOBAL_MSG_COUNT_KEY);
        return (obj instanceof Integer) ? (Integer) obj : 0;
    }

    private static String getShipKey(ShipAPI ship) {
        if (ship == null) return "null";
        return ship.getId();
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        logStart("isApplicableToShip", ship);
        boolean result = !ship.isFighter() && !ship.isDrone() && !ship.getHullSpec().isCivilianNonCarrier();
        logEnd("isApplicableToShip", ship);
        return result;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Cannot be installed on fighters, drones, or standard civilian structures.";
    }
}