package BORG.data.campaign.industry;

import java.awt.Color;

import BORG.data.campaign.ids.BorgIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.Spaceport;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

public class TerminusAlpha extends BaseIndustry {

    // -----------------------------------------------------------------
    // POPULATION GROWTH HOOKS
    // -----------------------------------------------------------------
    public float getPopulationGrowthBonus() {
        float bonus = 2.0f; // Base population growth = 2

        // Biofactory Embryo (+2 Growth)
        if (special != null && Items.BIOFACTORY_EMBRYO.equals(special.getId())) {
            bonus += 2.0f;
        }

        // Omega Core (+2 Growth)
        if (Commodities.OMEGA_CORE.equals(getAICoreId())) {
            bonus += 2.0f;
        }

        return bonus;
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (!isFunctional()) return;

        float bonus = getPopulationGrowthBonus();
        if (bonus > 0 && incoming != null && incoming.getWeight() != null) {
            incoming.getWeight().modifyFlat(getModId(), bonus, getNameForModifier());
        }
    }

    @Override
    public void apply() {
        super.apply(!getSpec().hasTag(Industries.TAG_PATROL));

        if (market == null) return;
        int size = market.getSize();

        // -----------------------------------------------------------------
        // 1. BASE STATS
        // -----------------------------------------------------------------
        market.getAccessibilityMod().modifyFlat(getModId(0), 0.50f, "Terminus Alpha Base");
        market.getHazard().modifyFlat(getModId(1), 0.125f, "Terminus Alpha Base");

        int demandReduction = 0;
        int extraSupply = 0;
        float extraAccessibility = 0f;
        float fleetSizeBonus = 0f;

        // -----------------------------------------------------------------
        // 2. IMPROVEMENT TIERS (MAX 4)
        // -----------------------------------------------------------------
        int improveTier = getImproveProductionBonus();
        if (improveTier >= 1) demandReduction += 1; // Tier 1: -1 Demand
        if (improveTier >= 2) {
            market.getStability().modifyFlat(getModId(3), 1f, "Terminus Alpha Improvements"); // Tier 2: +1 Stability
        } else {
            market.getStability().unmodifyFlat(getModId(3));
        }
        if (improveTier >= 3) demandReduction += 1; // Tier 3: -1 Demand
        if (improveTier >= 4) extraAccessibility += 0.25f; // Tier 4: +25% Accessibility

        // -----------------------------------------------------------------
        // 3. AI CORES (STANDARD + OMEGA CORE)
        // -----------------------------------------------------------------
        String coreId = getAICoreId();
        if (Commodities.OMEGA_CORE.equals(coreId)) {
            demandReduction += 1;
            extraSupply += 1;
            getUpkeep().modifyMult(getModId(5), 0.50f, "Omega Core");
            extraAccessibility += 0.125f;
            getIncome().modifyMult(getModId(6), 1.075f, "Omega Core");
        } else {
            getIncome().unmodifyMult(getModId(6));
            applyStandardAICoreModifiers(coreId);
        }

        // -----------------------------------------------------------------
        // 4. SPECIAL ITEMS (BIOFACTORY EMBRYO)
        // -----------------------------------------------------------------
        if (special != null && Items.BIOFACTORY_EMBRYO.equals(special.getId())) {
            extraSupply += 1;
            demandReduction += 1;
            fleetSizeBonus += 0.125f; // +12.5% Fleet Size
        }

        // -----------------------------------------------------------------
        // 5. DEMAND & SUPPLY CALCULATIONS
        // -----------------------------------------------------------------
        int baseDemand = getBaseDemandForSize(size);
        int baseSupply = getBaseSupplyForSize(size);

        int finalDemand = Math.max(0, baseDemand - demandReduction);
        int finalSupply = baseSupply + extraSupply;

        // Apply Standard Demands
        demand(Commodities.FUEL, finalDemand);
        demand(BorgIDS.ASSIMILATED_CIVILIANS, finalDemand);
        demand(Commodities.SUPPLIES, finalDemand);

        // Dilithium Demand Logic
        int dilithiumDemand = getDilithiumDemandForSize(size);
        if (Items.BIOFACTORY_EMBRYO.equals(special != null ? special.getId() : null) || Commodities.OMEGA_CORE.equals(coreId)) {
            dilithiumDemand = Math.max(0, dilithiumDemand - 1);
        }
        demand(DimensionsCrossedIDS.DILITHIUM, dilithiumDemand);

        // Apply Outputs
        supply(BorgIDS.DRONES, finalSupply);
        supply(Commodities.CREW, finalSupply);

        // -----------------------------------------------------------------
        // 6. APPLY ACCESSIBILITY & FLEET MODIFIERS
        // -----------------------------------------------------------------
        if (extraAccessibility > 0f) {
            market.getAccessibilityMod().modifyFlat(getModId(8), extraAccessibility, "Terminus Alpha Upgrades");
        } else {
            market.getAccessibilityMod().unmodifyFlat(getModId(8));
        }

        if (fleetSizeBonus > 0f) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(getModId(9), fleetSizeBonus, "Biofactory Embryo");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(getModId(9));
        }

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }
    }

    private void applyStandardAICoreModifiers(String coreId) {
        if (Commodities.ALPHA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(5), 0.75f, "Alpha Core");
        } else if (Commodities.BETA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(5), 0.75f, "Beta Core");
        } else if (Commodities.GAMMA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(5), 0.75f, "Gamma Core");
        } else {
            getUpkeep().unmodifyMult(getModId(5));
        }
    }

    protected Pair<String, Integer> getBaseDeficit(String... commodities) {
        Pair<String, Integer> deficit = super.getMaxDeficit(commodities);
        if (deficit != null && Commodities.SUPPLIES.equals(deficit.one)) {
            return new Pair<>(Commodities.SUPPLIES, 0);
        }
        return deficit;
    }

    @Override
    public void unapply() {
        super.unapply();
        if (market == null) return;

        market.getAccessibilityMod().unmodifyFlat(getModId(0));
        market.getHazard().unmodifyFlat(getModId(1));
        market.getStability().unmodifyFlat(getModId(3));
        getUpkeep().unmodifyMult(getModId(5));
        getIncome().unmodifyMult(getModId(6));
        market.getAccessibilityMod().unmodifyFlat(getModId(8));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(getModId(9));
    }

    @Override
    public boolean isAvailableToBuild() {
        if (market == null) return false;

        for (Industry ind : market.getIndustries()) {
            if (!ind.isFunctional()) continue;

            String indId = ind.getId();

            if (Industries.SPACEPORT.equals(indId) ||
                    BorgIDS.ASSIMILATED_SPACEPORT.equals(indId) ||
                    DimensionsCrossedIDS.STARBASE_SPACEPORT.equals(indId)) {
                return true;
            }

            if (ind instanceof Spaceport) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getUnavailableReason() {
        return "Requires a functional Spaceport, Assimilated Spaceport, Starbase Spaceport, or equivalent facility.";
    }

    private int getBaseDemandForSize(int size) {
        if (size <= 5) return 3;
        if (size <= 8) return 4;
        if (size == 9) return 5;
        return 6; // 10+
    }

    private int getBaseSupplyForSize(int size) {
        if (size <= 3) return 3;
        if (size <= 5) return 4;
        if (size == 6) return 5;
        if (size <= 8) return 6;
        if (size == 9) return 7;
        return 8; // 10+
    }

    private int getDilithiumDemandForSize(int size) {
        if (size <= 5) return 1;
        if (size <= 7) return 2;
        if (size <= 9) return 3;
        return 4; // 10
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        return Items.BIOFACTORY_EMBRYO.equals(data.getId()) || super.wantsToUseSpecialItem(data);
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        float bonus = getPopulationGrowthBonus();
        tooltip.addPara("Population growth: %s", opad, h, "+" + (int) bonus);

        if (special != null && Items.BIOFACTORY_EMBRYO.equals(special.getId())) {
            tooltip.addPara("Biofactory Embryo installed: +1 Supply, -1 Demand, +2 Population Growth, +12.5%% Fleet Size.", opad, h, "+1", "-1", "+2", "+12.5%");
        }
    }
}