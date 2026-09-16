package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class StarfleetAcademy extends BaseIndustry implements MarketImmigrationModifier {

    protected int improvementLevel = 0;

    // --- DO NOT CONSUME INDUSTRY SLOT ---
    @Override
    public boolean isStructure() {
        return true;
    }

    // --- SUPPLY / DEMAND SCALING TABLES ---
    private int getBaseCadetDemand(int marketSize) {
        return switch (marketSize) {
            case 3 -> 1;
            case 4, 5, 6 -> 2;
            case 7, 8, 9 -> 3;
            default -> (marketSize >= 10) ? 4 : 1;
        };
    }

    private int getSupplementCivilianDemand(int marketSize) {
        return switch (marketSize) {
            case 3, 4 -> 1;
            case 5, 6 -> 3;
            case 7, 8, 9 -> 4;
            default -> (marketSize >= 10) ? 5 : 1;
        };
    }

    private int getBaseFoodDemand(int marketSize) {
        return switch (marketSize) {
            case 3, 4, 5 -> 3;
            case 6, 7, 8 -> 4;
            case 9 -> 5;
            default -> (marketSize >= 10) ? 6 : 3;
        };
    }

    private int getBaseOfficerSupply(int marketSize) {
        return switch (marketSize) {
            case 3 -> 3;
            case 4, 5 -> 4;
            case 6 -> 5;
            case 7, 8 -> 6;
            case 9 -> 7;
            default -> (marketSize >= 10) ? 8 : 3;
        };
    }

    @Override
    public void apply() {

        int size = market.getSize();

        // Base Supply & Demand values
        int baseCadetDemand = getBaseCadetDemand(size);
        int baseFoodDemand = getBaseFoodDemand(size);
        int baseOfficerSupply = getBaseOfficerSupply(size);

        // Accumulators
        int generalDemandReduction = 0;
        int foodDemandReduction = 0;
        int cadetDemandReduction = 0;
        int bonusSupply = 0;

        float accessibility = 0.20f; // Base Accessibility = +20%

        // --- BASE STAT MODIFIERS ---
        market.getStability().modifyFlat(getModId(0), 1f, getNameForModifier());
        getIncome().modifyPercent(getModId(0), 2.5f, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(0), 0.25f, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).modifyFlat(getModId(0), 0.40f, getNameForModifier());

        // --- IMPROVEMENTS ---
        if (improvementLevel >= 1) foodDemandReduction += 1;
        if (improvementLevel >= 2) cadetDemandReduction += 1;
        if (improvementLevel >= 4) {
            bonusSupply += 2;
            accessibility += 0.05f;
            generalDemandReduction += 1;
            getUpkeep().modifyMult(getModId(4), 0.85f, "Starfleet Academy Tier IV (-15% Upkeep)");
        }

        // --- AI CORES ---
        if (aiCoreId != null) {
            generalDemandReduction += 1; // All cores reduce demand by -1

            switch (aiCoreId) {
                case Commodities.GAMMA_CORE ->
                        getUpkeep().modifyMult(getModId(1), 0.75f, "Gamma Core (-25% Upkeep)");
                case Commodities.ALPHA_CORE -> {
                    bonusSupply += 1;
                    getUpkeep().modifyMult(getModId(1), 0.75f, "Alpha Core (-25% Upkeep)");
                }
                case Commodities.OMEGA_CORE -> {
                    bonusSupply += 2;
                    getUpkeep().modifyMult(getModId(1), 0.50f, "Omega Core (-50% Upkeep)");
                    market.getStability().modifyFlat(getModId(1), 1f, "Omega Core (+1 Stability)");
                    market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                            .modifyPercent(getModId(1), 5f, "Omega Core (+5% Fleet Size)");
                }
            }
        }

        // Apply Accessibility
        market.getAccessibilityMod().modifyFlat(getModId(0), accessibility, getNameForModifier());

        // --- DEMANDS IMPLEMENTATION ---
        int finalCadetDemand = Math.max(0, baseCadetDemand - cadetDemandReduction - generalDemandReduction);
        int finalFoodDemand = Math.max(0, baseFoodDemand - foodDemandReduction - generalDemandReduction);

        demand(DimensionsCrossedIDS.CADETS, finalCadetDemand);
        demand(Commodities.FOOD, finalFoodDemand);

        // CADET DEFICIT & CIVILIAN FALLBACK LOGIC
        Pair<String, Integer> cadetDeficit = getMaxDeficit(DimensionsCrossedIDS.CADETS);
        int cDef = (cadetDeficit != null) ? cadetDeficit.two : 0;

        List<String> productionDeficitCommodities = new ArrayList<>();
        productionDeficitCommodities.add(Commodities.FOOD);

        if (cDef > 0) {
            // Cadets not met: Demand CIVILIANS to supplement
            int baseCivilianDemand = getSupplementCivilianDemand(size);
            int finalCivilianDemand = Math.max(0, baseCivilianDemand - generalDemandReduction);
            demand(DimensionsCrossedIDS.CIVILIAN, finalCivilianDemand);

            // Production deficit will depend on CIVILIAN availability instead
            productionDeficitCommodities.add(DimensionsCrossedIDS.CIVILIAN);
        } else {
            // Cadets met: Production deficit relies on CADETS
            productionDeficitCommodities.add(DimensionsCrossedIDS.CADETS);
        }

        // --- CALCULATE DEFICIT & SUPPLY ---
        Pair<String, Integer> maxDeficit = getMaxDeficit(productionDeficitCommodities.toArray(new String[0]));
        int deficit = (maxDeficit != null) ? maxDeficit.two : 0;

        int finalOfficerSupply = Math.max(0, baseOfficerSupply + bonusSupply - deficit);
        supply(DimensionsCrossedIDS.STARFLEET_OFFICERS, finalOfficerSupply);

        // Register Immigration Modifier for Tier III Improvement
        market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply() {
        super.unapply();

        market.getStability().unmodifyFlat(getModId(0));
        market.getStability().unmodifyFlat(getModId(1));
        getIncome().unmodifyPercent(getModId(0));
        getUpkeep().unmodifyMult(getModId(1));
        getUpkeep().unmodifyMult(getModId(4));

        market.getAccessibilityMod().unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyPercent(getModId(1));
    }

    // --- POPULATION GROWTH MODIFIER (TIER III) ---
    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (improvementLevel >= 3) {
            incoming.getWeight().modifyFlat(getModId(3), 10f, getNameForModifier() + " (Tier III)");
        }
    }

    // --- IMPROVEMENT TIERS SUPPORT ---
    @Override
    public boolean canImprove() {
        return improvementLevel < 4;
    }

    public int getImprovementLevel() {
        return improvementLevel;
    }

    public void setImprovementLevel(int level) {
        this.improvementLevel = Math.min(4, Math.max(0, level));
    }

    // --- TOOLTIPS WITH ICONS ---
    @Override
    public void addAICoreSection(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (coreId == null) {
            super.addAICoreSection(tooltip, coreId, mode);
            return;
        }

        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(coreId);
        if (spec == null) {
            super.addAICoreSection(tooltip, coreId, mode);
            return;
        }

        TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48f);

        switch (coreId) {
            case Commodities.BETA_CORE ->
                    text.addPara("Beta Core assigned. Reduces demand by %s.", 0f, h, "1 unit");
            case Commodities.GAMMA_CORE ->
                    text.addPara("Gamma Core assigned. Reduces demand by %s and upkeep by %s.", 0f, h, "1 unit", "25%");
            case Commodities.ALPHA_CORE ->
                    text.addPara("Alpha Core assigned. Reduces demand by %s, increases supply by %s, and reduces upkeep by %s.", 0f, h, "1 unit", "1 unit", "25%");
            case Commodities.OMEGA_CORE ->
                    text.addPara("Omega Core assigned. Reduces demand by %s, increases supply by %s, reduces upkeep by %s, adds %s stability, and increases fleet size by %s.", 0f, h, "1 unit", "2 units", "50%", "+1", "5%");
            default -> {
                super.addAICoreSection(tooltip, coreId, mode);
                return;
            }
        }

        tooltip.addImageWithText(opad);
    }
}