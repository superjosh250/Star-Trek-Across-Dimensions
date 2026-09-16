package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI; // Added import
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import UFP.data.logger.ScriptPerformanceReader;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class ESM_Weapons {

    private static final String WEAPON_AMMO_TRACKER_KEY = "UFP_ESM_Weapon_Ammo_Tracker";

    public static void advanceWeaponEnergyLogic(float amount, ShipAPI ship) {
        ScriptPerformanceReader.startTrack("ESM_Weapons.advanceWeaponEnergyLogic");
        try {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || ship != engine.getPlayerShip() || !ship.isAlive() || engine.isPaused()) {
                return;
            }

            EnergySystemManager esm = EnergySystemManager.getPlayerTracker(engine, ship);
            if (esm == null) return;

            // Retrieve or initialize tracking for projectile weapon ammo states
            Map<WeaponAPI, Integer> ammoTracker = (Map<WeaponAPI, Integer>) ship.getCustomData().get(WEAPON_AMMO_TRACKER_KEY);
            if (ammoTracker == null) {
                ammoTracker = new HashMap<>();
                ship.getCustomData().put(WEAPON_AMMO_TRACKER_KEY, ammoTracker);
            }

            float weaponCostMultiplier = esm.getWeaponEnergyCostMultiplier();
            float currentEnergy = esm.getCurrEnergy();

            List<WeaponAPI> weapons = ship.getAllWeapons();
            int size = weapons.size();

            for (int i = 0; i < size; i++) {
                WeaponAPI weapon = weapons.get(i);

                if (weapon.getSlot() == null || weapon.getSlot().isSystemSlot() || weapon.getSlot().isDecorative() || weapon.isDisabled()) {
                    continue;
                }

                WeaponSpecAPI spec = weapon.getSpec();
                if (spec == null) continue;

                float singleShotCost = 0f;
                float continuousCostPerSecond = 0f;
                boolean isBeam = spec instanceof BeamWeaponSpecAPI;

                if (isBeam) {
                    BeamWeaponSpecAPI beamSpec = (BeamWeaponSpecAPI) spec;
                    continuousCostPerSecond = beamSpec.getEnergyPerSecond() <= 0f ? beamSpec.getFluxPerSecond() : beamSpec.getEnergyPerSecond();
                    singleShotCost = continuousCostPerSecond * amount;
                } else if (spec instanceof ProjectileWeaponSpecAPI) {
                    ProjectileWeaponSpecAPI projSpec = (ProjectileWeaponSpecAPI) spec;
                    singleShotCost = projSpec.getEnergyPerShot();
                    continuousCostPerSecond = projSpec.getEnergyPerSecond();
                } else {
                    singleShotCost = spec.getDerivedStats().getFluxPerSecond() * amount;
                }

                float finalCostToApply = singleShotCost * weaponCostMultiplier;

                // --- 1. PRE-FIRE COST VERIFICATION ---
                if (weapon.isFiring()) {
                    if (currentEnergy < finalCostToApply) {
                        // Hard stifle the trigger frame completely if there's an energy deficit
                        weapon.setForceNoFireOneFrame(true);
                        if (isBeam) {
                            weapon.stopFiring();
                        }
                        continue;
                    }
                }

                // --- 2. POST-FIRE ENERGY CONSUMPTION LOGIC ---
                if (isBeam) {
                    if (weapon.isFiring() && weapon.getBeams() != null && !weapon.getBeams().isEmpty()) {
                        float beamDrain = (continuousCostPerSecond * amount) * weaponCostMultiplier;
                        esm.consumeEnergy(beamDrain);
                        currentEnergy -= beamDrain;
                    }
                } else {
                    // Pull historical weapon register entry
                    Integer lastAmmo = ammoTracker.get(weapon);
                    int currentAmmo = weapon.getAmmo();

                    if (lastAmmo == null) {
                        ammoTracker.put(weapon, currentAmmo);
                        continue;
                    }

                    // A projectile shot fired if ammo dropped or the weapon cycled a charge frame
                    boolean shotFired = currentAmmo < lastAmmo;

                    // Fallback verification rule for non-ammo weapons using real time status registers
                    if (!weapon.usesAmmo() && weapon.isFiring() && weapon.getCooldownRemaining() <= 0f && weapon.getChargeLevel() >= 1.0f) {
                        shotFired = true;
                    }

                    if (shotFired) {
                        float projectileDrain = finalCostToApply;
                        if (continuousCostPerSecond > 0f) {
                            projectileDrain += (continuousCostPerSecond * amount) * weaponCostMultiplier;
                        }

                        esm.consumeEnergy(projectileDrain);
                        currentEnergy -= projectileDrain;
                    }

                    // Keep historical map entry accurately updated
                    ammoTracker.put(weapon, currentAmmo);
                }
            }
        } finally {
            ScriptPerformanceReader.endTrack("ESM_Weapons.advanceWeaponEnergyLogic");
        }
    }

    // =========================================================================
    // --- FLUX COST SPECIFICATION MODIFIERS ---
    // =========================================================================

    /**
     * Applies a universal multiplier modifier to the flux costs of all weapon classifications
     * (Ballistic, Energy, Missile, and Beam).
     *
     * @param stats The target ship's mutable statistics pool.
     * @param id    The unique string modifier key tracking the modification source.
     * @param mult  The multiplier value to apply (e.g., 0.85f for a 15% cost reduction).
     */
    public static void modifyWeaponFluxCostMult(MutableShipStatsAPI stats, String id, float mult) {
        if (stats == null || id == null) return;

        stats.getBallisticWeaponFluxCostMod().modifyMult(id, mult);
        stats.getEnergyWeaponFluxCostMod().modifyMult(id, mult);
        stats.getMissileWeaponFluxCostMod().modifyMult(id, mult);
        stats.getBeamWeaponFluxCostMult().modifyMult(id, mult);
    }

    /**
     * Completely unloads and strips out a universal weapon flux cost modifier across all profiles.
     *
     * @param stats The target ship's mutable statistics pool.
     * @param id    The unique string modifier key tracking the modification source.
     */
    public static void unmodifyWeaponFluxCost(MutableShipStatsAPI stats, String id) {
        if (stats == null || id == null) return;

        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getEnergyWeaponFluxCostMod().unmodify(id);
        stats.getMissileWeaponFluxCostMod().unmodify(id);
        stats.getBeamWeaponFluxCostMult().unmodify(id);
    }
}