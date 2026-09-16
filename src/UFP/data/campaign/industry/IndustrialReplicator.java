package UFP.data.campaign.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.Refining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.IntervalUtil;

public class IndustrialReplicator extends Refining {

    public static final float BASE_INCOME = 25000f;

    protected int improvementLevel = 0;
    protected final IntervalUtil omegaMarketTimer = new IntervalUtil(30f, 30f);

    @Override
    public void apply() {
        super.apply(true);

        if (!isFunctional()) return;

        // 1. Base Income
        getIncome().modifyFlat(getModId(0), BASE_INCOME, getNameForModifier());

        // 2. Improvement Levels Mechanics
        int improvementSupplyBonus = 0;

        if (improvementLevel >= 1) {
            // Tier 1: Reduce hazard rating by 12.5%
            market.getHazard().modifyFlat(getModId(0), -0.125f, getNameForModifier() + " (Replicator Improvement)");
        }
        if (improvementLevel >= 2) {
            // Tier 2: Increase accessibility by +12.5%
            market.getAccessibilityMod().modifyFlat(getModId(0), 0.125f, getNameForModifier() + " (Replicator Improvement)");
        }
        if (improvementLevel >= 3) {
            // Tier 3: Improve supply by +1
            improvementSupplyBonus += 1;
        }
        if (improvementLevel >= 4) {
            // Tier 4: Produce 1 unit of OMEGA_CORE
            supply(Commodities.OMEGA_CORE, 1);
        }

        // 3. AI Core Installation Modifiers
        int aiDemandReduction = 0;
        int aiSupplyBonus = 0;
        float incomePercentBonus = 0f;
        float upkeepMult = 1.0f;

        if (aiCoreId != null) {
            switch (aiCoreId) {
                case Commodities.BETA_CORE -> {
                    aiDemandReduction = 1;
                }
                case Commodities.GAMMA_CORE -> {
                    aiDemandReduction = 1;
                    incomePercentBonus = 20f;
                }
                case Commodities.ALPHA_CORE -> {
                    aiDemandReduction = 1;
                    aiSupplyBonus = 1;
                    incomePercentBonus = 20f;
                }
                case Commodities.OMEGA_CORE -> {
                    aiDemandReduction = 1;
                    aiSupplyBonus = 2;
                    incomePercentBonus = 25f;
                    upkeepMult = 0.875f; // -12.5% upkeep cost
                }
            }
        }

        if (incomePercentBonus > 0f) {
            getIncome().modifyPercent(getModId(0), incomePercentBonus, getNameForModifier() + " (AI Core)");
        }

        if (upkeepMult < 1.0f) {
            getUpkeep().modifyMult(getModId(0), upkeepMult, getNameForModifier() + " (AI Core)");
        }

        // 4. Market Size Supply Bonus (M size 6 -> +1, size 8 -> +1, size 10 -> +1)
        int size = market.getSize();
        int sizeSupplyBonus = 0;
        if (size >= 6) sizeSupplyBonus += 1;
        if (size >= 8) sizeSupplyBonus += 1;
        if (size >= 10) sizeSupplyBonus += 1;

        int totalExtraSupply = sizeSupplyBonus + improvementSupplyBonus + aiSupplyBonus;

        // 5. Calculate Demand & Supply Conversion Chains (25% Stockpile, Max Demand 7)

        // --- ORGANICS -> FOOD (1 Organics = 2 Food units [+1 supply level]) ---
        int organicsDemand = calculateStockpileDemand(Commodities.ORGANICS, aiDemandReduction);
        if (organicsDemand > 0) {
            demand(Commodities.ORGANICS, organicsDemand);
            supply(Commodities.FOOD, organicsDemand + 1 + totalExtraSupply);
        }

        // --- ORE -> METALS (1:1 Ratio) ---
        int oreDemand = calculateStockpileDemand(Commodities.ORE, aiDemandReduction);
        if (oreDemand > 0) {
            demand(Commodities.ORE, oreDemand);
            supply(Commodities.METALS, oreDemand + totalExtraSupply);
        }

        // --- RARE_ORE -> RARE_METALS (1:1 Ratio) ---
        int rareOreDemand = calculateStockpileDemand(Commodities.RARE_ORE, aiDemandReduction);
        if (rareOreDemand > 0) {
            demand(Commodities.RARE_ORE, rareOreDemand);
            supply(Commodities.RARE_METALS, rareOreDemand + totalExtraSupply);
        }

        // --- SUPPLIES -> DOMESTIC_GOODS & LUXURY_GOODS (1:1 Ratio) ---
        int suppliesDemand = calculateStockpileDemand(Commodities.SUPPLIES, aiDemandReduction);
        if (suppliesDemand > 0) {
            demand(Commodities.SUPPLIES, suppliesDemand);
            supply(Commodities.DOMESTIC_GOODS, suppliesDemand + totalExtraSupply);
            supply(Commodities.LUXURY_GOODS, suppliesDemand + totalExtraSupply);
        }

        // --- METALS -> HEAVY_MACHINERY (1:1 Ratio) ---
        int metalsDemand = calculateStockpileDemand(Commodities.METALS, aiDemandReduction);
        if (metalsDemand > 0) {
            demand(Commodities.METALS, metalsDemand);
            supply(Commodities.HEAVY_MACHINERY, metalsDemand + totalExtraSupply);
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Global.getSector().getEconomy().isSimMode() || !isFunctional()) return;

        // Tier 4 Improvement: Inject 1 purchasable OMEGA_CORE into Open Market cargo every month
        if (improvementLevel >= 4) {
            float days = Global.getSector().getClock().convertToDays(amount);
            omegaMarketTimer.advance(days);
            if (omegaMarketTimer.intervalElapsed()) {
                SubmarketAPI openMarket = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
                if (openMarket != null && openMarket.getCargo() != null) {
                    openMarket.getCargo().addCommodity(Commodities.OMEGA_CORE, 1);
                }
            }
        }
    }

    /**
     * Calculates demand from 25% of current market stockpile (capped at 7 max demand),
     * then applies AI Core demand reductions.
     */
    private int calculateStockpileDemand(String commodityId, int aiDemandReduction) {
        CommodityOnMarketAPI com = market.getCommodityData(commodityId);
        if (com == null) return 0;

        float stockpile = com.getStockpile();
        float usable25Percent = stockpile * 0.25f;

        if (usable25Percent <= 0f) return 0;

        // Converts 25% raw stockpile quantity into Starsector's logarithmic commodity level scale
        int baseDemand = (int) Math.floor(Math.log10(usable25Percent));

        // Cap base demand to maximum of 7 units
        baseDemand = Math.max(0, Math.min(7, baseDemand));

        // Apply AI Core reduction (-1 demand)
        return Math.max(0, baseDemand - aiDemandReduction);
    }

    @Override
    public void unapply() {
        super.unapply();
        getIncome().unmodifyFlat(getModId(0));
        getIncome().unmodifyPercent(getModId(0));
        getUpkeep().unmodifyMult(getModId(0));
        market.getHazard().unmodifyFlat(getModId(0));
        market.getAccessibilityMod().unmodifyFlat(getModId(0));
    }

    public int getImprovementLevel() {
        return improvementLevel;
    }

    public void setImprovementLevel(int improvementLevel) {
        this.improvementLevel = Math.max(0, Math.min(4, improvementLevel));
    }
}