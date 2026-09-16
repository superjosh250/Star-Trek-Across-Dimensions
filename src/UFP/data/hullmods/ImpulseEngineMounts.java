package UFP.data.hullmods;

import UFP.data.logger.ScriptPerformanceReader;
import UFP.data.util.UFPSettings_Manager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ImpulseEngineMounts extends BaseHullMod {

    public static final String HULLMOD_ID = "impulse_engine_mount";
    public static final float RECOVERY_LOSS_CHANCE = 0.10f;

    private static final String STATUS_KEY = "ufp_impulse_engine_mounts_status";
    private static final String UFP_IMPULSE_STATE_KEY = "ufp_impulse_runtime_state";

    private static final Color BAD_COLOR = new Color(255, 120, 120);
    private static final Color GOOD_COLOR = new Color(120, 255, 140);

    // Metric Profiler Constants
    public static final String METRIC_ADVANCE = "ImpulseEngineMounts.advanceInCombat";
    public static final String METRIC_PENALTY = "ImpulseEngineMounts.applyPenalty";
    public static final String METRIC_BONUS = "ImpulseEngineMounts.applyBonus";
    public static final String METRIC_PROTECT = "ImpulseEngineMounts.protectThrusters";

    private static final class PenaltyProfile {
        final float maxSpeedMult;
        final float accelerationMult;
        final float turnRateMult;

        private PenaltyProfile(float maxSpeedMult, float accelerationMult, float turnRateMult) {
            this.maxSpeedMult = maxSpeedMult;
            this.accelerationMult = accelerationMult;
            this.turnRateMult = turnRateMult;
        }
    }

    private static final PenaltyProfile ONE_SLOT_PROFILE = new PenaltyProfile(0.02f, 0.05f, 0.0125f);
    private static final PenaltyProfile TWO_SLOT_PROFILE = new PenaltyProfile(0.05f, 0.10f, 0.025f);
    private static final PenaltyProfile THREE_PLUS_SLOT_PROFILE = new PenaltyProfile(0.01f, 0.10f, 0.05f);

    // Lean runtime state context tracking
    private static final class ImpulseState {
        boolean initialized = false;
        boolean hasEngine = false;
        int slotCount = 0;
        PenaltyProfile profile;
        boolean statsApplied = false; // Flag to prevent destroying/rebuilding stat modifiers every single frame
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        ShipVariantAPI variant = stats.getVariant();
        if (variant == null) return;

        boolean hasEngine = hasValidImpulseEngineInstalled(variant);

        if (!hasEngine) {
            stats.getMaxSpeed().modifyMult(id, 0.05f);
            stats.getAcceleration().modifyMult(id, 0.10f);
            stats.getMaxTurnRate().modifyMult(id, 0.025f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        ScriptPerformanceReader.startTrack(METRIC_ADVANCE);
        ScriptPerformanceReader.countOperation(METRIC_ADVANCE);

        if (ship == null || !ship.isAlive()) {
            ScriptPerformanceReader.endTrack(METRIC_ADVANCE);
            return;
        }

        Object customObj = ship.getCustomData().get(UFP_IMPULSE_STATE_KEY);
        ImpulseState state;

        if (customObj instanceof ImpulseState) {
            state = (ImpulseState) customObj;
        } else {
            state = new ImpulseState();
            ShipVariantAPI variant = ship.getVariant();
            if (variant != null) {
                state.hasEngine = hasValidImpulseEngineInstalled(variant);
                state.slotCount = cacheEngineSlotCount(ship);
                state.profile = getPenaltyProfile(state.slotCount);
                state.initialized = true;
            }
            ship.getCustomData().put(UFP_IMPULSE_STATE_KEY, state);
        }

        if (!state.initialized) {
            ScriptPerformanceReader.endTrack(METRIC_ADVANCE);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        String id = HULLMOD_ID;

        if (!state.hasEngine) {
            // Only update the engine's internal StatMod stack once, instead of every frame
            if (!state.statsApplied) {
                ScriptPerformanceReader.startTrack(METRIC_PENALTY);
                ScriptPerformanceReader.countOperation(METRIC_PENALTY);

                MutableShipStatsAPI stats = ship.getMutableStats();
                stats.getMaxSpeed().unmodify(id);
                stats.getAcceleration().unmodify(id);
                stats.getMaxTurnRate().unmodify(id);

                stats.getMaxSpeed().modifyMult(id, state.profile.maxSpeedMult);
                stats.getAcceleration().modifyMult(id, state.profile.accelerationMult);
                stats.getMaxTurnRate().modifyMult(id, state.profile.turnRateMult);

                state.statsApplied = true;
                ScriptPerformanceReader.endTrack(METRIC_PENALTY);
            }

            if (engine != null && ship == engine.getPlayerShip()) {
                engine.maintainStatusForPlayerShip(STATUS_KEY, null, "Impulse Engine Mounts", "Maneuvering thrusters only", true);
            }

            ScriptPerformanceReader.startTrack(METRIC_PROTECT);
            ScriptPerformanceReader.countOperation(METRIC_PROTECT);
            protectThrusterEnginesOptimized(ship);
            ScriptPerformanceReader.endTrack(METRIC_PROTECT);

        } else {
            if (state.slotCount >= 3) {
                // Apply the bonus modifications once, then safely stay idle on subsequent frames
                if (!state.statsApplied) {
                    ScriptPerformanceReader.startTrack(METRIC_BONUS);
                    ScriptPerformanceReader.countOperation(METRIC_BONUS);

                    MutableShipStatsAPI stats = ship.getMutableStats();
                    stats.getMaxSpeed().unmodify(id);
                    stats.getAcceleration().unmodify(id);
                    stats.getMaxTurnRate().unmodify(id);

                    int extraSlots = state.slotCount - 2;
                    stats.getMaxSpeed().modifyMult(id, 1f + (0.025f * extraSlots));
                    stats.getAcceleration().modifyMult(id, 1f + (0.02f * extraSlots));
                    stats.getMaxTurnRate().modifyMult(id, 1f + (0.04f * extraSlots));

                    state.statsApplied = true;
                    ScriptPerformanceReader.endTrack(METRIC_BONUS);
                }

                if (engine != null && ship == engine.getPlayerShip()) {
                    int extraSlots = state.slotCount - 2;
                    engine.maintainStatusForPlayerShip(STATUS_KEY, null, "Impulse Engine Mounts", "Impulse engine synchronized (" + extraSlots + " extra slot" + (extraSlots == 1 ? "" : "s") + " bonus)", false);
                }
            }
        }

        ScriptPerformanceReader.endTrack(METRIC_ADVANCE);
    }

    private static void protectThrusterEnginesOptimized(ShipAPI ship) {
        ShipEngineControllerAPI controller = ship.getEngineController();
        if (controller == null) return;

        List<ShipEngineControllerAPI.ShipEngineAPI> engines = controller.getShipEngines();
        for (int i = 0, size = engines.size(); i < size; i++) {
            ShipEngineControllerAPI.ShipEngineAPI engine = engines.get(i);
            if (engine == null) continue;

            if (engine.isDisabled() || engine.isPermanentlyDisabled()) {
                engine.repair();
            }

            float maxHp = engine.getMaxHitpoints();
            if (maxHp > 0f && engine.getHitpoints() < maxHp) {
                engine.setHitpoints(maxHp);
            }
        }
    }

    private static int cacheEngineSlotCount(ShipAPI ship) {
        ShipEngineControllerAPI controller = ship.getEngineController();
        if (controller == null) return 0;

        List<ShipEngineControllerAPI.ShipEngineAPI> engines = controller.getShipEngines();
        int count = 0;
        for (int i = 0, size = engines.size(); i < size; i++) {
            ShipEngineControllerAPI.ShipEngineAPI engine = engines.get(i);
            if (engine != null && engine.getEngineSlot() != null) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;

        tooltip.addSectionHeading("Impulse Engine Requirement", Alignment.MID, 10f);
        tooltip.addPara("This ship requires a valid %s hullmod to avoid severe propulsion penalties.", 10f, BAD_COLOR, "impulse engine");
        tooltip.addPara("Valid engine hullmods are read dynamically from %s, so additional engine sizes can be added later without changing this class.", 3f, Global.getSettings().getColor("textFriendColor"), "UFPSettings.json");
        tooltip.addPara("Without a valid impulse engine, the ship fights using only maneuvering thrusters.", 3f, BAD_COLOR, "maneuvering thrusters");
        tooltip.addPara("If the ship is destroyed and later recovered, there is a %s chance the installed impulse engine hullmod is destroyed and removed.", 3f, BAD_COLOR, "10%");
        tooltip.addPara("Engine-slot balance: 1 slot worsens the no-engine penalties; 3+ slots reduce some penalties and grant scaling bonuses when a valid impulse engine is installed.", 3f);

        if (ship != null) {
            boolean valid = hasValidImpulseEngineInstalled(ship.getVariant());
            int slots = cacheEngineSlotCount(ship);

            if (valid) {
                tooltip.addPara("Current ship status: %s (%s engine slot%s detected).", 10f, GOOD_COLOR, "VALID IMPULSE ENGINE INSTALLED", String.valueOf(slots), slots == 1 ? "" : "s");
            } else {
                tooltip.addPara("Current ship status: %s (%s engine slot%s detected).", 10f, BAD_COLOR, "NO VALID IMPULSE ENGINE INSTALLED", String.valueOf(slots), slots == 1 ? "" : "s");
            }
        }
    }

    @Override
    public boolean showInRefitScreenModPickerFor(ShipAPI ship) { return false; }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        switch (index) {
            case 0: return "valid impulse engine";
            case 1: return "maneuvering thrusters only";
            case 2: return "10%";
            default: return null;
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) { return false; }

    @Override
    public String getUnapplicableReason(ShipAPI ship) { return "Built-in hullmod only"; }

    @Override
    public boolean affectsOPCosts() { return false; }

    public static boolean maybeDestroyImpulseEngineOnRecovery(FleetMemberAPI member, Random random) {
        if (member == null || member.getVariant() == null) return false;
        return maybeDestroyImpulseEngineOnRecovery(member.getVariant(), random);
    }

    public static boolean maybeDestroyImpulseEngineOnRecovery(ShipVariantAPI variant, Random random) {
        if (variant == null) return false;
        if (random == null) random = new Random();

        if (random.nextFloat() >= RECOVERY_LOSS_CHANCE) return false;

        Set<String> validEngines = UFPSettings_Manager.getValidImpulseEngines();
        for (String validId : validEngines) {
            if (variant.hasHullMod(validId)) {
                variant.removeMod(validId);
                variant.getSMods().remove(validId);
                variant.getPermaMods().remove(validId);
                variant.getSuppressedMods().remove(validId);
                return true;
            }
        }
        return false;
    }

    private static PenaltyProfile getPenaltyProfile(int slotCount) {
        if (slotCount <= 1) return ONE_SLOT_PROFILE;
        if (slotCount >= 3) return THREE_PLUS_SLOT_PROFILE;
        return TWO_SLOT_PROFILE;
    }

    private static boolean hasValidImpulseEngineInstalled(ShipVariantAPI variant) {
        if (variant == null) return false;
        Set<String> validEngines = UFPSettings_Manager.getValidImpulseEngines();
        for (String validId : validEngines) {
            if (variant.hasHullMod(validId)) return true;
        }
        return false;
    }
}