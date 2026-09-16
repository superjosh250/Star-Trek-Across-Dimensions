package UFP.data.util;

import UFP.data.logger.ScriptPerformanceReader;
import UFP.data.ui.ESM_LibControl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ImpulseDriveManager {

    private static final Color BAD_COLOR = new Color(255, 120, 120);
    private static final Color ALERT_YELLOW = new Color(255, 230, 70);
    private static final Color ALERT_RED = new Color(255, 100, 100);

    public static final class EngineState {
        public boolean hasMount = false;
        public float timeUntilNextCheck = 5f;
        public float activeMalusDuration = 0f;
        public boolean isMalusActive = false;
        public final Map<Integer, Float> disabledEngineTimers = new HashMap<>();
        public final Random rand = new Random();
    }

    public static class DriveProfile {
        public float yellowFluxThreshold;
        public float yellowSpeed;
        public float yellowAccel;
        public float redSpeed;
        public float redAccel;
        public String yellowDesc;
        public String redDesc;

        public DriveProfile(float yellowFluxThreshold, float yellowSpeed, float yellowAccel,
                            float redSpeed, float redAccel, String yellowDesc, String redDesc) {
            this.yellowFluxThreshold = yellowFluxThreshold;
            this.yellowSpeed = yellowSpeed;
            this.yellowAccel = yellowAccel;
            this.redSpeed = redSpeed;
            this.redAccel = redAccel;
            this.yellowDesc = yellowDesc;
            this.redDesc = redDesc;
        }
    }

    public static boolean hasValidMount(ShipAPI ship, List<String> validMounts) {
        if (ship == null || ship.getVariant() == null || validMounts == null) return false;
        for (String mountId : validMounts) {
            if (ship.getVariant().hasHullMod(mountId)) {
                return true;
            }
        }
        return false;
    }

    public static void processDriveUpdate(ShipAPI ship, float amount, String stateKey,
                                          String modYellow, String modRed, String modMalus,
                                          List<String> validMounts, DriveProfile profile) {

        String metricId = "ImpulseDriveManager[" + stateKey + "]";
        ScriptPerformanceReader.startTrack(metricId);
        ScriptPerformanceReader.countOperation(metricId);

        if (ship == null || !ship.isAlive()) {
            ScriptPerformanceReader.endTrack(metricId);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            ScriptPerformanceReader.endTrack(metricId);
            return;
        }

        Map<String, Object> customData = ship.getCustomData();
        EngineState state = (EngineState) customData.get(stateKey);
        if (state == null) {
            state = new EngineState();
            state.hasMount = hasValidMount(ship, validMounts);
            customData.put(stateKey, state);
        }

        processRapidRepair(ship, state, amount);
        if (!state.hasMount) {
            processMalusSystem(ship, state, engine, modMalus, amount);
        }

        MutableShipStatsAPI stats = ship.getMutableStats();
        ShieldAPI shield = ship.getShield();

        boolean shieldActive = shield != null && shield.isOn();

        boolean isYellowAlert = false;
        boolean isRedAlert = false;

        // --- CHECK IF ESM (ZERO FLUX GENERATION) IS ACTIVE ---
        if (ESM_LibControl.isZeroFluxGenerationEnabled()) {
            EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(engine, ship);
            float esmLevel = (esm != null) ? esm.getEnergyLevel() : 0.0f;

            // ESM Logic: Inverted!
            // Thresholds are flipped so that HIGH ESM energy correlates to bonus states.
            // profile.yellowFluxThreshold = 0.05 (5%) in standard flux -> 0.95 (95%) in ESM
            float yellowEsmThreshold = 1.0f - profile.yellowFluxThreshold;

            isYellowAlert = shieldActive && (esmLevel >= yellowEsmThreshold);
            isRedAlert = !isYellowAlert && shieldActive && (esmLevel >= 0.75f);

        } else {
            // Standard Vanilla Flux Logic:
            FluxTrackerAPI flux = ship.getFluxTracker();
            float fluxLevel = (flux != null) ? flux.getFluxLevel() : 1.0f;

            isYellowAlert = shieldActive && (fluxLevel < profile.yellowFluxThreshold);
            isRedAlert = !isYellowAlert && shieldActive && (fluxLevel < 0.25f);
        }

        // --- APPLY STAT MODIFIERS ---
        if (isYellowAlert) {
            stats.getMaxSpeed().unmodify(modRed);
            stats.getAcceleration().unmodify(modRed);

            stats.getMaxSpeed().modifyPercent(modYellow, profile.yellowSpeed);
            stats.getAcceleration().modifyPercent(modYellow, profile.yellowAccel);

            if (ship == engine.getPlayerShip() && profile.yellowDesc != null) {
                engine.maintainStatusForPlayerShip(modYellow, "graphics/icons/statuseffects/high_energy_focus.png",
                        "YELLOW ALERT", profile.yellowDesc, false);
            }
        } else if (isRedAlert) {
            stats.getMaxSpeed().unmodify(modYellow);
            stats.getAcceleration().unmodify(modYellow);

            stats.getMaxSpeed().modifyPercent(modRed, profile.redSpeed);
            stats.getAcceleration().modifyPercent(modRed, profile.redAccel);

            if (ship == engine.getPlayerShip() && profile.redDesc != null) {
                engine.maintainStatusForPlayerShip(modRed, "graphics/icons/statuseffects/high_energy_focus.png",
                        "RED ALERT", profile.redDesc, false);
            }
        } else {
            stats.getMaxSpeed().unmodify(modYellow);
            stats.getAcceleration().unmodify(modYellow);
            stats.getMaxSpeed().unmodify(modRed);
            stats.getAcceleration().unmodify(modRed);
        }

        ScriptPerformanceReader.endTrack(metricId);
    }

    private static void processMalusSystem(ShipAPI ship, EngineState state, CombatEngineAPI engine, String modMalus, float amount) {
        MutableShipStatsAPI stats = ship.getMutableStats();

        if (state.isMalusActive) {
            state.activeMalusDuration -= amount;
            if (state.activeMalusDuration <= 0f) {
                state.isMalusActive = false;
                stats.getAcceleration().unmodify(modMalus);
                stats.getMaxSpeed().unmodify(modMalus);
            }
        }

        state.timeUntilNextCheck -= amount;
        if (state.timeUntilNextCheck <= 0f) {
            state.timeUntilNextCheck = 6f + (state.rand.nextFloat() * 8f);
            int roll = state.rand.nextInt(3);
            float incidentDuration = 5f + (state.rand.nextFloat() * 5f);

            switch (roll) {
                case 0:
                    ship.getEngineController().forceFlameout();
                    engine.addFloatingText(ship.getLocation(), "Unstable Mount Flameout!", 15f, BAD_COLOR, ship, 1f, 1f);
                    break;
                case 1:
                    float damagePercent = 0.05f + (state.rand.nextFloat() * 0.55f);
                    float finalDamage = ship.getMaxHitpoints() * damagePercent;
                    engine.applyDamage(ship, ship.getLocation(), finalDamage, DamageType.FRAGMENTATION, 0f, true, false, ship);
                    engine.addFloatingText(ship.getLocation(), "Structural Engine Backfire!", 18f, BAD_COLOR, ship, 1f, 1f);
                    break;
                case 2:
                    state.isMalusActive = true;
                    state.activeMalusDuration = incidentDuration;
                    if (state.rand.nextBoolean()) {
                        stats.getMaxSpeed().modifyMult(modMalus, 0.40f);
                        engine.addFloatingText(ship.getLocation(), "Impulse Velocity Failure!", 15f, BAD_COLOR, ship, 1f, 1f);
                    } else {
                        stats.getAcceleration().modifyMult(modMalus, 0.20f);
                        engine.addFloatingText(ship.getLocation(), "Maneuvering Compression Failure!", 15f, BAD_COLOR, ship, 1f, 1f);
                    }
                    break;
            }
        }
    }

    private static void processRapidRepair(ShipAPI ship, EngineState state, float amount) {
        ShipEngineControllerAPI controller = ship.getEngineController();
        if (controller == null) return;

        List<ShipEngineControllerAPI.ShipEngineAPI> engines = controller.getShipEngines();
        for (int i = 0, size = engines.size(); i < size; i++) {
            ShipEngineControllerAPI.ShipEngineAPI engineIdx = engines.get(i);
            if (engineIdx == null) continue;

            if (engineIdx.isDisabled() && !engineIdx.isPermanentlyDisabled()) {
                float disabledElapsed = state.disabledEngineTimers.containsKey(i) ? state.disabledEngineTimers.get(i) : 0f;
                disabledElapsed += amount;

                if (disabledElapsed >= 3.0f) {
                    engineIdx.repair();
                    state.disabledEngineTimers.remove(i);
                } else {
                    state.disabledEngineTimers.put(i, disabledElapsed);
                }
            } else {
                state.disabledEngineTimers.remove(i);
            }
        }
    }
}