package BORG.data.campaign.industry;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class AssimilationMatrix extends BaseIndustry {

    private static final float TICK_DAYS = 30f;

    private static final float ASSIMILATION_GAIN = 0.15f; // +15% per tick
    private static final float HOST_LOSS = 0.10f;         // -10% per tick

    private static final float TAKEOVER_THRESHOLD = 0.60f;

    private static final String MEM_TIMER = "$borg_assimilation_days";

    @Override
    public void apply() {
        super.apply(true);

        if (!isFunctional()) return;

        // Assimilate ideological resistance on host market
        if (market.hasCondition(Conditions.PATHER_CELLS)) {
            market.removeCondition(Conditions.PATHER_CELLS);
        }
        if (market.hasCondition(Conditions.LUDDIC_MAJORITY)) {
            market.removeCondition(Conditions.LUDDIC_MAJORITY);
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (market == null || !isFunctional()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        float elapsed = market.getMemoryWithoutUpdate().getFloat(MEM_TIMER) + days;

        if (elapsed < TICK_DAYS) {
            market.getMemoryWithoutUpdate().set(MEM_TIMER, elapsed);
            return;
        }

        elapsed -= TICK_DAYS;
        market.getMemoryWithoutUpdate().set(MEM_TIMER, elapsed);

        processSystemAssimilation();
    }

    // ======================================================
    // Core logic
    // ======================================================

    private void processSystemAssimilation() {
        StarSystemAPI system = market.getStarSystem();
        if (system == null) return;

        String assimilatorFaction = market.getFactionId();

        for (MarketAPI other : Global.getSector().getEconomy().getMarketsCopy()) {
            if (other == market) continue;
            if (other.getStarSystem() != system) continue;
            if (other.getFactionId().equals(assimilatorFaction)) continue;

            applyAssimilationPressure(other, assimilatorFaction);
        }
    }

    private void applyAssimilationPressure(MarketAPI target, String assimilatorFaction) {
        String hostFaction = target.getFactionId();

        float assimilatorShare = getFactionShare(target, assimilatorFaction);
        float hostShare = getFactionShare(target, hostFaction);

        assimilatorShare += ASSIMILATION_GAIN;
        hostShare -= HOST_LOSS;

        assimilatorShare = clamp01(assimilatorShare);
        hostShare = clamp01(hostShare);

        setFactionShare(target, assimilatorFaction, assimilatorShare);
        setFactionShare(target, hostFaction, hostShare);

        // Check for takeover
        if (assimilatorShare >= TAKEOVER_THRESHOLD &&
                assimilatorShare >= hostShare + TAKEOVER_THRESHOLD) {

            convertMarket(target, assimilatorFaction);
        }
    }

    // ======================================================
    // Market conversion
    // ======================================================

    private void convertMarket(MarketAPI target, String newFaction) {
        target.setFactionId(newFaction);

        // Remove hostile ideological remnants
        target.removeCondition(Conditions.PATHER_CELLS);
        target.removeCondition(Conditions.LUDDIC_MAJORITY);

        // Occupation shock
        target.getStability().modifyFlat(
                getModId(),
                -2f,
                "Forced Assimilation"
        );

        // Reset population shares
        target.getMemoryWithoutUpdate().clear();
    }

    // ======================================================
    // Population share helpers
    // ======================================================

    private float getFactionShare(MarketAPI market, String factionId) {
        String key = "$popShare_" + factionId;
        return market.getMemoryWithoutUpdate().getFloat(key);
    }

    private void setFactionShare(MarketAPI market, String factionId, float value) {
        String key = "$popShare_" + factionId;
        market.getMemoryWithoutUpdate().set(key, clamp01(value));
    }

    private float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
