package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.Mining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Planets;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class AdvancedMining extends Mining implements MarketImmigrationModifier {

    public static final String OMEGA_CORE = "omega_core";
    protected int improvementLevel = 0;

    // --- Supply / Demand Math Tables ---
    private int getCustomDemandUnit(int marketSize) {
        return switch (marketSize) {
            case 3, 4, 5 -> 3;
            case 6, 7, 8 -> 4;
            case 9 -> 5;
            default -> (marketSize >= 10) ? 6 : Math.max(1, marketSize);
        };
    }

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

    // --- RESOURCE CONDITION EVALUATION ---
    private Integer getResourceConditionBonus(String commodityId) {
        if (market == null) return null;

        return switch (commodityId) {
            case Commodities.ORE -> {
                if (market.hasCondition(Conditions.ORE_ULTRARICH)) yield 4;
                if (market.hasCondition(Conditions.ORE_RICH)) yield 3;
                if (market.hasCondition(Conditions.ORE_ABUNDANT)) yield 2;
                if (market.hasCondition(Conditions.ORE_MODERATE)) yield 1;
                if (market.hasCondition(Conditions.ORE_SPARSE)) yield 0;
                yield null;
            }
            case Commodities.RARE_ORE -> {
                if (market.hasCondition(Conditions.RARE_ORE_ULTRARICH)) yield 4;
                if (market.hasCondition(Conditions.RARE_ORE_RICH)) yield 3;
                if (market.hasCondition(Conditions.RARE_ORE_ABUNDANT)) yield 2;
                if (market.hasCondition(Conditions.RARE_ORE_MODERATE)) yield 1;
                if (market.hasCondition(Conditions.RARE_ORE_SPARSE)) yield 0;
                yield null;
            }
            case Commodities.ORGANICS -> {
                if (market.hasCondition(Conditions.ORGANICS_PLENTIFUL)) yield 4;
                if (market.hasCondition(Conditions.ORGANICS_ABUNDANT)) yield 2;
                if (market.hasCondition(Conditions.ORGANICS_COMMON)) yield 1;
                if (market.hasCondition(Conditions.ORGANICS_TRACE)) yield 0;
                yield null;
            }
            case Commodities.VOLATILES -> {
                if (market.hasCondition(Conditions.VOLATILES_PLENTIFUL)) yield 4;
                if (market.hasCondition(Conditions.VOLATILES_ABUNDANT)) yield 2;
                if (market.hasCondition(Conditions.VOLATILES_TRACE)) yield 1;
                if (market.hasCondition(Conditions.VOLATILES_DIFFUSE)) yield 0;
                yield null;
            }
            case DimensionsCrossedIDS.DILITHIUM_ORE -> {
                if (market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_ABUNDANT)) yield 4;
                if (market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_MODERATE)) yield 2;
                if (market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_TRACE)) yield 0;
                yield null;
            }
            default -> null;
        };
    }

    public static boolean hasMiningCondition(MarketAPI market) {
        if (market == null) return false;
        return market.hasCondition(Conditions.ORE_SPARSE) || market.hasCondition(Conditions.ORE_MODERATE) ||
                market.hasCondition(Conditions.ORE_ABUNDANT) || market.hasCondition(Conditions.ORE_RICH) ||
                market.hasCondition(Conditions.ORE_ULTRARICH) || market.hasCondition(Conditions.RARE_ORE_SPARSE) ||
                market.hasCondition(Conditions.RARE_ORE_MODERATE) || market.hasCondition(Conditions.RARE_ORE_ABUNDANT) ||
                market.hasCondition(Conditions.RARE_ORE_RICH) || market.hasCondition(Conditions.RARE_ORE_ULTRARICH) ||
                market.hasCondition(Conditions.ORGANICS_TRACE) || market.hasCondition(Conditions.ORGANICS_COMMON) ||
                market.hasCondition(Conditions.ORGANICS_ABUNDANT) || market.hasCondition(Conditions.ORGANICS_PLENTIFUL) ||
                market.hasCondition(Conditions.VOLATILES_DIFFUSE) || market.hasCondition(Conditions.VOLATILES_TRACE) ||
                market.hasCondition(Conditions.VOLATILES_ABUNDANT) || market.hasCondition(Conditions.VOLATILES_PLENTIFUL) ||
                market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_TRACE) ||
                market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_MODERATE) ||
                market.hasCondition(DimensionsCrossedIDS.DILITHIUM_ORE_ABUNDANT);
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && hasMiningCondition(market);
    }

    @Override
    public String getUnavailableReason() {
        if (!hasMiningCondition(market)) {
            return "Requires at least one minable resource condition on the market.";
        }
        return super.getUnavailableReason();
    }

    // --- APPLY LOGIC ---
    @Override
    public void apply() {
        super.apply(true);

        int size = market.getSize();
        int baseDemand = getCustomDemandUnit(size);
        int baseSupply = getCustomSupplyUnit(size);

        int demandReduction = 0;
        int globalBonusSupply = 0;
        float hazardReduction = 0f;

        // --- AI CORE MODIFIERS ---
        if (aiCoreId != null) {
            switch (aiCoreId) {
                case Commodities.BETA_CORE -> demandReduction += 1;
                case Commodities.GAMMA_CORE -> {
                    demandReduction += 1;
                    hazardReduction += 0.05f;
                }
                case Commodities.ALPHA_CORE -> {
                    demandReduction += 1;
                    globalBonusSupply += 1;
                    hazardReduction += 0.05f;
                    getUpkeep().modifyMult(getModId(2), 0.75f, "Alpha Core (-25% Upkeep)");
                }
                case OMEGA_CORE -> {
                    demandReduction += 1;
                    globalBonusSupply += 2;
                    hazardReduction += 0.10f;
                    getUpkeep().modifyMult(getModId(2), 0.75f, "Omega Core (-25% Upkeep)");
                }
            }
        }

        // --- SPECIAL ITEM MODIFIERS ---
        boolean isGasGiant = market.getPlanetEntity() != null && Planets.GAS_GIANT.equals(market.getPlanetEntity().getTypeId());
        boolean isHabitable = market.hasCondition(Conditions.HABITABLE);

        if (special != null) {
            if (Items.MANTLE_BORE.equals(special.getId()) && !isGasGiant && !isHabitable) {
                globalBonusSupply += 2;
                hazardReduction += 0.20f;
            } else if (Items.PLASMA_DYNAMO.equals(special.getId())) {
                globalBonusSupply += 1;
                hazardReduction += 0.20f;
            }
        }

        // --- IMPROVEMENT TIERS ---
        if (improvementLevel >= 1) globalBonusSupply += 1;
        if (improvementLevel >= 2) hazardReduction += 0.10f;
        if (improvementLevel >= 3) {
            market.getIncomeMult().modifyPercent(getModId(3), 12.5f, "Advanced Mining Improvement Tier III");
        }
        if (improvementLevel >= 4) {
            demandReduction += 1;
            hazardReduction += 0.05f;
            addRandomMissingMiningCondition();
        }

        // --- DEMAND IMPLEMENTATION ---
        int finalDemand = Math.max(0, baseDemand - demandReduction);
        demand(Commodities.HEAVY_MACHINERY, finalDemand);

        Pair<String, Integer> hmDeficit = getMaxDeficit(Commodities.HEAVY_MACHINERY);
        if (hmDeficit != null && hmDeficit.two > 0) {
            int civilianNeeded = Math.min(finalDemand, hmDeficit.two);
            demand(DimensionsCrossedIDS.CIVILIAN, civilianNeeded);
        } else {
            demand(DimensionsCrossedIDS.CIVILIAN, 0);
        }

        // --- SUPPLY IMPLEMENTATION ---
        String[] minableCommodities = {
                Commodities.ORE,
                Commodities.RARE_ORE,
                Commodities.ORGANICS,
                Commodities.VOLATILES,
                DimensionsCrossedIDS.DILITHIUM_ORE
        };

        for (String commodity : minableCommodities) {
            Integer conditionBonus = getResourceConditionBonus(commodity);
            if (conditionBonus != null) {
                int finalSupply = baseSupply + conditionBonus + globalBonusSupply;
                if (special != null && Items.PLASMA_DYNAMO.equals(special.getId()) && Commodities.VOLATILES.equals(commodity)) {
                    finalSupply += 2; // Extra +2 Volatiles from Plasma Dynamo
                }
                supply(commodity, finalSupply);
            }
        }

        // --- HAZARD RATING & STABILITY MODIFIERS ---
        if (hazardReduction > 0f) {
            market.getHazard().modifyFlat(getModId(1), -hazardReduction, "Advanced Mining Efficiency");
        }

        if (market.hasIndustry(DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK)) {
            market.getStability().modifyFlat(getModId(4), 1f, "Spacedock Station Synergy");
        }

        market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getHazard().unmodifyFlat(getModId(1));
        getUpkeep().unmodifyMult(getModId(2));
        market.getIncomeMult().unmodifyPercent(getModId(3));
        market.getStability().unmodifyFlat(getModId(4));
    }

    // --- IMMIGRATION / GROWTH MODIFIER ---
    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        super.modifyIncoming(market, incoming);

        if (special != null) {
            boolean isGasGiant = market.getPlanetEntity() != null && Planets.GAS_GIANT.equals(market.getPlanetEntity().getTypeId());
            boolean isHabitable = market.hasCondition(Conditions.HABITABLE);

            if (Items.MANTLE_BORE.equals(special.getId()) && !isGasGiant && !isHabitable) {
                incoming.getWeight().modifyFlat(getModId(7), 5f, "Mantle Bore");
            } else if (Items.PLASMA_DYNAMO.equals(special.getId()) && isHabitable) {
                incoming.getWeight().modifyFlat(getModId(7), 15f, "Plasma Dynamo (Habitable)");
            }
        }
    }

    // --- IMPROVEMENT TIER 4 FEATURE ---
    private void addRandomMissingMiningCondition() {
        if (market.getMemoryWithoutUpdate().getBoolean("$advancedMining_addedCondition")) return;

        List<String> missingConditions = new ArrayList<>();
        if (getResourceConditionBonus(Commodities.ORE) == null) missingConditions.add(Conditions.ORE_MODERATE);
        if (getResourceConditionBonus(Commodities.RARE_ORE) == null) missingConditions.add(Conditions.RARE_ORE_MODERATE);
        if (getResourceConditionBonus(Commodities.ORGANICS) == null) missingConditions.add(Conditions.ORGANICS_COMMON);
        if (getResourceConditionBonus(Commodities.VOLATILES) == null) missingConditions.add(Conditions.VOLATILES_TRACE);
        if (getResourceConditionBonus(DimensionsCrossedIDS.DILITHIUM_ORE) == null) missingConditions.add(DimensionsCrossedIDS.DILITHIUM_ORE_TRACE);

        if (!missingConditions.isEmpty()) {
            String selectedCondition = missingConditions.get(Misc.random.nextInt(missingConditions.size()));
            market.addCondition(selectedCondition);
            market.getMemoryWithoutUpdate().set("$advancedMining_addedCondition", true);
        }
    }

    // --- SPECIAL ITEMS CONFIGURATION & VISUALS ---
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

        if (Items.MANTLE_BORE.equals(id)) {
            boolean isGasGiant = market.getPlanetEntity() != null && Planets.GAS_GIANT.equals(market.getPlanetEntity().getTypeId());
            boolean isHabitable = market.hasCondition(Conditions.HABITABLE);
            return !isGasGiant && !isHabitable;
        }

        if (Items.PLASMA_DYNAMO.equals(id)) {
            return true;
        }

        return super.wantsToUseSpecialItem(data);
    }

    @Override
    public void setSpecialItem(SpecialItemData special) {
        super.setSpecialItem(special);

        if (shownPlasmaNetVisuals && (special == null || !special.getId().equals(Items.PLASMA_DYNAMO))) {
            unapplyVisuals(market.getPlanetEntity());
        }

        if (special != null && special.getId().equals(Items.PLASMA_DYNAMO)) {
            applyVisuals(market.getPlanetEntity());
        }
    }

    // --- IMPROVEMENT SUPPORT ---
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
                        tooltip.addPara("Gamma Core assigned. Reduces demand by %s and hazard rating by %s.", opad, h, "1 unit", "5%");
                case Commodities.ALPHA_CORE ->
                        tooltip.addPara("Alpha Core assigned. Reduces demand by %s, increases supply by %s, reduces hazard rating by %s, and upkeep by %s.", opad, h, "1 unit", "1 unit", "5%", "25%");
                case OMEGA_CORE ->
                        tooltip.addPara("Omega Core assigned. Reduces demand by %s, increases supply by %s, reduces hazard rating by %s, and upkeep by %s.", opad, h, "1 unit", "2 units", "10%", "25%");
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

            if (Items.MANTLE_BORE.equals(special.getId())) {
                tooltip.addPara("Installed Special Item: %s", opad, h, "Mantle Bore (+2 Supply, -20% Hazard Rating, +5 Growth)");
                return true;
            } else if (Items.PLASMA_DYNAMO.equals(special.getId())) {
                tooltip.addPara("Installed Special Item: %s", opad, h, "Plasma Dynamo (+1 Supply, +2 Volatiles, -20% Hazard Rating, +15 Growth if Habitable)");
                return true;
            }
        }
        return super.addNonAICoreInstalledItems(mode, tooltip, expanded);
    }
}