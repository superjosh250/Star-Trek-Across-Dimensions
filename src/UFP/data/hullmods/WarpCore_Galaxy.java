package UFP.data.hullmods;

import UFP.data.util.EnergySystemManager;
import UFP.data.util.WarpDriveManager;
import UFP.data.util.ESM_Weapons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.Color;

public class WarpCore_Galaxy extends BaseHullMod {

    public static final String HULLMOD_ID = "warp_core_galaxy";

    static {
        // Enforce registration as a valid campaign warp drive mechanism upon class indexing
        WarpDriveManager.registerWarpDriveHullMod(HULLMOD_ID);
    }

    private static boolean isNebulaHull(String hullId) {
        return "fed_nebula".equals(hullId) ||
                "fed_nebula_NX".equals(hullId) ||
                "fed_nebula_sensor".equals(hullId) ||
                "fed_nebula_warp".equals(hullId);
    }

    private static boolean isValidGalaxyHull(String hullId) {
        return "fed_galaxy".equals(hullId) ||
                "fed_galaxy_stardrive".equals(hullId) ||
                "fed_enterpriseD".equals(hullId) ||
                "fed_enterpriseD_stardrive".equals(hullId) ||
                "terran_galaxy".equals(hullId) ||
                isNebulaHull(hullId);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Fixed: Flat individual ship campaign mobility enhancement (instead of fleetwide)
        stats.getMaxBurnLevel().modifyFlat(id, 2f);

        // Core tactical flux optimization: 25% soft flux accumulation reduction
        ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 0.75f);

        // Unique chassis condition: Stardrive variant receives a 5% bonus to total weapons output efficiency
        if (stats.getVariant() != null && stats.getVariant().getHullSpec() != null) {
            String hullId = stats.getVariant().getHullSpec().getHullId();
            if ("fed_galaxy_stardrive".equals(hullId)) {
                stats.getBallisticWeaponDamageMult().modifyPercent(id, 5f);
                stats.getEnergyWeaponDamageMult().modifyPercent(id, 5f);
                stats.getMissileWeaponDamageMult().modifyPercent(id, 5f);
            }
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(Global.getCombatEngine(), ship);
        if (esm != null) {
            // Declares this system layout explicitly as a primary operational reactor core
            esm.setHasPowerSystem(true);

            String hullId = ship.getHullSpec().getHullId();

            if (isNebulaHull(hullId)) {
                // Nebula configurations: Optimized resource allocation profiles
                esm.modifyMaxEnergyPercent(HULLMOD_ID, 0.12f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, 0.015f);
            } else if ("fed_galaxy_stardrive".equals(hullId)) {
                // Stardrive configuration: Aggressive discharge and cycle matrices
                esm.modifyMaxEnergyPercent(HULLMOD_ID, 0.10f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, 0.025f);
            } else {
                // Standard Galaxy base configuration or safe fallback defaults
                esm.unmodifyMaxEnergy(HULLMOD_ID);
                esm.unmodifyRechargeRate(HULLMOD_ID);
            }
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;

        Color goodColor = new Color(120, 255, 140);
        Color systemColor = new Color(100, 200, 255);

        tooltip.addSectionHeading("Class-7 Warp Core Assembly", Alignment.MID, 10f);
        tooltip.addPara("A high-capacity matter-antimatter reaction matrix tailored exclusively to support the colossal structural mass and system grids of capital-tier exploratory vessels.", 6f);

        tooltip.addSectionHeading("Combat System Specifications", Alignment.MID, 10f);
        tooltip.addPara("Flux Optimization: Reduces soft flux accumulation during tactical weapon discharge sequences by 25%%.", 4f, goodColor, "25%");

        tooltip.addSectionHeading("Energy System Architecture Integration", Alignment.MID, 10f);
        tooltip.addPara("Power Grid Status: The generation of power aligns with the careful placement of EPS Conduits through the ship.", 4f, systemColor, "main reactor matrix node");

        // Determine variant filtering based on active ship inspection status
        String targetHullId = (ship != null && ship.getHullSpec() != null) ? ship.getHullSpec().getHullId() : null;

        boolean showGalaxy    = (targetHullId == null) || "fed_galaxy".equals(targetHullId);
        boolean showStardrive = (targetHullId == null) || "fed_galaxy_stardrive".equals(targetHullId);
        boolean showNebula    = (targetHullId == null) || isNebulaHull(targetHullId);

        if (showGalaxy) {
            tooltip.addSectionHeading("Galaxy Core Specifications", Alignment.MID, 8f);
            tooltip.addPara("• System Tolerances: Operates at optimized baseline core structural load configurations.", 4f);
        }

        if (showStardrive) {
            tooltip.addSectionHeading("Galaxy Stardrive Specifications", Alignment.MID, 8f);
            tooltip.addPara("• Tactical Surge: Core operational throughput expansion (+10%% ESM Max Energy, +2.5%% ESM Recharge).", 4f, goodColor, "+10%", "+2.5%");
            tooltip.addPara("• Weapon Overcharge: Weapon system field harmonizers increase total output damage by 5%%.", 4f, goodColor, "5%");
        }

        if (showNebula) {
            tooltip.addSectionHeading("Nebula Core Specifications", Alignment.MID, 8f);
            tooltip.addPara("• Science Array Routing: Auxiliary power relays adjust layout fields to maximize energy retention (+12%% ESM Max Energy, +1.5%% ESM Recharge).", 4f, goodColor, "+12%", "+1.5%");
        }

        tooltip.addSectionHeading("Campaign Properties", Alignment.MID, 10f);
        tooltip.addPara("• Propulsion Array: Field geometry stabilization expands interstellar cruise capabilities (+2 Max Burn Speed).", 4f, goodColor, "+2");
        tooltip.addPara("• Warp Capability: Designated as an active, registered Warp Drive component framework. Satisfies fleet security checks ensuring access to Jump Point travel vector fields.", 4f, goodColor, "Warp Drive", "Jump Point");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) return false;
        return isValidGalaxyHull(ship.getHullSpec().getHullId());
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "The Class-7 exploratory warp core cannot interface with generic hulls. It requires a dedicated Galaxy or Nebula class spaceframe structure.";
    }
}