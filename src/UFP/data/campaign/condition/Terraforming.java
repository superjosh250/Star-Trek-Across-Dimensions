package UFP.data.campaign.condition;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class Terraforming extends BaseHazardCondition implements MarketImmigrationModifier {

    public static final float ACCESS_BONUS = 0.10f;
    public static final float HAZARD_PENALTY = 0.10f;
    public static final float GROWTH_FLAT = 12f;

    @Override
    public void apply(String id) {
        super.apply(id);

        market.getAccessibilityMod().modifyPercent(id, ACCESS_BONUS * 100f, "Terraforming");
        market.getHazard().modifyFlat(id, -HAZARD_PENALTY, "Terraforming");
        market.addImmigrationModifier(this);
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        market.getAccessibilityMod().unmodify(id);
        market.getHazard().unmodify(id);
        market.removeImmigrationModifier(this);
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat(getModId(), GROWTH_FLAT, "Terraforming");
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        tooltip.addPara("%s accessibility", 10f, Misc.getHighlightColor(), "+10%");
        tooltip.addPara("%s hazard rating", 10f, Misc.getHighlightColor(), "-10%");
        tooltip.addPara("%s population growth", 10f, Misc.getHighlightColor(), "+12");
    }
}
