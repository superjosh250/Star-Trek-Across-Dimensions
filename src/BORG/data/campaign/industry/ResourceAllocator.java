package BORG.data.campaign.industry;

import java.awt.Color;
import java.util.Random;

import BORG.data.campaign.ids.BorgIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Items;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class ResourceAllocator extends BaseIndustry {

    @Override
    public void apply() {
        // Runs BaseIndustry setup (handles upkeep, AI cores, story improvements)
        // without pulling in vanilla Mining's default demands (like Drugs)

        if (market == null) return;
        int size = market.getSize();

        // -----------------------------------------------------------------
        // 1. BASE STATS & HAZARD MODIFIERS
        // -----------------------------------------------------------------
        float baseHazardMod = 0.125f; // Base Hazard Rating (+12.5%)

        // Special Item: Mantle Bore (-12.5% Hazard)
        if (special != null && Items.MANTLE_BORE.equals(special.getId())) {
            baseHazardMod -= 0.125f;
        }

        // Special Item: Plasma Dynamo (-5% Hazard)
        if (special != null && Items.PLASMA_DYNAMO.equals(special.getId())) {
            baseHazardMod -= 0.05f;
        }

        if (baseHazardMod != 0f) {
            market.getHazard().modifyFlat(getModId(0), baseHazardMod, "Resource Allocator Infrastructure");
        } else {
            market.getHazard().unmodifyFlat(getModId(0));
        }

        // -----------------------------------------------------------------
        // 2. DEMAND CALCULATIONS & SPECIAL MODIFIERS
        // -----------------------------------------------------------------
        int baseDemand = getBaseDemandForSize(size);

        // Tier 2 Improvement (-1 Demand)
        if (getImproveProductionBonus() >= 2) {
            baseDemand = Math.max(0, baseDemand - 1);
        }

        // Omega Core (-1 Demand)
        if (Commodities.OMEGA_CORE.equals(getAICoreId())) {
            baseDemand = Math.max(0, baseDemand - 1);
        }

        // Special Item: Mantle Bore (-4 Demand)
        if (special != null && Items.MANTLE_BORE.equals(special.getId())) {
            baseDemand = Math.max(0, baseDemand - 4);
        }

        // Special Item: Plasma Dynamo (-1 Demand)
        if (special != null && Items.PLASMA_DYNAMO.equals(special.getId())) {
            baseDemand = Math.max(0, baseDemand - 1);
        }

        demand(Commodities.HEAVY_MACHINERY, baseDemand);

        // Safe Deficit Lookup via Market Availability (Avoids StackOverflow)
        int hMachSupply = market.getCommodityData(Commodities.HEAVY_MACHINERY).getAvailable();
        int hMachDeficit = Math.max(0, baseDemand - hMachSupply);

        if (hMachDeficit > 0) {
            int extraNeeded = hMachDeficit * 2;
            Random rand = new Random(market.getId().hashCode() + 7);

            // Randomly split fallback demand between Drones and Assimilated Civilians
            int dronesExtra = rand.nextInt(extraNeeded + 1);
            int civiliansExtra = extraNeeded - dronesExtra;

            demand(BorgIDS.DRONES, dronesExtra);
            demand(BorgIDS.ASSIMILATED_CIVILIANS, civiliansExtra);
        }

        // -----------------------------------------------------------------
        // 3. SUPPLY CALCULATIONS & CONDITION CHECKS
        // -----------------------------------------------------------------
        int baseSupply = getBaseSupplyForSize(size);

        // Tier 1 Improvement (+1 Supply)
        if (getImproveProductionBonus() >= 1) {
            baseSupply += 1;
        }

        // Tier 4 Improvement (+1 Supply)
        if (getImproveProductionBonus() >= 4) {
            baseSupply += 1;
        }

        // Omega Core (+1 Supply)
        if (Commodities.OMEGA_CORE.equals(getAICoreId())) {
            baseSupply += 1;
        }

        // Special Item: Mantle Bore (+1 Supply)
        if (special != null && Items.MANTLE_BORE.equals(special.getId())) {
            baseSupply += 1;
        }

        // Apply condition checks for production
        if (hasOreCondition()) {
            supply(Commodities.ORE, baseSupply);
        } else {
            supply(Commodities.ORE, Math.max(0, baseSupply - 2));
        }

        if (hasRareOreCondition()) {
            supply(Commodities.RARE_ORE, baseSupply);
        } else {
            supply(Commodities.RARE_ORE, Math.max(0, baseSupply - 2));
        }

        // Volatiles: Plasma Dynamo gives extra +1 Volatiles supply
        int volatilesSupply = hasVolatilesCondition() ? baseSupply : Math.max(0, baseSupply - 2);
        if (special != null && Items.PLASMA_DYNAMO.equals(special.getId())) {
            volatilesSupply += 1;
        }
        supply(Commodities.VOLATILES, volatilesSupply);

        // Organics
        supply(Commodities.ORGANICS, baseSupply);

        // Dilithium Ore
        if (market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_TRACE)) {
            supply(DimensionsCrossedIDS.DILITHIUM_ORE, baseSupply);
        } else if (market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_MODERATE)) {
            supply(DimensionsCrossedIDS.DILITHIUM_ORE, baseSupply + 1);
        } else if (market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE)) {
            supply(DimensionsCrossedIDS.DILITHIUM_ORE, baseSupply + 2);
        } else if (market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_ABUNDANT)) {
            supply(DimensionsCrossedIDS.DILITHIUM_ORE, baseSupply + 4);
        }

        // -----------------------------------------------------------------
        // 4. IMPROVEMENTS (TIERS 3 & 4 HAZARD REDUCTION)
        // -----------------------------------------------------------------
        int impLevel = getImproveProductionBonus();

        if (impLevel == 3) {
            market.getHazard().modifyFlat(getModId(1), -0.25f, "Resource Allocator Optimization Tier 3");
        } else if (impLevel >= 4) {
            market.getHazard().modifyFlat(getModId(1), -0.50f, "Resource Allocator Optimization Tier 4");
        } else {
            market.getHazard().unmodifyFlat(getModId(1));
        }

        // -----------------------------------------------------------------
        // 5. OMEGA CORE SPECIFIC MULTIPLIERS
        // -----------------------------------------------------------------
        if (Commodities.OMEGA_CORE.equals(getAICoreId())) {
            getUpkeep().modifyMult(getModId(2), 0.75f, "Omega Core Efficiency");
        } else {
            getUpkeep().unmodifyMult(getModId(2));
        }

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null) return;

        market.getHazard().unmodifyFlat(getModId(0));
        market.getHazard().unmodifyFlat(getModId(1));
        getUpkeep().unmodifyMult(getModId(2));
    }

    public void modifyIncoming(MarketAPI market, com.fs.starfarer.api.impl.campaign.population.PopulationComposition incoming) {
        // Omega Core (+1 Population Growth)
        if (Commodities.OMEGA_CORE.equals(getAICoreId())) {
            incoming.getWeight().modifyFlat(getModId(3), 1f, "Omega Core Population Directives");
        }
    }

    // -----------------------------------------------------------------
    // SPECIAL ITEMS SUPPORT & TOOLTIPS
    // -----------------------------------------------------------------
    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        String itemId = data.getId();
        return Items.MANTLE_BORE.equals(itemId) || Items.PLASMA_DYNAMO.equals(itemId) || super.wantsToUseSpecialItem(data);
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (special != null) {
            if (Items.MANTLE_BORE.equals(special.getId())) {
                tooltip.addPara("Mantle Bore installed: +1 supply, -12.5%% hazard rating, and -4 demand.", opad, h, "+1", "-12.5%", "-4");
            } else if (Items.PLASMA_DYNAMO.equals(special.getId())) {
                tooltip.addPara("Plasma Dynamo installed: +1 Volatiles supply, -5%% hazard rating, and -1 demand.", opad, h, "+1", "-5%", "-1");
            }
        }

        if (Commodities.OMEGA_CORE.equals(getAICoreId())) {
            tooltip.addPara("Omega Core assigned: -1 demand, +1 supply, -25%% upkeep, and boosts population growth by %s.", opad, h, "+1");
        }
    }

    // -----------------------------------------------------------------
    // CONDITION CHECKERS & HELPERS
    // -----------------------------------------------------------------
    private boolean hasOreCondition() {
        return market.hasCondition(Conditions.ORE_SPARSE) ||
                market.hasCondition(Conditions.ORE_MODERATE) ||
                market.hasCondition(Conditions.ORE_ABUNDANT) ||
                market.hasCondition(Conditions.ORE_RICH) ||
                market.hasCondition(Conditions.ORE_ULTRARICH);
    }

    private boolean hasRareOreCondition() {
        return market.hasCondition(Conditions.RARE_ORE_SPARSE) ||
                market.hasCondition(Conditions.RARE_ORE_MODERATE) ||
                market.hasCondition(Conditions.RARE_ORE_ABUNDANT) ||
                market.hasCondition(Conditions.RARE_ORE_RICH) ||
                market.hasCondition(Conditions.RARE_ORE_ULTRARICH);
    }

    private boolean hasVolatilesCondition() {
        return market.hasCondition(Conditions.VOLATILES_TRACE) ||
                market.hasCondition(Conditions.VOLATILES_DIFFUSE) ||
                market.hasCondition(Conditions.VOLATILES_ABUNDANT) ||
                market.hasCondition(Conditions.VOLATILES_PLENTIFUL);
    }

    private int getBaseDemandForSize(int size) {
        if (size <= 5) return 3;
        if (size <= 8) return 4;
        if (size == 9) return 5;
        return 6;
    }

    private int getBaseSupplyForSize(int size) {
        if (size <= 3) return 3;
        if (size <= 5) return 4;
        if (size <= 6) return 5;
        if (size <= 8) return 6;
        if (size == 9) return 7;
        return 8;
    }
}