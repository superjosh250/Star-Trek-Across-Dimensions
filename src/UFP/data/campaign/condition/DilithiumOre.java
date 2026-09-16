package UFP.data.campaign.condition;

import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class DilithiumOre extends ResourceDepositsCondition {

    public static final String COMMODITY_ID = "dilithium_ore";
    public static final String CONDITION_ID = "dilithium_ore";
    public static final String TRACE_ID = "dilithium_ore_trace";
    public static final String ABUNDANT_ID = "dilithium_ore_abundant";

    // Register the condition in the shared maps used by ResourceDepositsCondition
    static {
        // Map condition ID to commodity ID
        COMMODITY.put(CONDITION_ID, COMMODITY_ID);
        COMMODITY.put(TRACE_ID, COMMODITY_ID);
        COMMODITY.put(ABUNDANT_ID, COMMODITY_ID);

        // Map condition ID to production modifier (0 = Moderate/Standard)
        MODIFIER.put(CONDITION_ID, 0);
        MODIFIER.put(TRACE_ID, -1);
        MODIFIER.put(ABUNDANT_ID, 2);

        // Map commodity to the Industry that parses it (Standard Mining usually)
        INDUSTRY.put(COMMODITY_ID, Industries.MINING);

        // Base modifier for the commodity
        BASE_MODIFIER.put(COMMODITY_ID, 0);
    }

}
