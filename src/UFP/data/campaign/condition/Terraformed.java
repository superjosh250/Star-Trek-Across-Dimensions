package UFP.data.campaign.condition;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class Terraformed extends BaseHazardCondition implements MarketImmigrationModifier {

    public static final float ACCESS_BONUS = 0.20f;
    public static final float HAZARD_PENALTY = 0.20f;
    public static final float GROWTH_FLAT = 10f;

    @Override
    public void apply(String id) {
        super.apply(id);

        market.getAccessibilityMod().modifyPercent(id, ACCESS_BONUS * 100f, "Terraformed");
        market.getHazard().modifyFlat(id, -HAZARD_PENALTY, "Terraformed");
        market.addImmigrationModifier(this);

        // Add organics trace condition if not present
        if (!market.hasCondition(Conditions.ORGANICS_TRACE)) {
            market.addCondition(Conditions.ORGANICS_TRACE);
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        market.getAccessibilityMod().unmodify(id);
        market.getHazard().unmodify(id);
        market.removeImmigrationModifier(this);
        if (market.hasCondition(Conditions.ORGANICS_TRACE)) {
            market.removeCondition(Conditions.ORGANICS_TRACE);
        }
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat(getModId(), GROWTH_FLAT, "Terraformed");
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        tooltip.addPara("%s accessibility", 10f, Misc.getHighlightColor(), "+" + (int) (ACCESS_BONUS * 100) + "%");
        tooltip.addPara("%s hazard rating", 10f, Misc.getHighlightColor(), "-" + (int) (HAZARD_PENALTY * 100) + "%");
        tooltip.addPara("%s population growth", 10f, Misc.getHighlightColor(), "+" + (int) GROWTH_FLAT);
    }
}
