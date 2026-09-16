package UFP.data.campaign.condition;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;

public class Starbase extends BaseMarketConditionPlugin implements MarketImmigrationModifier {

    /**
     * Helper method to return the modifier display name from the condition spec.
     */
    public String getNameForModifier() {
        if (condition != null && condition.getSpec() != null) {
            return condition.getSpec().getName();
        }
        return "Starbase";
    }

    @Override
    public void apply(String id) {
        super.apply(id);

        // 1. Ensure required conditions exist on the market
        if (!market.hasCondition(Conditions.HABITABLE)) {
            market.addCondition(Conditions.HABITABLE);
        }
        if (!market.hasCondition(Conditions.MILD_CLIMATE)) {
            market.addCondition(Conditions.MILD_CLIMATE);
        }
        if (!market.hasCondition(Conditions.ESTABLISHED_POLITY)) {
            market.addCondition(Conditions.ESTABLISHED_POLITY);
        }

        // 2. Remove Pather Cells if active
        if (market.hasCondition(Conditions.PATHER_CELLS)) {
            market.removeCondition(Conditions.PATHER_CELLS);
        }

        // 3. -50% Hazard Rating
        market.getHazard().modifyFlat(getModId(), -0.50f, getNameForModifier());

        // 4. +1 Stability
        market.getStability().modifyFlat(getModId(), 1f, getNameForModifier());

        // 5. Commodity Supply Bonuses (+1 Food, +1 Dilithium) via getAvailableStat()
        CommodityOnMarketAPI food = market.getCommodityData(Commodities.FOOD);
        if (food != null) {
            food.getAvailableStat().modifyFlat(getModId(), 1, getNameForModifier());
        }

        CommodityOnMarketAPI dilithium = market.getCommodityData(DimensionsCrossedIDS.DILITHIUM);
        if (dilithium != null) {
            dilithium.getAvailableStat().modifyFlat(getModId(), 1, getNameForModifier());
        }

        // 6. Register transient immigration modifier for population growth
        market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);

        market.getHazard().unmodifyFlat(getModId());
        market.getStability().unmodifyFlat(getModId());

        CommodityOnMarketAPI food = market.getCommodityData(Commodities.FOOD);
        if (food != null) {
            food.getAvailableStat().unmodifyFlat(getModId());
        }

        CommodityOnMarketAPI dilithium = market.getCommodityData(DimensionsCrossedIDS.DILITHIUM);
        if (dilithium != null) {
            dilithium.getAvailableStat().unmodifyFlat(getModId());
        }

        market.removeTransientImmigrationModifier(this);
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        float popGrowth = 5f; // Base +5 Population Growth

        // +1 Population Growth if accessibility is not negative (>= 0)
        // Uses StatBonus.computeEffective(0f) from StatBonus API
        if (market.getAccessibilityMod().computeEffective(0f) >= 0f) {
            popGrowth += 1f;
        }

        // Modifies PopulationComposition weight via getWeight()
        incoming.getWeight().modifyFlat(getModId(), popGrowth, getNameForModifier());
    }
}