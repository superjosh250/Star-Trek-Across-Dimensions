package UFP.data.hullmods;

import UFP.data.ui.ESM_StatusBar;
import UFP.data.util.EnergySystemManager;
import UFP.data.util.ESM_Weapons;
import UFP.data.util.UFPSettings_Manager;
import UFP.data.util.UFP_CSV_Manager;
import UFP.data.listeners.ESM_WeaponPreFireGater;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.Set;
import org.apache.log4j.Logger;

public class EPSConduits extends BaseHullMod {

    private static final String MOD_ID = "UFP_EPSConduits";
    private static final String EJECTED_SET_KEY = "$UFP_ejectReactor_used_members";
    private static final String STATUS_BAR_KEY = "UFP_ESM_STATUS_BAR";
    private static final String GATER_LISTENER_KEY = "UFP_ESM_GATER_ADDED";
    private static final String INIT_CHECK_KEY = "UFP_EPS_INIT_DONE";
    private static final Logger log = Global.getLogger(EPSConduits.class);

    private float logTimer = 0f;
    private static final float LOG_INTERVAL = 1.0f;
    private String lastLoggedState = "NONE";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null) return;

        log.info("[UFP_DIAGNOSTIC] applyEffectsAfterShipCreation triggered for: " + ship.getHullSpec().getHullId());

        // Enforce the single reactor architectural constraint immediately
        if (ship.getVariant() != null) {
            EnergySystemManager.enforceSingleReactorLimit(ship.getVariant());
        }

        applyCampaignPenalty(ship.getMutableStats());
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null || !ship.isAlive() || ship != engine.getPlayerShip()) {
            return;
        }

        if (engine.getTotalElapsedTime(false) < 0.1f && !ship.getCustomData().containsKey(INIT_CHECK_KEY)) {
            clearZeroOutModifiers(ship.getMutableStats());
            return;
        }
        ship.getCustomData().put(INIT_CHECK_KEY, true);

        if (!ship.getCustomData().containsKey(GATER_LISTENER_KEY)) {
            ship.addListener(new ESM_WeaponPreFireGater(ship, engine));
            ship.getCustomData().put(GATER_LISTENER_KEY, true);
        }

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(engine, ship);
        if (esm == null) return;

        // WORKLOAD REDUCTION: Offloaded structural verification directly to ESM framework hooks
        boolean hasPower = esm.verifyShipHasReactorHullmod(ship);

        boolean ejectedInCombat = ship.getCustomData().containsKey("UFP_ejectReactor_used") &&
                Boolean.TRUE.equals(ship.getCustomData().get("UFP_ejectReactor_used"));

        boolean deployedWithFailure = ship.getVariant() != null &&
                (ship.getVariant().hasHullMod("main_power_failure") ||
                        ship.getVariant().getHullMods().contains("main_power_failure"));

        boolean reactorIsActive = (hasPower && !ejectedInCombat && !deployedWithFailure) || esm.isExternalReactorActive();

        // WORKLOAD REDUCTION: Register framework architecture states directly into ESM instance
        esm.setHasPowerDeliverySystem(true); // EPSConduits establishes the delivery infrastructure
        esm.setHasPowerSystem(reactorIsActive);

        String hullId = ship.getHullSpec().getHullId();
        UFP_CSV_Manager.ESMDataEntry csvEntry = UFP_CSV_Manager.getEsmData(hullId);
        if (csvEntry == null && ship.getHullSpec().getBaseHullId() != null) {
            csvEntry = UFP_CSV_Manager.getEsmData(ship.getHullSpec().getBaseHullId());
        }

        float targetMax = (csvEntry != null) ? csvEntry.mainPwr : ship.getHullSpec().getFluxCapacity();
        float targetRecharge = (csvEntry != null) ? csvEntry.pwrRecharge :
                ship.getMutableStats().getFluxDissipation().getBaseValue();

        if (esm.getBaseMaxEnergy() != targetMax) {
            esm.setBaseMaxEnergy(targetMax);
        }
        if (esm.getBaseRechargeRate() != targetRecharge) {
            esm.setBaseRechargeRate(targetRecharge);
        }

        boolean hasAuxPower = (csvEntry != null && csvEntry.auxPwr > 0f) ||
                esm.verifyShipHasAuxiliaryPowerHullmod(ship) ||
                deployedWithFailure || ejectedInCombat;

        String currentState = reactorIsActive ? "REACTOR_ACTIVE" :
                (hasAuxPower ? "AUX_POWER_MODE" : "BLACKOUT_ZERO_OUT");
        boolean shouldLog = false;

        if (!engine.isPaused()) {
            logTimer += amount;
            if (logTimer >= LOG_INTERVAL) {
                logTimer = 0f;
                shouldLog = true;
            }
        }
        if (!currentState.equals(lastLoggedState)) {
            log.info("[UFP_DIAGNOSTIC] STATE TRANSITION DETECTED: " + lastLoggedState + " -> " + currentState);
            lastLoggedState = currentState;
            shouldLog = true;
        }

        if (shouldLog) {
            runDiagnosticDump(ship, hasPower, ejectedInCombat, deployedWithFailure,
                    reactorIsActive, csvEntry, hasAuxPower, currentState, esm);
        }

        // --- STABILIZED POWER STATE MACHINE ---
        if (reactorIsActive) {
            esm.setZeroedOut(false);
            esm.setWeaponEnergyCostMultiplier(1.0f);
            clearZeroOutModifiers(ship.getMutableStats());

            // Enforce ESM Shield Power Evaluation (Passive query, does not touch state settings)
            if (!esm.hasEnoughPowerForShields()) {
                if (ship.getShield() != null && ship.getShield().isOn()) {
                    ship.getShield().toggleOff();
                }
                engine.maintainStatusForPlayerShip("UFP_SHIELD_LOW_PWR", null,
                        "SHIELD LOCKOUT", "INSUFFICIENT ESM POWER", true);
            }

        } else if (hasAuxPower) {
            esm.setZeroedOut(false);
            esm.setWeaponEnergyCostMultiplier(1.12f);
            clearZeroOutModifiers(ship.getMutableStats());

            // Enforce ESM Shield Power Evaluation (Passive query, does not touch state settings)
            if (!esm.hasEnoughPowerForShields()) {
                if (ship.getShield() != null && ship.getShield().isOn()) {
                    ship.getShield().toggleOff();
                }
                engine.maintainStatusForPlayerShip("UFP_SHIELD_LOW_PWR", null,
                        "SHIELD LOCKOUT", "INSUFFICIENT ESM POWER", true);
            }

        } else {
            // WORKLOAD REDUCTION: Handled via the structural zero-out method hook
            esm.setZeroedOut(true);

            ship.getEngineController().forceFlameout(true);
            ship.getMutableStats().getMaxSpeed().modifyMult(MOD_ID, 0f);
            ship.getMutableStats().getAcceleration().modifyMult(MOD_ID, 0f);
            ship.getMutableStats().getDeceleration().modifyMult(MOD_ID, 0f);
            ship.getMutableStats().getTurnAcceleration().modifyMult(MOD_ID, 0f);
            ship.getMutableStats().getMaxTurnRate().modifyMult(MOD_ID, 0f);

            if (ship.getShield() != null && ship.getShield().isOn()) {
                ship.getShield().toggleOff();
            }
            ship.getMutableStats().getShieldUpkeepMult().modifyMult(MOD_ID, 999f);

            for (WeaponAPI weapon : ship.getAllWeapons()) {
                if (weapon.isDecorative()) continue;
                weapon.setForceNoFireOneFrame(true);
            }

            engine.maintainStatusForPlayerShip("UFP_BLACKOUT_WARN", null,
                    "GRID FAILURE", "ALL POWER SYSTEMS DEAD", true);
        }

        // Advance energy logic and zero-flux states.
        if (!engine.isPaused()) {
            esm.advance(amount, ship);
        }

        ESM_Weapons.advanceWeaponEnergyLogic(amount, ship);

        if (!engine.getCustomData().containsKey(STATUS_BAR_KEY)) {
            ESM_StatusBar renderer = new ESM_StatusBar();
            engine.addLayeredRenderingPlugin(renderer);
            engine.getCustomData().put(STATUS_BAR_KEY, renderer);
        }
    }

    private void runDiagnosticDump(ShipAPI ship, boolean hasPower, boolean ejectedInCombat,
                                   boolean deployedWithFailure, boolean reactorIsActive, UFP_CSV_Manager.ESMDataEntry csvEntry,
                                   boolean hasAuxPower, String currentState, EnergySystemManager esm) {
        try {
            log.info("-------------------- UFP ENERGY SYSTEM DIAGNOSTIC DUMP --------------------");
            log.info("Hull ID: " + ship.getHullSpec().getHullId() + " | Base Hull ID: " + ship.getHullSpec().getBaseHullId());
            log.info("Evaluated State Machine Choice: " + currentState);
            log.info(String.format("Flags -> hasPower: %b | ejected: %b | failure: %b | active: %b | aux: %b",
                    hasPower, ejectedInCombat, deployedWithFailure, reactorIsActive, hasAuxPower));

            if (esm != null) {
                log.info(String.format("Tracker -> zeroed: %b | energy: %.1f | max: %.1f | zeroFluxActive: %b",
                        esm.isZeroedOut(), esm.getCurrEnergy(), esm.getMaxEnergy(), esm.isZeroFluxGenerationEnabled()));
                log.info(String.format("Architecture -> grid: %b | power: %b",
                        esm.hasPowerDeliverySystem(), esm.hasPowerSystem()));
            }
            log.info("---------------------------------------------------------------------------");
        } catch (Exception e) {
            log.error("Failure processing diagnostic log output data block", e);
        }
    }

    private void clearZeroOutModifiers(MutableShipStatsAPI stats) {
        if (stats == null) return;
        stats.getMaxSpeed().unmodify(MOD_ID);
        stats.getAcceleration().unmodify(MOD_ID);
        stats.getDeceleration().unmodify(MOD_ID);
        stats.getTurnAcceleration().unmodify(MOD_ID);
        stats.getMaxTurnRate().unmodify(MOD_ID);
        stats.getShieldUpkeepMult().unmodify(MOD_ID);
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        if (stats != null) {
            stats.getMaxBurnLevel().unmodify(MOD_ID);
            clearZeroOutModifiers(stats);
        }
    }

    @Override
    public boolean isSModEffectAPenalty() { return false; }

    private void applyCampaignPenalty(MutableShipStatsAPI stats) {
        UFPSettings_Manager.loadSettings();
        FleetMemberAPI fm = stats.getFleetMember();
        if (fm == null) return;

        boolean ejected = isCoreEjected(fm);
        boolean hasPower = hasValidPowerSystem(fm);

        if (!hasPower || ejected) {
            stats.getMaxBurnLevel().modifyMult(MOD_ID, 0f);
        } else {
            stats.getMaxBurnLevel().unmodify(MOD_ID);
        }
    }

    private boolean isCoreEjected(FleetMemberAPI fm) {
        if (fm.getVariant() != null && (fm.getVariant().hasHullMod("main_power_failure") ||
                fm.getVariant().getHullMods().contains("main_power_failure"))) {
            return true;
        }
        var sector = Global.getSector();
        if (sector == null) return false;

        Object o = sector.getMemoryWithoutUpdate().get(EJECTED_SET_KEY);
        if (!(o instanceof Set)) return false;

        @SuppressWarnings("unchecked")
        Set<String> ids = (Set<String>) o;
        return ids.contains(fm.getId());
    }

    private boolean hasValidPowerSystem(FleetMemberAPI fm) {
        var v = fm.getVariant();
        if (v == null) return false;

        Set<String> suppressed = null;
        try {
            suppressed = v.getSuppressedMods();
        } catch (Throwable ignored) {}

        Set<String> validReactors = UFPSettings_Manager.getValidPowerReactors();
        if (validReactors == null || validReactors.isEmpty()) return false;

        for (String id : validReactors) {
            if (suppressed != null && suppressed.contains(id)) continue;
            if (v.hasHullMod(id) || v.getHullMods().contains(id) ||
                    v.getPermaMods().contains(id) || v.getSMods().contains(id)) return true;

            if (fm.getHullSpec() != null && fm.getHullSpec().isBuiltIn(id)) {
                if (suppressed == null || !suppressed.contains(id)) return true;
            }
        }
        return false;
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "0";
        if (index == 1) return String.join(", ", UFPSettings_Manager.getValidPowerReactors());
        return null;
    }
}