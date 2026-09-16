package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.Refining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class AdvancedProcessingPlant extends Refining implements MarketImmigrationModifier {

    protected int improvementLevel = 0;

    // --- Supply / Demand Math Tables ---
    private int getCustomSupplyUnit(int marketSize) {
        return switch (marketSize) {
            case 3 -> 3;
            case 4, 5 -> 4;
            case 6 -> 5;
            case 7, 8 -> 6;
            case 9 -> 7;
            default -> (marketSize >= 10) ? 8 : Math.max(1, marketSize);
        };
    }

    private int getCustomDemandUnit(int marketSize) {
        int baseDemand = switch (marketSize) {
            case 3, 4, 5 -> 3;
            case 6, 7, 8 -> 4;
            case 9 -> 5;
            default -> (marketSize >= 10) ? 6 : Math.max(1, marketSize);
        };

        // If market size is below 5, demand is +1
        if (marketSize < 5) {
            baseDemand += 1;
        }
        return baseDemand;
    }

    @Override
    public void apply() {
        super.apply(true);

        int size = market.getSize();
        int baseSupply = getCustomSupplyUnit(size);
        int baseDemand = getCustomDemandUnit(size);

        int globalDemandReduction = 0;
        int globalBonusSupply = 0;
        int biofactoryHMDemandReduction = 0;
        int biofactoryDilithiumBonus = 0;
        float hazardReduction = 0.15f; // BASE hazard rating reduction = 15%

        // Base Stability Bonus (+2)
        market.getStability().modifyFlat(getModId(0), 2f, getNameForModifier());

        // --- AI CORE MODIFIERS ---
        if (aiCoreId != null) {
            switch (aiCoreId) {
                case Commodities.BETA_CORE -> globalDemandReduction += 1;
                case Commodities.GAMMA_CORE -> {
                    globalDemandReduction += 1;
                    getUpkeep().modifyMult(getModId(2), 0.90f, "Gamma Core (-10% Upkeep)");
                }
                case Commodities.ALPHA_CORE -> {
                    globalDemandReduction += 1;
                    globalBonusSupply += 1;
                    getUpkeep().modifyMult(getModId(2), 0.75f, "Alpha Core (-25% Upkeep)");
                }
                case Commodities.OMEGA_CORE -> {
                    globalDemandReduction += 1;
                    globalBonusSupply += 2;
                    getUpkeep().modifyMult(getModId(2), 0.75f, "Omega Core (-25% Upkeep)");
                    market.getAccessibilityMod().modifyFlat(getModId(2), 0.05f, "Omega Core");
                    market.getIncomeMult().modifyPercent(getModId(2), 5f, "Omega Core");
                    market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId(2), 0.05f, "Omega Core");
                }
            }
        }

        // --- SPECIAL ITEM MODIFIERS ---
        if (special != null) {
            String itemId = special.getId();
            if (Items.BIOFACTORY_EMBRYO.equals(itemId)) {
                biofactoryHMDemandReduction = 1;
                biofactoryDilithiumBonus = 2;
                market.getAccessibilityMod().modifyFlat(getModId(5), 0.10f, "Biofactory Embryo");
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId(5), 0.05f, "Biofactory Embryo");
            } else if (Items.CORRUPTED_NANOFORGE.equals(itemId)) {
                globalBonusSupply += 1;
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId(5), 0.05f, "Corrupted Nanoforge");
            } else if (Items.PRISTINE_NANOFORGE.equals(itemId)) {
                globalBonusSupply += 2;
                hazardReduction += 0.10f;
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId(5), 0.05f, "Pristine Nanoforge");
            }
        }

        // --- IMPROVEMENT TIERS ---
        if (improvementLevel >= 1) hazardReduction += 0.10f;
        if (improvementLevel >= 3) {
            market.getIncomeMult().modifyPercent(getModId() + "_imp3", 10f, "Advanced Processing Plant Tier III");
        }
        if (improvementLevel >= 4) {
            getUpkeep().modifyMult(getModId() + "_imp4", 0.90f, "Advanced Processing Plant Tier IV (-10% Upkeep)");
        }

        // --- DEMANDS IMPLEMENTATION ---
        int finalDemand = Math.max(0, baseDemand - globalDemandReduction);
        demand(Commodities.HEAVY_MACHINERY, Math.max(0, finalDemand - biofactoryHMDemandReduction));
        demand(Commodities.ORE, finalDemand);
        demand(Commodities.RARE_ORE, finalDemand);
        demand(Commodities.VOLATILES, finalDemand);
        demand(DimensionsCrossedIDS.DILITHIUM_ORE, finalDemand);

        // --- DEFICIT HANDLING & PRODUCTION MATH ---
        Pair<String, Integer> maxDeficit = getMaxDeficit(
                Commodities.HEAVY_MACHINERY,
                Commodities.ORE,
                Commodities.RARE_ORE,
                Commodities.VOLATILES,
                DimensionsCrossedIDS.DILITHIUM_ORE
        );
        int deficit = (maxDeficit != null) ? maxDeficit.two : 0;

        // Ensure at least 1 unit produced even at maximum deficit
        int metalsSupply = Math.max(1, baseSupply + globalBonusSupply - deficit);
        int rareMetalsSupply = Math.max(1, baseSupply + globalBonusSupply - deficit);

        // 2 units of FUEL produced for 1 unit of Volatiles (+1 supply tier level)
        int fuelSupply = Math.max(1, baseSupply + 1 + globalBonusSupply - deficit);

        // Dilithium 2:1 Ratio default (baseSupply - 1). Improved Tier IV makes it 1:1 (baseSupply).
        int dilithiumBase = (improvementLevel >= 4) ? baseSupply : (baseSupply - 1);
        int dilithiumSupply = Math.max(1, dilithiumBase + globalBonusSupply + biofactoryDilithiumBonus - deficit);

        // --- SUPPLIES IMPLEMENTATION ---
        supply(Commodities.METALS, metalsSupply);
        supply(Commodities.RARE_METALS, rareMetalsSupply);
        supply(Commodities.FUEL, fuelSupply);
        supply(DimensionsCrossedIDS.DILITHIUM, dilithiumSupply);

        // --- HAZARD RATING MODIFIER ---
        if (hazardReduction > 0f) {
            market.getHazard().modifyFlat(getModId(1), -hazardReduction, "Advanced Processing Efficiency");
        }

        market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getStability().unmodifyFlat(getModId(0));
        market.getHazard().unmodifyFlat(getModId(1));
        getUpkeep().unmodifyMult(getModId(2));
        getUpkeep().unmodifyMult(getModId() + "_imp4");

        market.getAccessibilityMod().unmodifyFlat(getModId(2));
        market.getAccessibilityMod().unmodifyFlat(getModId(5));

        market.getIncomeMult().unmodifyPercent(getModId(2));
        market.getIncomeMult().unmodifyPercent(getModId() + "_imp3");

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId(2));
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId(5));
    }

    // --- IMMIGRATION / GROWTH MODIFIER ---
    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        // No super call here since Refining does not implement MarketImmigrationModifier
        float growthBonus = 0f;
        if (improvementLevel >= 2) {
            growthBonus += 2f; // Tier II: +2 Population Growth
        }
        if (Commodities.OMEGA_CORE.equals(aiCoreId)) {
            growthBonus += 5f; // Omega Core: +5% Population Growth
        }

        if (growthBonus > 0f) {
            incoming.getWeight().modifyFlat(getModId(7), growthBonus, getNameForModifier());
        }
    }

    // --- ITEM INSTALLATION UI FIXES ---
    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        List<InstallableIndustryItemPlugin> list = new ArrayList<>();
        List<InstallableIndustryItemPlugin> superList = super.getInstallableItems();
        if (superList != null) {
            list.addAll(superList);
        }
        list.add(new GenericInstallableItemPlugin(this));
        return list;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        if (special != null && special.getId().equals(data.getId())) return false;

        String id = data.getId();
        if (Items.BIOFACTORY_EMBRYO.equals(id) ||
                Items.CORRUPTED_NANOFORGE.equals(id) ||
                Items.PRISTINE_NANOFORGE.equals(id)) {
            return true;
        }

        return super.wantsToUseSpecialItem(data);
    }

    // --- IMPROVEMENT SUPPORT (MAX 4) ---
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

    // --- TOOLTIPS ---
    @Override
    public void addAICoreSection(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (coreId != null) {
            switch (coreId) {
                case Commodities.BETA_CORE ->
                        tooltip.addPara("Beta Core assigned. Reduces demand by %s.", opad, h, "1 unit");
                case Commodities.GAMMA_CORE ->
                        tooltip.addPara("Gamma Core assigned. Reduces demand by %s and upkeep by %s.", opad, h, "1 unit", "10%");
                case Commodities.ALPHA_CORE ->
                        tooltip.addPara("Alpha Core assigned. Reduces demand by %s, increases supply by %s, and reduces upkeep by %s.", opad, h, "1 unit", "1 unit", "25%");
                case Commodities.OMEGA_CORE ->
                        tooltip.addPara("Omega Core assigned. Reduces demand by %s, increases supply by %s, reduces upkeep by %s, and increases accessibility, income, growth, and ship quality by %s.", opad, h, "1 unit", "2 units", "25%", "5%");
                default -> super.addAICoreSection(tooltip, coreId, mode);
            }
        } else {
            super.addAICoreSection(tooltip, coreId, mode);
        }
    }

    @Override
    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (special != null) {
            float opad = 10f;
            Color h = Misc.getHighlightColor();
            String id = special.getId();

            if (Items.BIOFACTORY_EMBRYO.equals(id)) {
                tooltip.addPara("Installed Special Item: %s", opad, h, "Biofactory Embryo (-1 Heavy Machinery Demand, +10% Accessibility, +2 Dilithium Supply, +5% Ship Quality)");
                return true;
            } else if (Items.CORRUPTED_NANOFORGE.equals(id)) {
                tooltip.addPara("Installed Special Item: %s", opad, h, "Corrupted Nanoforge (+1 Supply, +5% Stockpiles, +5% Ship Quality)");
                return true;
            } else if (Items.PRISTINE_NANOFORGE.equals(id)) {
                tooltip.addPara("Installed Special Item: %s", opad, h, "Pristine Nanoforge (+2 Supply, -10% Hazard Rating, +5% Ship Quality)");
                return true;
            }
        }
        return super.addNonAICoreInstalledItems(mode, tooltip, expanded);
    }
}