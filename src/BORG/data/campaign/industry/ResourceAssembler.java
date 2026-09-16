package BORG.data.campaign.industry;

import java.awt.Color;

import BORG.data.campaign.ids.BorgIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.econ.impl.Refining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class ResourceAssembler extends Refining {

    @Override
    public void apply() {
        super.apply();

        if (market == null) return;
        int size = market.getSize();

        // -----------------------------------------------------------------
        // 1. DEMAND CALCULATIONS
        // -----------------------------------------------------------------
        int baseDemand = getBaseDemandForSize(size);
        int auxDemand = getAuxDemandForSize(size);

        // Tier 2 Improvement (-1 Demand)
        if (getImproveProductionBonus() >= 2) {
            baseDemand = Math.max(0, baseDemand - 1);
            auxDemand = Math.max(0, auxDemand - 1);
        }

        // AI Core Demand Reductions (-1 for Beta, Gamma, Alpha, Omega)
        String coreId = getAICoreId();
        if (Commodities.BETA_CORE.equals(coreId) ||
                Commodities.GAMMA_CORE.equals(coreId) ||
                Commodities.ALPHA_CORE.equals(coreId) ||
                Commodities.OMEGA_CORE.equals(coreId)) {
            baseDemand = Math.max(0, baseDemand - 1);
            auxDemand = Math.max(0, auxDemand - 1);
        }

        // Apply Demands
        demand(Commodities.HEAVY_MACHINERY, baseDemand);
        demand(BorgIDS.DRONES, auxDemand);
        demand(BorgIDS.ASSIMILATED_CIVILIANS, auxDemand);

        // Input Commodity Demands
        demand(Commodities.ORE, baseDemand);
        demand(Commodities.RARE_ORE, baseDemand);
        demand(DimensionsCrossedIDS.DILITHIUM_ORE, baseDemand);
        demand(Commodities.VOLATILES, baseDemand);

        // -----------------------------------------------------------------
        // 2. SUPPLY CALCULATIONS & PRODUCTION RATIOS
        // -----------------------------------------------------------------
        int baseSupply = getBaseSupplyForSize(size);

        // Tier 1 & Tier 3 Improvements (+1 Supply each)
        if (getImproveProductionBonus() >= 1) {
            baseSupply += 1;
        }
        if (getImproveProductionBonus() >= 3) {
            baseSupply += 1;
        }

        // Alpha & Omega Core Supply Bonus (+1 Supply)
        if (Commodities.ALPHA_CORE.equals(coreId) || Commodities.OMEGA_CORE.equals(coreId)) {
            baseSupply += 1;
        }

        // Special Item: Catalytic Core (+1 Supply)
        if (special != null && Items.CATALYTIC_CORE.equals(special.getId())) {
            baseSupply += 1;
        }

        // Available Input Quantities from Market
        int oreAvail = market.getCommodityData(Commodities.ORE).getAvailable();
        int rareOreAvail = market.getCommodityData(Commodities.RARE_ORE).getAvailable();
        int dilithiumOreAvail = market.getCommodityData(DimensionsCrossedIDS.DILITHIUM_ORE).getAvailable();
        int volatilesAvail = market.getCommodityData(Commodities.VOLATILES).getAvailable();

        // Production requires at least one raw resource available
        boolean hasInputsAvailable = (oreAvail > 0) || (rareOreAvail > 0) || (dilithiumOreAvail > 0) || (volatilesAvail > 0);

        if (!hasInputsAvailable) {
            supply(Commodities.METALS, 0);
            supply(Commodities.RARE_METALS, 0);
            supply(DimensionsCrossedIDS.DILITHIUM, 0);
            supply(Commodities.FUEL, 0);
        } else {
            // 1 ORE = 2 METALS
            if (oreAvail > 0) {
                supply(Commodities.METALS, Math.min(baseSupply + 1, oreAvail + 1));
            } else {
                supply(Commodities.METALS, 0);
            }

            // 1 RARE_ORE = 1 RARE_METALS
            if (rareOreAvail > 0) {
                supply(Commodities.RARE_METALS, Math.min(baseSupply, rareOreAvail));
            } else {
                supply(Commodities.RARE_METALS, 0);
            }

            // 2 DILITHIUM_ORE = 1 DILITHIUM
            if (dilithiumOreAvail > 0) {
                int possibleDilithium = dilithiumOreAvail / 2;
                supply(DimensionsCrossedIDS.DILITHIUM, Math.min(baseSupply, possibleDilithium));
            } else {
                supply(DimensionsCrossedIDS.DILITHIUM, 0);
            }

            // 1 VOLATILES = 2 FUEL
            if (volatilesAvail > 0) {
                supply(Commodities.FUEL, Math.min(baseSupply + 1, volatilesAvail + 1));
            } else {
                supply(Commodities.FUEL, 0);
            }
        }

        // Omega Core Special Production Bonus (+2 Supplies)
        if (Commodities.OMEGA_CORE.equals(coreId)) {
            supply(Commodities.SUPPLIES, 2);
        } else {
            supply(Commodities.SUPPLIES, 0);
        }

        // -----------------------------------------------------------------
        // 3. HAZARD, STABILITY & UPKEEP MODIFIERS
        // -----------------------------------------------------------------
        // Catalytic Core: +5% Hazard Rating, +2 Stability
        if (special != null && Items.CATALYTIC_CORE.equals(special.getId())) {
            market.getHazard().modifyFlat(getModId(0), 0.05f, "Catalytic Core Processing");
            market.getStability().modifyFlat(getModId(1), 2f, "Catalytic Core Structural Order");
        } else {
            market.getHazard().unmodifyFlat(getModId(0));
            market.getStability().unmodifyFlat(getModId(1));
        }

        // Tier 4 Improvement (-20% Hazard Rating)
        if (getImproveProductionBonus() >= 4) {
            market.getHazard().modifyFlat(getModId(2), -0.20f, "Resource Assembler Optimization Tier 4");
        } else {
            market.getHazard().unmodifyFlat(getModId(2));
        }

        // Upkeep Reductions (-25% for Gamma, Alpha, and Omega Cores)
        if (Commodities.GAMMA_CORE.equals(coreId) || Commodities.ALPHA_CORE.equals(coreId) || Commodities.OMEGA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(3), 0.75f, "AI Core Upkeep Reduction");
        } else {
            getUpkeep().unmodifyMult(getModId(3));
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null) return;

        market.getHazard().unmodifyFlat(getModId(0));
        market.getStability().unmodifyFlat(getModId(1));
        market.getHazard().unmodifyFlat(getModId(2));
        getUpkeep().unmodifyMult(getModId(3));
    }

    // -----------------------------------------------------------------
    // SPECIAL ITEMS SUPPORT
    // -----------------------------------------------------------------
    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        return Items.CATALYTIC_CORE.equals(data.getId()) || super.wantsToUseSpecialItem(data);
    }

    // -----------------------------------------------------------------
    // TOOLTIPS
    // -----------------------------------------------------------------
    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (special != null && Items.CATALYTIC_CORE.equals(special.getId())) {
            tooltip.addPara("Catalytic Core installed: +1 supply, +5%% hazard rating, and +2 colony stability.", opad, h, "+1", "+5%", "+2");
        }

        String coreId = getAICoreId();
        if (Commodities.OMEGA_CORE.equals(coreId)) {
            tooltip.addPara("Omega Core assigned: -1 demand, +1 supply, -25%% upkeep, and produces %s.", opad, h, "2 units of Supplies");
        }
    }

    // -----------------------------------------------------------------
    // LOOKUP TABLES
    // -----------------------------------------------------------------
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

    private int getAuxDemandForSize(int size) {
        if (size <= 3) return 1;
        if (size <= 6) return 2;
        if (size <= 8) return 3;
        return 4; // 9+
    }
}