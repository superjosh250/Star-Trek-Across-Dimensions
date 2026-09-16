package UFP.data.campaign.industry;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class PlanetSweeper extends BaseIndustry {

    @Override
    public void apply() {
        super.apply(true);

        if (isFunctional() && market.hasCondition(Conditions.POLLUTION)) {
            market.removeCondition(Conditions.POLLUTION);
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        return true;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}
