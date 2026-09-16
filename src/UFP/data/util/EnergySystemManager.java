package UFP.data.util;

import UFP.data.ui.ESM_LibControl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import org.apache.log4j.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public final class EnergySystemManager {

    private static final String ESM_SHIP_DATA_KEY = "UFP_Ship_ESM_Instance";
    private static final String ZERO_FLUX_SOURCE_ID = "UFP_ESM_ZERO_FLUX";
    private static final Logger log = Global.getLogger(EnergySystemManager.class);

    private static int logThrottle = 0;

    private float esmCurrentEnergy;
    private float esmMaxEnergy;
    private float esmRechargeRate;
    private float weaponEnergyCostMultiplier = 1.0f;

    // --- Auxiliary Power Framework Storage ---
    private final float esmBaseAuxiliaryPower;   // Absolute raw value direct from CSV
    private float esmCurrentAuxiliaryPower;      // Modifiable container for other mods/hullmods to utilize

    // --- Passive Zero-Out Utility State ---
    private boolean isZeroedOut = false;         // If true, forces a functional blackout without affecting max limits

    // --- Zero Flux Generation Utility State ---
    private boolean zeroFluxGenerationEnabled = false; // Defaults to false; when true, suppresses flux on player ship via ESM_FluxLib

    // --- Shield Power Requirement Utilities ---
    private boolean shieldEnergyRequirementEnabled = false; // Toggles whether power threshold checks apply to shields
    private float shieldEnergyRequirementThreshold = 0.01f; // Minimum percentage of max energy (0.0f - 1.0f) needed to keep shields active

    // --- Custom Reactor Dynamic Modifiers ---
    private final Map<String, Float> maxEnergyPercentMods = new HashMap<>();
    private final Map<String, Float> rechargeRatePercentMods = new HashMap<>();
    private final Map<String, Float> rechargeRateFlatMods = new HashMap<>();
    private final Map<String, Float> rechargeRateMultMods = new HashMap<>();
    private boolean externalReactorActive = false;

    // --- Modular Power Architecture Framework States ---
    private boolean hasPowerDeliverySystem = false;
    private boolean hasPowerSystem = false;
    private boolean allowMultipleReactors = false;

    // Modifier type enum for recharge rate customization
    public enum RechargeModType {
        PERCENTAGE,     // Percentage modifier applied to base rate (+0.25f = +25%)
        MULTIPLICATIVE, // Multiplicative factor applied to rate (1.5f = 150%)
        FLAT            // Flat numerical value added/subtracted directly (+50.0f = +50 units/sec)
    }

    // Hard structural constructor
    public EnergySystemManager(float maxEnergy, float rechargeRate, float baseAuxPower) {
        this.esmMaxEnergy = Math.max(0f, maxEnergy);
        this.esmRechargeRate = Math.max(0f, rechargeRate);
        this.esmBaseAuxiliaryPower = Math.max(0f, baseAuxPower);
        this.esmCurrentAuxiliaryPower = this.esmBaseAuxiliaryPower;
        this.esmCurrentEnergy = this.esmMaxEnergy;
    }

    // =========================================================================
    // FORCE SINGLE REACTOR LIMIT (EXCLUDING AUX) ---
    // =========================================================================
    public static void enforceSingleReactorLimit(ShipVariantAPI variant) {
        if (variant == null) return;

        Set<String> coreReactors = UFPSettings_Manager.getValidPowerReactors();
        Set<String> auxSystems = UFPSettings_Manager.getValidAuxiliaryPowerSystems();
        if (coreReactors == null || coreReactors.isEmpty()) return;

        String primaryReactorId = null;
        List<String> redundantReactors = new ArrayList<>();

        for (String modId : variant.getHullMods()) {
            if (coreReactors.contains(modId)) {
                if (auxSystems != null && auxSystems.contains(modId)) {
                    continue; // Skip auxiliary systems completely
                }
                if (primaryReactorId == null) {
                    primaryReactorId = modId;
                } else {
                    redundantReactors.add(modId);
                }
            }
        }

        if (!redundantReactors.isEmpty()) {
            for (String duplicateId : redundantReactors) {
                variant.removeMod(duplicateId);
                log.warn("[ESM ENFORCEMENT] Stripped redundant core reactor: " + duplicateId);
            }
            try {
                if (Global.getSoundPlayer() != null) {
                    Global.getSoundPlayer().playUISound("ui_disabled_click", 1.0f, 1.0f);
                }
            } catch (Throwable ignored) {}
        }
    }

    // =========================================================================
    // --- METHOD 2: DEFINE SECOND REACTOR (REJECT REITERATIONS) ---
    // =========================================================================
    public void applySecondaryReactorWithDefinition(String sourceId, float maxEnergyPercentMod,
                                                    float rechargeRatePercentMod, float weaponCostMultiplier) {

        if (maxEnergyPercentMod == 0f && rechargeRatePercentMod == 0f && weaponCostMultiplier == 0f) {
            log.warn(String.format("[ESM API] Secondary reactor from '%s' rejected. Reiteration is redundant.", sourceId));
            return;
        }

        this.allowMultipleReactors = true;

        if (maxEnergyPercentMod != 0f) modifyMaxEnergyPercent(sourceId, maxEnergyPercentMod);
        if (rechargeRatePercentMod != 0f) modifyRechargeRatePercent(sourceId, rechargeRatePercentMod);
        if (weaponCostMultiplier != 0f) this.weaponEnergyCostMultiplier += weaponCostMultiplier;

        log.info(String.format("[ESM API] Secondary reactor registered: %s [Cap: %.2f | Rech: %.2f | Wpn: %.2f]",
                sourceId, maxEnergyPercentMod, rechargeRatePercentMod, weaponCostMultiplier));
    }

    public static EnergySystemManager getOrCreateTracker(CombatEngineAPI engine, ShipAPI ship) {
        if (engine == null || ship == null) return null;

        EnergySystemManager instance = (EnergySystemManager) ship.getCustomData().get(ESM_SHIP_DATA_KEY);
        if (instance != null) {
            return instance;
        }

        String hullId = ship.getHullSpec().getHullId();
        UFP_CSV_Manager.ESMDataEntry csvEntry = UFP_CSV_Manager.getEsmData(hullId);

        if (csvEntry == null && ship.getHullSpec().getBaseHullId() != null) {
            String baseHullId = ship.getHullSpec().getBaseHullId();
            csvEntry = UFP_CSV_Manager.getEsmData(baseHullId);
        }

        float resolvedMax;
        float resolvedRecharge;
        float resolvedAux;

        if (csvEntry != null) {
            resolvedMax = csvEntry.mainPwr;
            resolvedRecharge = csvEntry.pwrRecharge;
            resolvedAux = csvEntry.auxPwr;
        } else {
            resolvedMax = ship.getHullSpec().getFluxCapacity();
            resolvedRecharge = ship.getHullSpec().getFluxDissipation();
            resolvedAux = 0f;
        }

        instance = new EnergySystemManager(resolvedMax, resolvedRecharge, resolvedAux);
        ship.getCustomData().put(ESM_SHIP_DATA_KEY, instance);
        return instance;
    }

    public static EnergySystemManager getPlayerTracker(CombatEngineAPI engine, ShipAPI playerShip) {
        if (playerShip == null) return null;
        return (EnergySystemManager) playerShip.getCustomData().get(ESM_SHIP_DATA_KEY);
    }

    public void advance(float amount, ShipAPI ship) {
        if (ship == null) return;

        // Process zero flux state via ESM_FluxLib (Restricted to player ship only)
        boolean isPlayerShip = (Global.getCombatEngine() != null && ship == Global.getCombatEngine().getPlayerShip());
        boolean activeZeroFlux = isPlayerShip && isZeroFluxGenerationEnabled();
        ESM_FluxLib.processZeroFluxState(ship, ZERO_FLUX_SOURCE_ID, activeZeroFlux);

        float modifiedMax = getMaxEnergy();

        if (isZeroedOut) {
            this.esmCurrentEnergy = 0f;

            logThrottle++;
            if (logThrottle % 60 == 0) {
                log.info(String.format("[ESM STATUS - %s] ZEROED OUT - SYSTEM BLACKOUT | Max Ceiling Preserved: %.1f",
                        ship.getHullSpec().getHullId(), modifiedMax));
            }
            return;
        }

        if (esmCurrentEnergy > modifiedMax) {
            esmCurrentEnergy = modifiedMax;
        }

        logThrottle++;
        if (logThrottle % 60 == 0) {
            log.info(String.format("[ESM STATUS - %s] Current: %.1f / Max: %.1f (%.1f%%) | Aux Data: %.1f",
                    ship.getHullSpec().getHullId(), esmCurrentEnergy, modifiedMax, getEnergyLevel() * 100f, esmCurrentAuxiliaryPower));
        }

        float activeRechargeRatePerSecond = getRechargeRate();

        if (ship.getFluxTracker() != null && ship.getFluxTracker().isVenting()) {
            activeRechargeRatePerSecond *= 2.0f;
        }

        if (esmCurrentEnergy < modifiedMax) {
            esmCurrentEnergy += activeRechargeRatePerSecond * amount;
            if (esmCurrentEnergy > modifiedMax) {
                esmCurrentEnergy = modifiedMax;
            }
        }
    }

    // --- Core Key/Lock Reactor API Hooks ---
    public boolean isExternalReactorActive() { return externalReactorActive; }
    public void setExternalReactorActive(boolean active) { this.externalReactorActive = active; }
    public boolean isMultipleReactorsAllowed() { return this.allowMultipleReactors; }
    public void setAllowMultipleReactors(boolean allow) { this.allowMultipleReactors = allow; }

    public void modifyMaxEnergyPercent(String sourceId, float percentMod) { maxEnergyPercentMods.put(sourceId, percentMod); }
    public void unmodifyMaxEnergy(String sourceId) { maxEnergyPercentMods.remove(sourceId); }

    // =========================================================================
    // --- ZERO FLUX GENERATION API HOOKS ---
    // =========================================================================
    // AFTER:
    public boolean isZeroFluxGenerationEnabled() {
        // Enabled if manually set on this instance OR toggled ON globally in LunaLib
        return this.zeroFluxGenerationEnabled || ESM_LibControl.isZeroFluxGenerationEnabled();
    }

    public void setZeroFluxGenerationEnabled(boolean enabled) {
        this.zeroFluxGenerationEnabled = enabled;
    }

    // =========================================================================
    // --- RECHARGE ENHANCEMENT / DEHANCEMENT API HOOKS ---
    // =========================================================================

    public void modifyRechargeRate(String sourceId, float value, RechargeModType type) {
        if (sourceId == null || type == null) return;
        switch (type) {
            case PERCENTAGE:
                rechargeRatePercentMods.put(sourceId, value);
                break;
            case MULTIPLICATIVE:
                rechargeRateMultMods.put(sourceId, value);
                break;
            case FLAT:
                rechargeRateFlatMods.put(sourceId, value);
                break;
        }
    }

    public void modifyRechargeRatePercent(String sourceId, float percentMod) {
        modifyRechargeRate(sourceId, percentMod, RechargeModType.PERCENTAGE);
    }

    public void modifyRechargeRateMult(String sourceId, float multMod) {
        modifyRechargeRate(sourceId, multMod, RechargeModType.MULTIPLICATIVE);
    }

    public void modifyRechargeRateFlat(String sourceId, float flatMod) {
        modifyRechargeRate(sourceId, flatMod, RechargeModType.FLAT);
    }

    public void unmodifyRechargeRate(String sourceId) {
        if (sourceId == null) return;
        rechargeRatePercentMods.remove(sourceId);
        rechargeRateFlatMods.remove(sourceId);
        rechargeRateMultMods.remove(sourceId);
    }

    public void unmodifyRechargeRate(String sourceId, RechargeModType type) {
        if (sourceId == null || type == null) return;
        switch (type) {
            case PERCENTAGE:
                rechargeRatePercentMods.remove(sourceId);
                break;
            case MULTIPLICATIVE:
                rechargeRateMultMods.remove(sourceId);
                break;
            case FLAT:
                rechargeRateFlatMods.remove(sourceId);
                break;
        }
    }

    // --- Clean Isolation Getters for EPSConduits Syncing ---
    public float getBaseMaxEnergy() { return esmMaxEnergy; }
    public float getBaseRechargeRate() { return esmRechargeRate; }

    public void setBaseMaxEnergy(float maxEnergy) {
        this.esmMaxEnergy = Math.max(0f, maxEnergy);
        float modifiedMax = getMaxEnergy();
        if (this.esmCurrentEnergy > modifiedMax) {
            this.esmCurrentEnergy = modifiedMax;
        }
    }
    public void setBaseRechargeRate(float rechargeRate) { this.esmRechargeRate = Math.max(0f, rechargeRate); }

    // --- Zero Method API Utility Hooks ---
    public boolean isZeroedOut() { return isZeroedOut; }

    public void setZeroedOut(boolean zeroedOut) {
        this.isZeroedOut = zeroedOut;
        if (zeroedOut) {
            this.esmCurrentEnergy = 0f;
        }
    }

    // =========================================================================
    // --- SHIELD POWER REQUIREMENT API HOOKS ---
    // =========================================================================

    public void setShieldEnergyRequirementEnabled(boolean enabled) {
        this.shieldEnergyRequirementEnabled = enabled;
    }

    public boolean isShieldEnergyRequirementEnabled() {
        return this.shieldEnergyRequirementEnabled || ESM_LibControl.isShieldEnergyRequirementEnabled();
    }

    public void setShieldEnergyRequirementThreshold(float threshold) {
        this.shieldEnergyRequirementThreshold = Math.max(0.0f, Math.min(1.0f, threshold));
    }

    public float getShieldEnergyRequirementThreshold() {
        return this.shieldEnergyRequirementThreshold;
    }

    public boolean hasEnoughPowerForShields() {
        // FIX: Call the getter method so it checks LunaSettings!
        if (!isShieldEnergyRequirementEnabled()) return true;
        return getEnergyLevel() >= shieldEnergyRequirementThreshold;
    }

    // --- Clean Auxiliary Getters/Setters API Hooks ---
    public float getBaseAuxiliaryPower() { return esmBaseAuxiliaryPower; }
    public float getCurrentAuxiliaryPower() { return esmCurrentAuxiliaryPower; }
    public void setCurrentAuxiliaryPower(float currentAuxiliaryPower) { this.esmCurrentAuxiliaryPower = Math.max(0f, currentAuxiliaryPower); }

    // --- Core Architecture Processing Hooks (Dynamically Scaled) ---
    public float getWeaponEnergyCostMultiplier() { return weaponEnergyCostMultiplier; }
    public void setWeaponEnergyCostMultiplier(float multiplier) { this.weaponEnergyCostMultiplier = Math.max(0f, multiplier); }

    public float getCurrEnergy() { return esmCurrentEnergy; }

    public float getMaxEnergy() {
        float flatTotal = 1.0f;
        for (float mod : maxEnergyPercentMods.values()) {
            flatTotal += mod;
        }
        return Math.max(0f, esmMaxEnergy * flatTotal);
    }

    public float getMaxEnergyWithScale() { return getMaxEnergy(); }

    public float getRechargeRate() {
        float percentTotal = 1.0f;
        for (float mod : rechargeRatePercentMods.values()) {
            percentTotal += mod;
        }
        float baseWithPercent = esmRechargeRate * Math.max(0f, percentTotal);

        float flatTotal = 0f;
        for (float mod : rechargeRateFlatMods.values()) {
            flatTotal += mod;
        }
        float rateWithFlat = baseWithPercent + flatTotal;

        float multTotal = 1.0f;
        for (float mod : rechargeRateMultMods.values()) {
            multTotal *= mod;
        }

        return Math.max(0f, rateWithFlat * multTotal);
    }

    public float getEnergyLevel() {
        float modifiedMax = getMaxEnergy();
        if (modifiedMax <= 0f) return 0f;
        return Math.min(1.0f, esmCurrentEnergy / modifiedMax);
    }

    public void setCurrEnergy(float currEnergy) {
        if (isZeroedOut) {
            this.esmCurrentEnergy = 0f;
            return;
        }
        this.esmCurrentEnergy = Math.max(0f, Math.min(currEnergy, getMaxEnergy()));
    }

    public void setMaxEnergy(float maxEnergy) { setBaseMaxEnergy(maxEnergy); }
    public void setRechargeRate(float rechargeRate) { setBaseRechargeRate(rechargeRate); }

    public boolean consumeEnergy(float amount) {
        if (isZeroedOut) return false;
        if (esmCurrentEnergy >= amount) {
            esmCurrentEnergy -= amount;
            return true;
        }
        return false;
    }
    public void gainEnergy(float amount) { setCurrEnergy(esmCurrentEnergy + amount); }

    // =========================================================================
    // --- POWER DELIVERY SYSTEM FRAMEWORK ---
    // =========================================================================
    public void setHasPowerDeliverySystem(boolean active) {
        this.hasPowerDeliverySystem = active;
        if (active && !this.hasPowerSystem) {
            setZeroedOut(true);
        }
    }

    public boolean hasPowerDeliverySystem() { return this.hasPowerDeliverySystem; }
    public void setHasPowerSystem(boolean active) { this.hasPowerSystem = active; }
    public boolean hasPowerSystem() { return this.hasPowerSystem; }

    public boolean verifyShipHasDeliveryHullmod(ShipAPI ship) {
        if (ship == null || ship.getVariant() == null) return false;
        for (String modId : UFPSettings_Manager.getValidPowerDeliverySystems()) {
            if (ship.getVariant().hasHullMod(modId)) return true;
        }
        return false;
    }

    public boolean verifyShipHasReactorHullmod(ShipAPI ship) {
        if (ship == null || ship.getVariant() == null) return false;
        for (String modId : UFPSettings_Manager.getValidPowerReactors()) {
            if (ship.getVariant().hasHullMod(modId)) return true;
        }
        return false;
    }

    public boolean verifyShipHasAuxiliaryPowerHullmod(ShipAPI ship) {
        if (ship == null || ship.getVariant() == null) return false;
        for (String modId : UFPSettings_Manager.getValidAuxiliaryPowerSystems()) {
            if (ship.getVariant().hasHullMod(modId)) return true;
        }
        return false;
    }

    public boolean validatePowerArchitecture(ShipAPI ship) {
        if (ship == null) return true;

        if (UFPSettings_Manager.isEpsOverrideEnabled()) {
            this.hasPowerDeliverySystem = verifyShipHasDeliveryHullmod(ship);
            this.hasPowerSystem = verifyShipHasReactorHullmod(ship);
        }

        return (this.hasPowerDeliverySystem == this.hasPowerSystem);
    }
}