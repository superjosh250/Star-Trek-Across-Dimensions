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

public class WarpCore_Standard extends BaseHullMod {

    public static final String HULLMOD_ID = "warp_core_standard";

    static {
        // Enforce registration as a valid campaign warp drive mechanism upon class indexing
        WarpDriveManager.registerWarpDriveHullMod(HULLMOD_ID);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Soft Flux build-up mitigation: reduces all weapon flux generation profiles by 15% via ESM_Weapons
        ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 0.85f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(Global.getCombatEngine(), ship);
        if (esm != null) {
            // Declares this system layout explicitly as a primary operational reactor core
            esm.setHasPowerSystem(true);

            // Dynamically scale Energy System Maximum capacity variables based on structural mass categories
            if (ship.getHullSize() == ShipAPI.HullSize.DESTROYER) {
                esm.modifyMaxEnergyPercent(HULLMOD_ID, 0.10f);
            } else if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
                esm.modifyMaxEnergyPercent(HULLMOD_ID, -0.05f);
            } else {
                // Clear any residual scaling artifacts for unexpected edge states (e.g., Cruisers)
                esm.unmodifyMaxEnergy(HULLMOD_ID);
            }
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;

        Color goodColor = new Color(120, 255, 140);
        Color badColor = new Color(255, 120, 120);
        Color systemColor = new Color(100, 200, 255);

        tooltip.addSectionHeading("Standard Warp Core Configuration", Alignment.MID, 10f);
        tooltip.addPara("A balanced matter-antimatter power generation hub that provides standard sub-light power distribution alongside field-stabilized interstellar warp capability.", 6f);

        tooltip.addSectionHeading("Combat System Specifications", Alignment.MID, 10f);
        tooltip.addPara("Flux Optimization: Reduces soft flux accumulation during tactical weapon discharge sequences by 15%%.", 4f, goodColor, "15%");

        tooltip.addSectionHeading("Energy System Architecture Integration", Alignment.MID, 10f);
        tooltip.addPara("Power Grid Status: Registers as a fully functional main reactor matrix node within the local ESM core layout.", 4f, systemColor, "main reactor matrix node");

        // Dynamic Filtering Configuration
        boolean showDestroyer = (ship == null) || (hullSize == ShipAPI.HullSize.DESTROYER);
        boolean showCruiser   = (ship == null) || (hullSize == ShipAPI.HullSize.CRUISER);
        boolean showCapital   = (ship == null) || (hullSize == ShipAPI.HullSize.CAPITAL_SHIP);

        if (showDestroyer) {
            tooltip.addPara("Destroyer class: Excellent dimensional power conversion scaling (+10%% ESM Max Energy).", 4f, goodColor, "+10%");
        }
        if (showCruiser) {
            tooltip.addPara("Cruiser class: Operates at standard designed engineering tolerances.", 4f);
        }
        if (showCapital) {
            tooltip.addPara("Capital class: High displacement mass loads cause induction system efficiency loss (-5%% ESM Max Energy).", 4f, badColor, "-5%");
        }

        tooltip.addSectionHeading("Campaign Properties", Alignment.MID, 10f);
        tooltip.addPara("Warp Capability: Designated as an active, registered Warp Drive component framework. Satisfies fleet security checks ensuring access to Jump Point travel vector fields.", 4f, goodColor, "Warp Drive", "Jump Point");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        ShipAPI.HullSize size = ship.getHullSize();
        return size == ShipAPI.HullSize.DESTROYER || size == ShipAPI.HullSize.CRUISER || size == ShipAPI.HullSize.CAPITAL_SHIP;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship != null) {
            ShipAPI.HullSize size = ship.getHullSize();
            if (size == ShipAPI.HullSize.FRIGATE || size == ShipAPI.HullSize.FIGHTER) {
                return "Standard spatial warp matrix structures cannot be safely retrofitted onto Frigate or Fighter class chassis elements.";
            }
        }
        return "Incompatible hull size configuration.";
    }
}