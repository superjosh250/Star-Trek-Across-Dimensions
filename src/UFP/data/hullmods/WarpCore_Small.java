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

public class WarpCore_Small extends BaseHullMod {

    public static final String HULLMOD_ID = "warp_core_small";

    static {
        // Enforce registration as a valid campaign warp drive mechanism upon class indexing
        WarpDriveManager.registerWarpDriveHullMod(HULLMOD_ID);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Soft Flux build-up mitigation: reduces all weapon flux generation profiles by 7.5% via ESM_Weapons
        ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 0.925f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(Global.getCombatEngine(), ship);
        if (esm != null) {
            // Declares this system layout explicitly as a primary operational reactor core
            esm.setHasPowerSystem(true);

            // Dynamically scale Energy System Maximum capacity and Recharge variables based on structural mass categories
            if (ship.getHullSize() == ShipAPI.HullSize.FRIGATE) {
                esm.modifyMaxEnergyPercent(HULLMOD_ID, 0.12f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, 0.075f);
            } else if (ship.getHullSize() == ShipAPI.HullSize.DESTROYER) {
                esm.modifyMaxEnergyPercent(HULLMOD_ID, 0.0f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, 0.05f);
            } else if (ship.getHullSize() == ShipAPI.HullSize.CRUISER) {
                esm.modifyMaxEnergyPercent(HULLMOD_ID, -0.15f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, -0.075f);
            } else {
                // Clear any residual scaling artifacts for unexpected edge states (e.g., Capitals)
                esm.unmodifyMaxEnergy(HULLMOD_ID);
                esm.unmodifyRechargeRate(HULLMOD_ID);
            }
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;

        Color goodColor = new Color(120, 255, 140);
        Color badColor = new Color(255, 120, 120);
        Color systemColor = new Color(100, 200, 255);

        tooltip.addSectionHeading("Compact Warp Core Configuration", Alignment.MID, 10f);
        tooltip.addPara("A lightweight matter-antimatter power generation unit designed to support rapid tactical vector processing and localized sub-light energy grids.", 6f);

        tooltip.addSectionHeading("Combat System Specifications", Alignment.MID, 10f);
        tooltip.addPara("Flux Optimization: Reduces soft flux accumulation during tactical weapon discharge sequences by 7.5%%.", 4f, goodColor, "7.5%");

        tooltip.addSectionHeading("Energy System Architecture Integration", Alignment.MID, 10f);
        tooltip.addPara("Power Grid Status: Registers as a fully functional main reactor matrix node within the local ESM core layout.", 4f, systemColor, "main reactor matrix node");

        // Dynamic Filtering Configuration
        boolean showFrigate   = (ship == null) || (hullSize == ShipAPI.HullSize.FRIGATE);
        boolean showDestroyer = (ship == null) || (hullSize == ShipAPI.HullSize.DESTROYER);
        boolean showCruiser   = (ship == null) || (hullSize == ShipAPI.HullSize.CRUISER);

        if (showFrigate) {
            tooltip.addPara("Frigate class: Enhanced resonance scaling profiles (+12%% ESM Max Energy, +1.25%% ESM Recharge).", 4f, goodColor, "+12%", "+1.25%");
        }
        if (showDestroyer) {
            tooltip.addPara("Destroyer class: Stable operating parameters (+0%% ESM Max Energy, +1.25%% ESM Recharge).", 4f, goodColor, "+0%", "+1.25%");
        }
        if (showCruiser) {
            tooltip.addPara("Cruiser class: System strain induces power transfer deficiencies (-15%% ESM Max Energy, -7.5%% ESM Recharge).", 4f, badColor, "-15%", "-7.5%");
        }

        tooltip.addSectionHeading("Campaign Properties", Alignment.MID, 10f);
        tooltip.addPara("Warp Capability: Designated as an active, registered Warp Drive component framework. Satisfies fleet security checks ensuring access to Jump Point travel vector fields.", 4f, goodColor, "Warp Drive", "Jump Point");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        ShipAPI.HullSize size = ship.getHullSize();
        return size == ShipAPI.HullSize.FRIGATE || size == ShipAPI.HullSize.DESTROYER || size == ShipAPI.HullSize.CRUISER;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship != null) {
            ShipAPI.HullSize size = ship.getHullSize();
            if (size == ShipAPI.HullSize.CAPITAL_SHIP || size == ShipAPI.HullSize.FIGHTER) {
                return "Compact warp core matrices cannot supply or stabilize the mass displacement signatures of Capital ships or Fighter platforms.";
            }
        }
        return "Incompatible hull size configuration.";
    }
}