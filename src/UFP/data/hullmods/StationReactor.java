package UFP.data.hullmods;

import UFP.data.ai.StationReactorAI;
import UFP.data.util.EnergySystemManager;
import UFP.data.util.ESM_Weapons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;

public class StationReactor extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Reduces all weapon flux generation profiles by 10%
        ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 0.90f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(Global.getCombatEngine(), ship);
        if (esm != null) {
            // Registers as a primary operational power matrix
            esm.setHasPowerSystem(true);
        }
        // Process station reactor fire control AI logic
        StationReactorAI.advance(ship, amount);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;

        Color goodColor = new Color(120, 255, 140);
        Color systemColor = new Color(100, 200, 255);

        tooltip.addSectionHeading("Station Reactor Core Matrix", Alignment.MID, 10f);
        tooltip.addPara("A heavy-duty industrial reactor architecture engineered specifically for immovable planetary or orbital installation structures.", 6f);

        tooltip.addSectionHeading("Combat System Specifications", Alignment.MID, 10f);
        tooltip.addPara("Flux Optimization: Reduces soft flux accumulation during tactical weapon discharge sequences by 10%%.", 4f, goodColor, "10%");

        tooltip.addSectionHeading("Energy System Architecture Integration", Alignment.MID, 10f);
        tooltip.addPara("Power Grid Status: Registers as a fully functional main reactor matrix node within the local ESM core layout.", 4f, systemColor, "main reactor matrix node");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;

        // Cannot be installed on standard ships or anything lacking the STATION hint
        ShipHullSpecAPI spec = ship.getHullSpec();
        if (spec == null || spec.getHints() == null) return false;

        return spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Station Reactors can only be installed on station structures.";
    }
}