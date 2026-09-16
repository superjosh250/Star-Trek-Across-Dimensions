package BORG.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import BORG.data.campaign.ids.BorgIDS;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class AssimilatedSociety extends PopulationAndInfrastructure {

    protected String addedRandomCondition = null;

    @Override
    public void apply() {
        super.apply();

        if (market == null) return;
        int size = market.getSize();
        String coreId = getAICoreId();

        // -----------------------------------------------------------------
        // 1. DEMAND CALCULATIONS
        // -----------------------------------------------------------------
        int baseFoodDemand = getBaseDemandForSize(size);
        int baseCyberneticsDemand = getBaseDemandForSize(size);

        int foodDemand = baseFoodDemand;
        int cyberneticsDemand = baseCyberneticsDemand;

        // Apply Omega Core demand reductions (-2)
        if (Commodities.OMEGA_CORE.equals(coreId)) {
            foodDemand = Math.max(0, foodDemand - 2);
            cyberneticsDemand = Math.max(0, cyberneticsDemand - 2);
        } else if (Commodities.BETA_CORE.equals(coreId) || Commodities.ALPHA_CORE.equals(coreId) || Commodities.GAMMA_CORE.equals(coreId)) {
            foodDemand = Math.max(0, foodDemand - 1);
            cyberneticsDemand = Math.max(0, cyberneticsDemand - 1);
        }

        // Apply Story Point Improvement demand reduction (-2)
        if (isImproved()) {
            foodDemand = Math.max(0, foodDemand - 2);
            cyberneticsDemand = Math.max(0, cyberneticsDemand - 2);
        }

        demand(Commodities.FOOD, foodDemand);
        demand(Commodities.CREW, size);

        // Cybernetics Demand Logic (Safely queried via market commodity availability)
        int totalCyberneticsSupply = market.getCommodityData(BorgIDS.CYBERNETICS).getAvailable();
        int cappedCyberneticsDemand = Math.max(0, totalCyberneticsSupply - 2);

        boolean externalProducer = false;
        for (Industry ind : market.getIndustries()) {
            if (ind != this && ind.isFunctional()) {
                MutableCommodityQuantity supplyQty = ind.getSupply(BorgIDS.CYBERNETICS);
                if (supplyQty != null && supplyQty.getQuantity() != null && supplyQty.getQuantity().getModifiedInt() > 0) {
                    externalProducer = true;
                    break;
                }
            }
        }

        if (externalProducer) {
            demand(BorgIDS.CYBERNETICS, cyberneticsDemand);
        } else {
            demand(BorgIDS.CYBERNETICS, Math.min(cyberneticsDemand, cappedCyberneticsDemand));
        }

        // -----------------------------------------------------------------
        // 2. SUPPLY CALCULATIONS
        // -----------------------------------------------------------------
        int baseSupply = getBaseSupplyForSize(size);

        // Special Items & Improvement level modifiers
        if (special != null) {
            if (Items.BIOFACTORY_EMBRYO.equals(special.getId())) {
                baseSupply += 1;
            } else if (Items.CRYOARITHMETIC_ENGINE.equals(special.getId())) {
                baseSupply += 1;
            }
        }

        if (isImproved()) {
            baseSupply += 1;
        }

        if (Commodities.ALPHA_CORE.equals(coreId)) {
            baseSupply += 1;
        } else if (Commodities.OMEGA_CORE.equals(coreId)) {
            baseSupply += 2;
        }

        supply(Commodities.CREW, baseSupply);
        supply(BorgIDS.DRONES, baseSupply);
        supply(BorgIDS.ASSIMILATED_CIVILIANS, baseSupply);

        // -----------------------------------------------------------------
        // 3. BASE STATS & OVERRIDES
        // -----------------------------------------------------------------
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId() + "_off_prob", 0.75f);
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId() + "_off_prob_size", 0.025f * Math.max(0, size - 3));
        market.getStats().getDynamic().getMod(Stats.OFFICER_IS_MERC_PROB_MOD).modifyFlat(getModId() + "_off_merc", 0.25f);
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).modifyFlat(getModId() + "_admin_prob", 0.40f);

        market.getAccessibilityMod().modifyFlat(getModId() + "_acc_base", 1.00f, "Collective Logistics");
        market.getStability().modifyFlat(getModId() + "_stab_base", 4f, "Collective Order");
        market.getHazard().modifyFlat(getModId() + "_haz_base", -0.25f, "Collective Assimilation");

        // Use modifyMult() so fleet size appears as x1.25 in tooltips
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMult(getModId() + "_fleet_size", 1.25f, "Assimilated Processing");

        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyFlatAlways(getModId() + "_ground_def", 10000f, "Assimilated Matrix Defenses");

        // -----------------------------------------------------------------
        // 4. MARKET CONDITION STACKING
        // -----------------------------------------------------------------
        boolean hasHot = market.hasCondition(Conditions.HOT);
        boolean hasVeryHot = market.hasCondition(Conditions.VERY_HOT);
        boolean hasDenseAtm = market.hasCondition(Conditions.DENSE_ATMOSPHERE);

        float accBonus = 0f;
        if (hasHot) accBonus += 0.10f;
        if (hasVeryHot) accBonus += 0.10f;
        if (hasDenseAtm) accBonus += 0.10f;

        if (accBonus > 0f) {
            market.getAccessibilityMod().modifyFlat(getModId() + "_acc_env", accBonus, "Environmental Synergy");
        } else {
            market.getAccessibilityMod().unmodifyFlat(getModId() + "_acc_env");
        }

        int condCount = 0;
        if (hasHot) condCount++;
        if (hasVeryHot) condCount++;
        if (hasDenseAtm) condCount++;

        int stackableConds = Math.min(2, condCount);
        if (stackableConds > 0) {
            market.getStability().modifyFlat(getModId() + "_stab_env", stackableConds * 1f, "Environmental Harmony");
        } else {
            market.getStability().unmodifyFlat(getModId() + "_stab_env");
        }

        // -----------------------------------------------------------------
        // 5. IMPROVEMENTS
        // -----------------------------------------------------------------
        if (isImproved()) {
            market.getStability().modifyFlat(getModId() + "_stab_imp", 1f, "Assimilated Improvement");
            market.getAccessibilityMod().modifyFlat(getModId() + "_acc_imp", 0.10f, "Assimilated Improvement Tier 2");
            applyTier4RandomCondition();
        } else {
            market.getStability().unmodifyFlat(getModId() + "_stab_imp");
            market.getAccessibilityMod().unmodifyFlat(getModId() + "_acc_imp");
            if (addedRandomCondition != null) {
                market.removeCondition(addedRandomCondition);
                addedRandomCondition = null;
            }
        }

        // -----------------------------------------------------------------
        // 6. SPECIAL AI CORE: OMEGA CORE
        // -----------------------------------------------------------------
        if (Commodities.OMEGA_CORE.equals(coreId)) {
            market.getStability().modifyFlat(getModId() + "_stab_omega", 1f, "Omega Core Processing");
            market.getHazard().modifyFlat(getModId() + "_haz_omega", -0.25f, "Omega Core Restructuring");
            getUpkeep().modifyMult(getModId() + "_upkeep_omega", 0.75f, "Omega Core Efficiency");
        } else {
            market.getStability().unmodifyFlat(getModId() + "_stab_omega");
            market.getHazard().unmodifyFlat(getModId() + "_haz_omega");
            getUpkeep().unmodifyMult(getModId() + "_upkeep_omega");
        }

        // -----------------------------------------------------------------
        // 7. MAX INDUSTRIES SLOT EXPANSION
        // -----------------------------------------------------------------
        market.getStats().getDynamic().getStat(Stats.MAX_INDUSTRIES)
                .modifyFlat(getModId() + "_max_ind", getMaxIndustries(size), "Assimilated Society Infrastructure");
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null) return;

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId() + "_off_prob");
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId() + "_off_prob_size");
        market.getStats().getDynamic().getMod(Stats.OFFICER_IS_MERC_PROB_MOD).unmodifyFlat(getModId() + "_off_merc");
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).unmodifyFlat(getModId() + "_admin_prob");

        market.getAccessibilityMod().unmodifyFlat(getModId() + "_acc_base");
        market.getStability().unmodifyFlat(getModId() + "_stab_base");
        market.getHazard().unmodifyFlat(getModId() + "_haz_base");

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_fleet_size");
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId() + "_ground_def");

        market.getAccessibilityMod().unmodifyFlat(getModId() + "_acc_env");
        market.getStability().unmodifyFlat(getModId() + "_stab_env");

        market.getStability().unmodifyFlat(getModId() + "_stab_imp");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_acc_imp");

        market.getStability().unmodifyFlat(getModId() + "_stab_omega");
        market.getHazard().unmodifyFlat(getModId() + "_haz_omega");
        getUpkeep().unmodifyMult(getModId() + "_upkeep_omega");

        market.getStats().getDynamic().getStat(Stats.MAX_INDUSTRIES).unmodifyFlat(getModId() + "_max_ind");

        if (addedRandomCondition != null) {
            market.removeCondition(addedRandomCondition);
            addedRandomCondition = null;
        }
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        super.modifyIncoming(market, incoming);

        // Base Population Growth (+4)
        incoming.getWeight().modifyFlat(getModId() + "_pop_base", 4f, "Assimilated Expansion");

        // Environment condition growth (+1 per valid condition, max 2)
        int condCount = 0;
        if (market.hasCondition(Conditions.HOT)) condCount++;
        if (market.hasCondition(Conditions.VERY_HOT)) condCount++;
        if (market.hasCondition(Conditions.DENSE_ATMOSPHERE)) condCount++;

        int stackableConds = Math.min(2, condCount);
        if (stackableConds > 0) {
            incoming.getWeight().modifyFlat(getModId() + "_pop_env", stackableConds * 1f, "Environmental Harmony");
        } else {
            incoming.getWeight().unmodifyFlat(getModId() + "_pop_env");
        }

        // Special Item: Cryoarithmetic Engine (+1 Pop Growth or +2 if Hot/Very Hot)
        if (special != null && Items.CRYOARITHMETIC_ENGINE.equals(special.getId())) {
            float cryoGrowth = (market.hasCondition(Conditions.HOT) || market.hasCondition(Conditions.VERY_HOT)) ? 2f : 1f;
            incoming.getWeight().modifyFlat(getModId() + "_pop_cryo", cryoGrowth, "Cryoarithmetic Thermal Harnessing");
        } else {
            incoming.getWeight().unmodifyFlat(getModId() + "_pop_cryo");
        }
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

    @Override
    public int getMaxIndustries() {
        return getMaxIndustries(market.getSize());
    }

    public static int getMaxIndustries(int size) {
        if (size <= 3) return 1;
        if (size == 4) return 2;
        if (size == 5) return 3;
        if (size == 6) return 4;
        if (size == 7) return 5;
        if (size == 8) return 6;
        if (size == 9) return 7;
        return 8;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        String itemId = data.getId();
        return Items.BIOFACTORY_EMBRYO.equals(itemId) || Items.CRYOARITHMETIC_ENGINE.equals(itemId) || super.wantsToUseSpecialItem(data);
    }

    private void applyTier4RandomCondition() {
        if (addedRandomCondition != null) return;

        List<String> validConds = new ArrayList<String>();
        if (!market.hasCondition(Conditions.HOT)) validConds.add(Conditions.HOT);
        if (!market.hasCondition(Conditions.VERY_HOT)) validConds.add(Conditions.VERY_HOT);
        if (!market.hasCondition(Conditions.DENSE_ATMOSPHERE)) validConds.add(Conditions.DENSE_ATMOSPHERE);

        if (!validConds.isEmpty()) {
            Random rand = new Random(market.getId().hashCode() + 4);
            addedRandomCondition = validConds.get(rand.nextInt(validConds.size()));
            market.addCondition(addedRandomCondition);
        }
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
            if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Omega Core assigned: -2 demand, +2 supply, +1 stability, -25%% hazard, -25%% upkeep.", opad, h, "-2", "+2", "+1", "-25%", "-25%");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Alpha Core assigned: -1 demand, +1 supply.", opad, h, "-1", "+1");
            } else if (Commodities.BETA_CORE.equals(coreId) || Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("%s assigned: -1 demand.", opad, h, coreId.toUpperCase(), "-1");
            }
        } else if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_INSTALLED) {
            if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces commodity demand by 2 units, increases supply by 2 units, increases stability by 1, reduces hazard rating by 25%%, and reduces upkeep by 25%%.", opad, h, "2", "2", "1", "25%", "25%");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces commodity demand by 1 unit and increases supply by 1 unit.", opad, h, "1", "1");
            } else if (Commodities.BETA_CORE.equals(coreId) || Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces commodity demand by 1 unit.", opad, h, "1");
            }
        } else if (mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Omega Core: -2 demand, +2 supply, +1 stability, -25%% hazard, -25%% upkeep.", opad, h, "-2", "+2", "+1", "-25%", "-25%");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Alpha Core: -1 demand, +1 supply.", opad, h, "-1", "+1");
            } else if (Commodities.BETA_CORE.equals(coreId) || Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("%s: -1 demand.", opad, h, coreId.toUpperCase(), "-1");
            }
        }
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Max Industries allowed by colony size: %s", opad, h, "" + getMaxIndustries(market.getSize()));
    }
}