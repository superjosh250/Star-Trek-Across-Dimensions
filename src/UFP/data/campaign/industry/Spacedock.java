package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.Spaceport;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class Spacedock extends Spaceport implements MarketImmigrationModifier {

    // Track multi-level improvement state (1 to 4)
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

    private int getCadetsSupplyUnit(int marketSize) {
        if (marketSize <= 4) return 1;
        if (marketSize <= 7) return 2;
        if (marketSize <= 9) return 3;
        return 4;
    }

    @Override
    public void apply() {
        super.apply(true);

        int size = market.getSize();
        int demandUnit = getCustomDemandUnit(size);
        int supplyUnit = getCustomSupplyUnit(size);
        int cadetsSupply = getCadetsSupplyUnit(size);

        // Calculate supply bonuses from AI cores and improvements
        int extraSupply = 0;
        if (aiCoreId != null) {
            if (Commodities.ALPHA_CORE.equals(aiCoreId)) extraSupply += 1;
            if (Commodities.OMEGA_CORE.equals(aiCoreId)) extraSupply += 2;
        }
        if (improvementLevel >= 2) {
            extraSupply += 1;
        }

        // --- DEMAND SETUP ---
        demand(Commodities.SUPPLIES, demandUnit);
        demand(Commodities.SHIPS, demandUnit);

        // Extra Fuel demand (+1) if standard Mining is installed
        int fuelDemand = demandUnit + (market.hasIndustry(Industries.MINING) ? 1 : 0);
        demand(Commodities.FUEL, fuelDemand);

        // Dilithium demand only when Advanced Mining is installed on the planet
        if (market.hasIndustry(DimensionsCrossedIDS.MINING_ADVANCED)) {
            demand(DimensionsCrossedIDS.DILITHIUM, demandUnit);
        } else {
            demand(DimensionsCrossedIDS.DILITHIUM, 0);
        }

        // --- SUPPLY SETUP ---
        supply(Commodities.CREW, supplyUnit + extraSupply);
        supply(Commodities.MARINES, supplyUnit + extraSupply);
        supply(DimensionsCrossedIDS.CADETS, Math.min(4, cadetsSupply + extraSupply));

        // --- AI CORE MODIFIERS ---
        if (aiCoreId != null) {
            switch (aiCoreId) {
                case Commodities.GAMMA_CORE:
                    market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                            .modifyPercent(getModId(4), 5f, "Gamma Core");
                    break;
                case Commodities.ALPHA_CORE:
                    market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                            .modifyPercent(getModId(4), 5f, "Alpha Core");
                    getUpkeep().modifyMult(getModId(4), 0.75f, "Alpha Core (-25% upkeep)");
                    break;
                case Commodities.OMEGA_CORE:
                    // Multiplicative stacking bonus for Omega Core (1.25x combat fleet size)
                    market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                            .modifyMult(getModId() + "_omega_fleet_size", 1.25f, "Omega Core (Spacedock Stacking Bonus)");
                    getUpkeep().modifyMult(getModId(4), 0.75f, "Omega Core (-25% upkeep)");
                    break;
            }
        }

        // --- SPECIAL ITEM: DEALMAKER HOLOSUITE ---
        if (special != null && Items.DEALMAKER_HOLOSUITE.equals(special.getId())) {
            market.getAccessibilityMod().modifyFlat(getModId(5), 0.50f, "Dealmaker Holosuite");
            market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(5), 0.105f, "Dealmaker Holosuite");
            market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).modifyFlat(getModId(5), 0.105f, "Dealmaker Holosuite");
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyPercent(getModId(5), 15f, "Dealmaker Holosuite");
            market.getIncomeMult().modifyPercent(getModId(5), 2.5f, "Dealmaker Holosuite");
        }

        // --- IMPROVEMENT TIERS ---
        if (improvementLevel >= 1) {
            getUpkeep().modifyMult(getModId() + "_imp1", 0.95f, "Spacedock Improvement Tier I");
        }
        if (improvementLevel >= 3) {
            market.getAccessibilityMod().modifyFlat(getModId() + "_imp3", 0.125f, "Spacedock Improvement Tier III");
        }
        if (improvementLevel >= 4) {
            getUpkeep().modifyMult(getModId() + "_imp4", 0.95f, "Spacedock Improvement Tier IV");
            market.getHazard().modifyFlat(getModId() + "_imp4", -0.05f, "Spacedock Improvement Tier IV");
        }

        // --- ORBITAL STATION INTERACTION ---
        if (market.hasIndustry(DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK)) {
            float baseDefenses = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0f);
            if (baseDefenses > 10000f) {
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyPercent(getModId(6), 60f, "Spacedock Orbital Station Synergy");
            } else {
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyFlat(getModId(6), 10000f, "Spacedock Orbital Station Synergy");
            }
        }

        market.setHasSpaceport(true);
        market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply() {
        super.unapply();

        // Standard ID Cleanups
        market.getAccessibilityMod().unmodifyFlat(getModId(5));
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_imp3");

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId(5));
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).unmodifyFlat(getModId(5));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyPercent(getModId(5));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyPercent(getModId(6));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId(6));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyPercent(getModId(4));

        // Clean up Omega Core fleet size multiplier
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_omega_fleet_size");

        market.getIncomeMult().unmodifyPercent(getModId(5));

        getUpkeep().unmodifyMult(getModId(4));
        getUpkeep().unmodifyMult(getModId() + "_imp1");
        getUpkeep().unmodifyMult(getModId() + "_imp4");
        market.getHazard().unmodifyFlat(getModId() + "_imp4");
    }

    // Growth Rate Modifications
    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        super.modifyIncoming(market, incoming);

        float growthBonus = 0f;
        if (special != null && Items.DEALMAKER_HOLOSUITE.equals(special.getId())) {
            growthBonus += 2.5f; // +2.5% Population Growth
        }
        if (improvementLevel >= 4) {
            growthBonus += 10f; // +10 Population Growth
        }

        if (growthBonus > 0f) {
            incoming.getWeight().modifyFlat(getModId(7), growthBonus, getNameForModifier());
        }
    }

    // --- ITEM INSTALLATION FIXES ---
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
        if (Items.DEALMAKER_HOLOSUITE.equals(data.getId())) return true;
        return super.wantsToUseSpecialItem(data);
    }

    // --- IMPROVEMENT TIER MANAGEMENT ---
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

    // --- TOOLTIP OVERRIDES ---
    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (special != null && Items.DEALMAKER_HOLOSUITE.equals(special.getId())) {
            tooltip.addSpacer(opad);
            tooltip.addPara("Installed Item: %s", opad, h, "Dealmaker Holosuite (+50% Accessibility, +10.5% Officer/Admin Prob., +2.5% Growth, +15% Defenses, +2.5% Income)");
        }

        if (market.hasIndustry(DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK)) {
            tooltip.addSpacer(opad);
            tooltip.addPara("Orbital Station Active: %s", opad, h, "Enhanced ground defenses provided by Spacedock Station.");
        }
    }

    @Override
    public void addAICoreSection(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (coreId != null) {
            switch (coreId) {
                case Commodities.GAMMA_CORE ->
                        tooltip.addPara("Gamma Core assigned. Reduces demand by %s and increases fleet size by %s.", opad, h, "1 unit", "5%");
                case Commodities.BETA_CORE ->
                        tooltip.addPara("Beta Core assigned. Reduces demand by %s.", opad, h, "1 unit");
                case Commodities.ALPHA_CORE ->
                        tooltip.addPara("Alpha Core assigned. Reduces demand by %s, increases supply by %s, fleet size by %s, and reduces upkeep by %s.", opad, h, "1 unit", "1 unit", "5%", "25%");
                case Commodities.OMEGA_CORE ->
                        tooltip.addPara("Omega Core assigned. Reduces demand by %s, increases supply by %s, fleet size by %s, and reduces upkeep by %s.", opad, h, "1 unit", "2 units", "25%", "25%");
                default -> super.addAICoreSection(tooltip, coreId, mode);
            }
        } else {
            super.addAICoreSection(tooltip, coreId, mode);
        }
    }
}