package UFP.data.util;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;


public final class ESM_FluxLib {

    private ESM_FluxLib() {
        // Utility class constructor
    }

    /**
     * Directly wipes current soft and hard flux via FluxTrackerAPI primitives.
     * Extremely lightweight direct memory writes.
     *
     * @param ship Target ship to clamp flux on
     */
    public static void clampFluxToZero(ShipAPI ship) {
        if (ship == null) return;

        FluxTrackerAPI fluxTracker = ship.getFluxTracker();
        if (fluxTracker != null) {
            fluxTracker.setCurrFlux(0f);
            fluxTracker.setHardFlux(0f);
        }
    }

    /**
     * Combined method that modifies all individual weapon flux cost categories
     * (Ballistic, Energy, Missile, Beam) and ship systems to a 0x multiplier.
     *
     * @param ship     Target ship
     * @param sourceId Unique identifier key for modifier tracking
     */
    public static void suppressAllWeaponAndSystemFlux(ShipAPI ship, String sourceId) {
        if (ship == null || sourceId == null) return;

        MutableShipStatsAPI stats = ship.getMutableStats();
        if (stats == null) return;

        // Weapon Flux Modifications
        stats.getBallisticWeaponFluxCostMod().modifyMult(sourceId, 0f);
        stats.getEnergyWeaponFluxCostMod().modifyMult(sourceId, 0f);
        stats.getMissileWeaponFluxCostMod().modifyMult(sourceId, 0f);
        stats.getBeamWeaponFluxCostMult().modifyMult(sourceId, 0f);

        // System Flux Modification
        stats.getSystemFluxCostBonus().modifyMult(sourceId, 0f);
    }

    /**
     * Combined method that removes flux suppression across all weapon categories
     * and ship systems using the provided source key.
     *
     * @param ship     Target ship
     * @param sourceId Unique identifier key used when applying suppression
     */
    public static void restoreAllWeaponAndSystemFlux(ShipAPI ship, String sourceId) {
        if (ship == null || sourceId == null) return;

        MutableShipStatsAPI stats = ship.getMutableStats();
        if (stats == null) return;

        // Restore Weapon Flux Mods
        stats.getBallisticWeaponFluxCostMod().unmodify(sourceId);
        stats.getEnergyWeaponFluxCostMod().unmodify(sourceId);
        stats.getMissileWeaponFluxCostMod().unmodify(sourceId);
        stats.getBeamWeaponFluxCostMult().unmodify(sourceId);

        // Restore System Flux Mod
        stats.getSystemFluxCostBonus().unmodify(sourceId);
    }

    /**
     * Master controller method to manage zero-flux enforcement on a per-frame basis.
     *
     * @param ship     Target ship
     * @param sourceId Unique key for modifier tracking
     * @param active   True to suppress costs and wipe flux; false to unmodify stats
     */
    public static void processZeroFluxState(ShipAPI ship, String sourceId, boolean active) {
        if (ship == null || sourceId == null) return;

        if (active) {
            suppressAllWeaponAndSystemFlux(ship, sourceId);
            clampFluxToZero(ship);
        } else {
            restoreAllWeaponAndSystemFlux(ship, sourceId);
        }
    }
}