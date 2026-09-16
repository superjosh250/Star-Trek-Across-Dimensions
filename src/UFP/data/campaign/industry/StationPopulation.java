package UFP.data.campaign.industry;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;

public class StationPopulation extends SocietyandInfanstructure {

    // --- Capped Sizing & Units ---
    public int getCustomSupplyUnit(int marketSize) {
        int size = Math.min(marketSize, 5);
        return switch (size) {
            case 3 -> 3;
            case 4, 5 -> 4;
            default -> Math.max(1, size);
        };
    }

    public int getCustomDemandUnit(int marketSize) {
        int size = Math.min(marketSize, 5);
        return switch (size) {
            case 3, 4, 5 -> 3;
            default -> Math.max(1, size);
        };
    }

    public static int getMaxIndustriesForSize(int size) {
        int clampedSize = Math.min(size, 5);
        if (clampedSize <= 3) return 1;
        if (clampedSize == 4) return 2;
        return 5; // Capped at maximum 5 industry slots available
    }

    @Override
    public int getMaxIndustries() {
        return getMaxIndustriesForSize(market.getSize());
    }

    @Override
    public void apply() {
        if (market == null) return;

        // Always ensure the Starbase Habitat market condition is installed
        if (!market.hasCondition(DimensionsCrossedIDS.STARBASE_HABITAT)) {
            market.addCondition(DimensionsCrossedIDS.STARBASE_HABITAT);
        }

        // Run base logic (AI Cores, Dealmaker Holosuite, Stockpiles, etc.)
        super.apply();

        int size = Math.min(market.getSize(), 5);
        int supplyUnit = getCustomSupplyUnit(size);
        int demandUnit = getCustomDemandUnit(size);

        // --- DEMAND SETUP ---
        demand(Commodities.FOOD, demandUnit);
        demand(Commodities.SUPPLIES, demandUnit);
        demand(Commodities.LUXURY_GOODS, 0);
        demand(Commodities.DOMESTIC_GOODS, 0);

        // --- SUPPLY SETUP ---
        int bonusSupply = 0;
        if (aiCoreId != null) {
            switch (aiCoreId) {
                case Commodities.ALPHA_CORE -> bonusSupply += 1;
                case Commodities.OMEGA_CORE -> bonusSupply += 2;
            }
        }
        if (special != null && com.fs.starfarer.api.impl.campaign.ids.Items.DEALMAKER_HOLOSUITE.equals(special.getId())) {
            bonusSupply += 1;
        }

        String[] standardSupplies = {
                Commodities.CREW,
                DimensionsCrossedIDS.CIVILIAN,
                DimensionsCrossedIDS.CADETS,
                Commodities.SUPPLIES
        };
        int finalSupplyValue = supplyUnit + bonusSupply;
        for (String commodity : standardSupplies) {
            supply(commodity, finalSupplyValue);
        }

        // --- BASE STATS ---
        market.getStability().modifyFlat(getModId() + "_base_stab", 4f, getNameForModifier());
        market.getAccessibilityMod().modifyFlat(getModId() + "_base_acc", 0.25f, getNameForModifier());

        int maxIndustries = getMaxIndustries();
        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES)
                .modifyFlat(getModId() + "_max_ind", maxIndustries, getNameForModifier());

        // --- CONDITIONAL INDUSTRY BONUSES ---
        if (market.hasIndustry(Industries.COMMERCE)) {
            market.getIncomeMult().modifyPercent(getModId() + "_commerce_income", 20f, "Commerce");
        } else {
            market.getIncomeMult().unmodifyPercent(getModId() + "_commerce_income");
        }

        if (market.hasIndustry(Industries.WAYSTATION) || market.hasIndustry(DimensionsCrossedIDS.WAYSTATION)) {
            market.getAccessibilityMod().modifyFlat(getModId() + "_waystation_acc", 0.30f, "Waystation");
        } else {
            market.getAccessibilityMod().unmodifyFlat(getModId() + "_waystation_acc");
        }

        if (market.hasIndustry(DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK)) {
            market.getStability().modifyFlat(getModId() + "_spacedock_stab", 2f, "Orbital Spacedock");
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                    .modifyFlat(getModId() + "_spacedock_def", 10000f, "Orbital Spacedock");
        } else {
            market.getStability().unmodifyFlat(getModId() + "_spacedock_stab");
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                    .unmodifyFlat(getModId() + "_spacedock_def");
        }

        if (market.hasIndustry(Industries.HEAVYINDUSTRY) ||
                market.hasIndustry(Industries.ORBITALWORKS) ||
                market.hasIndustry(DimensionsCrossedIDS.DRYDOCK) ||
                market.hasIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED)) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyPercent(getModId() + "_drydock_fleet", 25f, "Heavy Industry / Drydock");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .unmodifyPercent(getModId() + "_drydock_fleet");
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null) return;

        market.getStability().unmodifyFlat(getModId() + "_base_stab");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_base_acc");

        market.getIncomeMult().unmodifyPercent(getModId() + "_commerce_income");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_waystation_acc");

        market.getStability().unmodifyFlat(getModId() + "_spacedock_stab");
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId() + "_spacedock_def");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyPercent(getModId() + "_drydock_fleet");

        // Clean up growth modifiers on unapply
        if (market.getIncoming() != null && market.getIncoming().getWeight() != null) {
            market.getIncoming().getWeight().unmodifyFlat(getModId() + "_base_growth");
            market.getIncoming().getWeight().unmodifyMult(getModId() + "_max_size_cap");
        }
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        super.modifyIncoming(market, incoming);

        if (market == null || incoming == null || incoming.getWeight() == null) return;

        // Normal Growth allowed for Size 3 and 4
        if (market.getSize() < 5) {
            incoming.getWeight().unmodifyMult(getModId() + "_max_size_cap");
            incoming.getWeight().modifyFlat(getModId() + "_base_growth", 4f, getNameForModifier());
        } else { // Hard cap at Size 5
            incoming.getWeight().unmodifyFlat(getModId() + "_base_growth");
            incoming.getWeight().modifyMult(getModId() + "_max_size_cap", 0f, "Maximum station capacity");
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (market == null) return;

        // AUTO-REPAIR FOR EXISTING SAVES:
        // Demote stations that previously grew to Size 6+ back to Size 5
        if (market.getSize() > 5) {
            market.setSize(5);
        }

        // Clean up legacy flat growth and freeze growth immediately upon save load
        if (market.getSize() >= 5 && market.getIncoming() != null && market.getIncoming().getWeight() != null) {
            market.getIncoming().getWeight().unmodifyFlat(getModId() + "_base_growth");
            market.getIncoming().getWeight().modifyMult(getModId() + "_max_size_cap", 0f, "Maximum station capacity");
        }
    }
}