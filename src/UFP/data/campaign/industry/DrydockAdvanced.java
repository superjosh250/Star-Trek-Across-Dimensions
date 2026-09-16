package UFP.data.campaign.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Pair;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class DrydockAdvanced extends Drydock {

    private int getCustomSupplyUnit(int marketSize) {
        return switch (marketSize) {
            case 3 -> 3;
            case 4, 5 -> 4;
            case 6 -> 5;
            default -> (marketSize >= 7) ? 6 : Math.max(1, marketSize);
        };
    }

    private int getCustomDemandUnit(int marketSize) {
        return switch (marketSize) {
            case 3, 4, 5 -> 3;
            case 6, 7, 8 -> 4;
            default -> (marketSize >= 9) ? 5 : Math.max(1, marketSize);
        };
    }

    protected int getDrydockCountInSystem() {
        if (market == null || market.getContainingLocation() == null) return 0;
        int count = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarkets(market.getContainingLocation())) {
            for (Industry ind : m.getIndustries()) {
                if (ind.isFunctional()) {
                    String id = ind.getId();
                    if (DimensionsCrossedIDS.DRYDOCK.equals(id) ||
                            DimensionsCrossedIDS.DRYDOCK_MCKINLEY.equals(id) ||
                            DimensionsCrossedIDS.DRYDOCK_ADVANCED.equals(id)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    protected boolean hasOtherDrydockOnMarket() {
        if (market == null) return false;
        for (Industry ind : market.getIndustries()) {
            if (ind == this || !ind.isFunctional()) continue;
            String id = ind.getId();
            if (DimensionsCrossedIDS.DRYDOCK.equals(id) || DimensionsCrossedIDS.DRYDOCK_MCKINLEY.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply() {
        super.apply();

        if (!isFunctional()) return;

        int size = market.getSize();
        int baseSupply = getCustomSupplyUnit(size);
        int baseDemand = getCustomDemandUnit(size);

        int demandReduction = 0;
        int bonusSupply = 0;
        int shipsSupplyExtra = 0;

        // --- BASE ADVANCED VALUES ---
        int shipBonus = 6;
        float shipQuality = 1.75f;
        float accessibility = 0.25f;

        market.getHazard().modifyFlat(getModId(1), -0.075f, getNameForModifier());

        // --- COMBAT FLEET SIZE MULTIPLIER LOGIC ---
        float fleetSizeMult = hasOtherDrydockOnMarket() ? 3.05f : 2.25f;
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMult(getModId() + "_fleet_size", fleetSizeMult, getNameForModifier());

        // --- SYSTEM SYNERGY BONUS (4+ DRYDOCKS IN SYSTEM) ---
        if (getDrydockCountInSystem() >= 4) {
            shipBonus += 4;
            shipQuality += 0.25f;

            // Stack 1.25x multiplier bonus onto fleet size
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId() + "_system_4_bonus", 1.25f, getNameForModifier() + " (System Drydock Network)");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .unmodifyMult(getModId() + "_system_4_bonus");
        }

        // --- SPECIAL ITEMS (Inherited behavior from Drydock) ---
        if (special != null) {
            String id = special.getId();
            boolean isCorrupted = Items.CORRUPTED_NANOFORGE.equals(id);
            boolean isPristine = Items.PRISTINE_NANOFORGE.equals(id);

            if (isCorrupted || isPristine) {
                bonusSupply += isPristine ? 2 : 1;
                shipQuality += isPristine ? 0.35f : 0.25f;
                shipBonus += isPristine ? 4 : 2;
            }
        }

        // --- AI CORES (Inherited behavior from Drydock) ---
        if (aiCoreId != null) {
            demandReduction += 1;

            switch (aiCoreId) {
                case Commodities.GAMMA_CORE ->
                        getUpkeep().modifyMult(getModId(2), 0.90f, "Gamma Core (-10% Upkeep)");
                case Commodities.ALPHA_CORE -> {
                    bonusSupply += 1;
                    getUpkeep().modifyMult(getModId(2), 0.75f, "Alpha Core (-25% Upkeep)");
                }
                case Commodities.OMEGA_CORE -> {
                    bonusSupply += 2;
                    getUpkeep().modifyMult(getModId(2), 0.75f, "Omega Core (-25% Upkeep)");
                    market.getStability().modifyFlat(getModId(2), 1f, "Omega Core (+1 Stability)");
                }
            }
        }

        // --- ADVANCED IMPROVEMENT TIERS ---
        if (improvementLevel >= 2) shipQuality += 0.80f;
        if (improvementLevel >= 3) shipsSupplyExtra += 2;
        if (improvementLevel >= 4) {
            shipBonus += 1;
            bonusSupply += 1;
            getUpkeep().modifyMult(getModId() + "_imp4", 0.975f, "Drydock Tier IV (-2.5% Upkeep)");
        }

        // --- APPLY STAT MODIFIERS ---
        market.getAccessibilityMod().modifyFlat(getModId(0), accessibility, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(0), shipQuality, getNameForModifier());

        // --- DEMANDS ---
        int finalMetalsDemand = Math.max(0, baseDemand - demandReduction);
        int finalDilithiumDemand = Math.max(0, baseSupply - demandReduction);

        demand(Commodities.METALS, finalMetalsDemand);
        demand(Commodities.RARE_METALS, finalMetalsDemand);
        demand(DimensionsCrossedIDS.DILITHIUM, finalDilithiumDemand);

        // --- DEFICIT HANDLING & PRODUCTION MATH ---
        Pair<String, Integer> maxDeficit = getMaxDeficit(
                Commodities.METALS,
                Commodities.RARE_METALS,
                DimensionsCrossedIDS.DILITHIUM
        );
        int deficit = (maxDeficit != null) ? maxDeficit.two : 0;

        int generalSupply = Math.max(1, baseSupply + bonusSupply - deficit);
        int shipsSupply = Math.max(1, baseSupply + bonusSupply + shipsSupplyExtra - deficit);

        // --- SUPPLIES ---
        supply(Commodities.SUPPLIES, generalSupply);
        supply(Commodities.HEAVY_MACHINERY, generalSupply);
        supply(Commodities.HAND_WEAPONS, generalSupply);
        supply(Commodities.SHIPS, shipsSupply);

        if (shipBonus > 0) {
            supply(1, Commodities.SHIPS, shipBonus, getNameForModifier());
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_fleet_size");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_system_4_bonus");
    }
}