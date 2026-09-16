package BORG.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import BORG.data.campaign.ids.BorgIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.Spaceport;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class AssimilatedSpaceport extends Spaceport {

    @Override
    public void apply() {
        super.apply();

        if (market == null) return;
        int size = market.getSize();
        String coreId = getAICoreId();

        // 1. DEMAND CALCULATIONS
        int baseDemand = getBaseDemandForSize(size);
        int dilithiumDemand = getDilithiumDemandForSize(size);

        int fuelDemand = baseDemand;
        int suppliesDemand = baseDemand;

        // AI Core Demand Reduction (-1 for Beta, Gamma, Alpha, Omega)
        if (Commodities.BETA_CORE.equals(coreId) ||
                Commodities.GAMMA_CORE.equals(coreId) ||
                Commodities.ALPHA_CORE.equals(coreId) ||
                Commodities.OMEGA_CORE.equals(coreId)) {
            fuelDemand = Math.max(0, fuelDemand - 1);
            suppliesDemand = Math.max(0, suppliesDemand - 1);
        }

        // Improvement Level 4 Demand Reduction (-3 excluding Dilithium)
        if (getImproveProductionBonus() >= 4) {
            fuelDemand = Math.max(0, fuelDemand - 3);
            suppliesDemand = Math.max(0, suppliesDemand - 3);
        }

        demand(Commodities.FUEL, fuelDemand);
        demand(Commodities.SUPPLIES, suppliesDemand);
        demand(DimensionsCrossedIDS.DILITHIUM, dilithiumDemand);

        // 2. SUPPLY CALCULATIONS
        int baseSupply = getBaseSupplyForSize(size);

        // AI Core Supply Modifications
        if (Commodities.ALPHA_CORE.equals(coreId) || Commodities.OMEGA_CORE.equals(coreId)) {
            baseSupply += 1;
        }

        supply(Commodities.CREW, baseSupply);
        supply(BorgIDS.DRONES, baseSupply);

        // 3. BASE MODIFIERS & ACCESSIBILITY
        market.getAccessibilityMod().modifyFlat(getModId(0), 0.75f, "Assimilated Spaceport Logistics");

        // 4. FLEET SIZE SCALING (COMBAT_FLEET_SIZE_MULT)
        float sizeFleetMult = getFleetSizeMultForSize(size);

        // Use modifyMult() so Starsector renders "x2.75" (or "x1.75") instead of "+175%"
        if (sizeFleetMult > 1.0f) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId(1), sizeFleetMult, "Assimilated Spaceport Operations");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(1));
        }

        // Distinct entry for AI Core fleet size multiplier (shows up as its own line)
        if (Commodities.GAMMA_CORE.equals(coreId) || Commodities.ALPHA_CORE.equals(coreId)) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId(8), 1.125f, "AI Core Fleet Directives");
        } else if (Commodities.OMEGA_CORE.equals(coreId)) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId(8), 1.25f, "Omega Core Fleet Directives");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(8));
        }

        // 5. IMPROVEMENTS (TIERS 1 - 4)
        int impLevel = getImproveProductionBonus();

        if (impLevel >= 3) {
            market.getAccessibilityMod().modifyFlat(getModId(2), 0.50f, "Assimilated Spaceport Enhancement");
        } else {
            market.getAccessibilityMod().unmodifyFlat(getModId(2));
        }

        // 6. SPECIAL AI CORE: OMEGA CORE ECONOMIC BONUSES
        if (Commodities.OMEGA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(3), 0.75f, "Omega Core Efficiency");
            getIncome().modifyPercent(getModId(4), 2.5f, "Omega Core Economic Optimization");
        } else {
            getUpkeep().unmodifyMult(getModId(3));
            getIncome().unmodifyPercent(getModId(4));
        }

        // 7. POPULATION ASSIMILATION (MARKET COMPOSITION)
        assimilateComposition(market.getPopulation());
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null) return;

        market.getAccessibilityMod().unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(1));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(8));
        market.getAccessibilityMod().unmodifyFlat(getModId(2));

        getUpkeep().unmodifyMult(getModId(3));
        getIncome().unmodifyPercent(getModId(4));
    }

    // -----------------------------------------------------------------
    // POPULATION GROWTH & INCOMING COMPOSITION ASSIMILATION
    // -----------------------------------------------------------------
    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        super.modifyIncoming(market, incoming);

        // Base Population Growth (+2)
        incoming.getWeight().modifyFlat(getModId(5), 2f, "Assimilated Spaceport Growth");

        // Improvement Tier 1 (+1 Population Growth)
        if (getImproveProductionBonus() >= 1) {
            incoming.getWeight().modifyFlat(getModId(6), 1f, "Assimilated Improvement Tier 1");
        } else {
            incoming.getWeight().unmodifyFlat(getModId(6));
        }

        // Omega Core (+1 Population Growth)
        if (Commodities.OMEGA_CORE.equals(getAICoreId())) {
            incoming.getWeight().modifyFlat(getModId(7), 1f, "Omega Core Population Directives");
        } else {
            incoming.getWeight().unmodifyFlat(getModId(7));
        }

        // Instantly assimilate incoming population into Borg
        assimilateComposition(incoming);
    }

    /*** Replaces all faction compositions with 100% Borg population.*/
    protected void assimilateComposition(PopulationComposition comp) {
        if (comp == null || comp.getComp() == null) return;

        float totalWeight = comp.getPositiveWeight();
        if (totalWeight <= 0f) {
            totalWeight = 1.0f;
        }

        List<String> keys = new ArrayList<String>(comp.getComp().keySet());
        for (String key : keys) {
            comp.set(key, 0f);
        }

        comp.set(BorgIDS.BORG, totalWeight);
    }

    // -----------------------------------------------------------------
    // AI CORE TOOLTIPS
    // -----------------------------------------------------------------
    @Override
    public void addAICoreSection(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        String coreId = getAICoreId();

        if (coreId == null) return;

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            if (Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("Gamma Core assigned: -1 demand, x1.125 combat fleet size.", opad, h, "-1", "x1.125");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Alpha Core assigned: -1 demand, +1 supply, x1.125 combat fleet size.", opad, h, "-1", "+1", "x1.125");
            } else if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Omega Core assigned: -1 demand, +1 supply, x1.25 combat fleet size, +1 pop growth, -25%% upkeep, +2.5%% income.", opad, h, "-1", "+1", "x1.25", "+1", "-25%", "+2.5%");
            } else if (Commodities.BETA_CORE.equals(coreId)) {
                tooltip.addPara("Beta Core assigned: -1 demand.", opad, h, "-1");
            }
        } else if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_INSTALLED) {
            if (Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces commodity demand by 1 unit and increases combat fleet size by x1.125.", opad, h, "1", "x1.125");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces demand by 1 unit, increases supply by 1 unit, and increases combat fleet size by x1.125.", opad, h, "1", "1", "x1.125");
            } else if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces demand by 1 unit, increases supply by 1 unit, increases combat fleet size by x1.25, increases pop growth by 1, reduces upkeep by 25%%, and increases income by 2.5%%.", opad, h, "1", "1", "x1.25", "1", "25%", "2.5%");
            } else if (Commodities.BETA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces commodity demand by 1 unit.", opad, h, "1");
            }
        } else if (mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            if (Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("Gamma Core: -1 demand, x1.125 combat fleet size.", opad, h, "-1", "x1.125");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Alpha Core: -1 demand, +1 supply, x1.125 combat fleet size.", opad, h, "-1", "+1", "x1.125");
            } else if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Omega Core: -1 demand, +1 supply, x1.25 combat fleet size, +1 pop growth.", opad, h, "-1", "+1", "x1.25", "+1");
            } else if (Commodities.BETA_CORE.equals(coreId)) {
                tooltip.addPara("Beta Core: -1 demand.", opad, h, "-1");
            }
        }
    }

    // -----------------------------------------------------------------
    // LOOKUP TABLES
    // -----------------------------------------------------------------
    private float getFleetSizeMultForSize(int size) {
        switch (size) {
            case 1: case 2:
                return 1.00f;
            case 3:
                return 1.21875f;
            case 4:
                return 1.4375f;
            case 5:
                return 1.65625f;
            case 6:
                return 1.875f;
            case 7:
                return 2.09375f;
            case 8:
                return 2.3125f;
            case 9:
                return 2.53125f;
            default: // 10+
                return 2.75f;
        }
    }

    private int getBaseDemandForSize(int size) {
        if (size <= 5) return 3;
        if (size <= 8) return 4;
        if (size == 9) return 5;
        return 6; // 10+
    }

    private int getBaseSupplyForSize(int size) {
        if (size <= 3) return 3;
        if (size <= 5) return 4;
        if (size <= 6) return 5;
        if (size <= 8) return 6;
        if (size == 9) return 7;
        return 8; // 10+
    }

    private int getDilithiumDemandForSize(int size) {
        if (size <= 3) return 1;
        if (size <= 6) return 2;
        if (size <= 8) return 3;
        return 4; // 9+
    }
}