package UFP.data.campaign.industry;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.List;

public class ResearchLabs extends BaseIndustry {

    private float daysElapsed = 0f;
    private static final float REFRESH_DAYS = 10f;

    private static final List<String> SPECIAL_ITEMS = List.of(
            Items.CORRUPTED_NANOFORGE,
            Items.CRYOARITHMETIC_ENGINE,
            Items.DEALMAKER_HOLOSUITE,
            Items.PLASMA_DYNAMO,
            Items.PRISTINE_NANOFORGE,
            Items.SYNCHROTRON
    );

    @Override
    public void apply() {
        super.apply(true);
        int size = market.getSize();

        // Standard Demands
        demand(Commodities.CREW, size);
        demand(Commodities.SUPPLIES, size - 1);
        demand(Commodities.VOLATILES, size - 1);
        demand(Commodities.RARE_METALS, size - 2);
        demand(Commodities.ORGANS, size - 2);
        demand(Commodities.FOOD, size);
        demand(Commodities.AI_CORES, size - 3);

        // Interchangeable Demands
        demand(DimensionsCrossedIDS.SCIENTIST, size);
        demand(DimensionsCrossedIDS.STARFLEET_OFFICERS, size);

        // Logic for substitution: Scientist OR Science Officer
        // We calculate the efficiency based on whichever one is more available.
        float scientistAvail = market.getCommodityData(DimensionsCrossedIDS.SCIENTIST).getAvailable();
        float officerAvail = market.getCommodityData(DimensionsCrossedIDS.STARFLEET_OFFICERS).getAvailable();

        // Efficiency is the highest availability of either, divided by required (size)
        float maxAvailable = Math.max(scientistAvail, officerAvail);
        float personnelEfficiency = Math.min(1f, maxAvailable / Math.max(1f, (float) size));

        // Apply custom efficiency to production and income
        // (This overrides the default penalty if one is missing but the other is present)
        if (personnelEfficiency < 1f) {
            float penalty = 1f - personnelEfficiency;
            getIncome().modifyMult("personnel_shortage", personnelEfficiency, "Personnel shortage");
            // Also apply to all supplies
            for (String id : getAllSupplied()) {
                getSupply(id).getQuantity().modifyMult("personnel_shortage", personnelEfficiency);
            }
        } else {
            getIncome().unmodifyMult("personnel_shortage");
            for (String id : getAllSupplied()) {
                getSupply(id).getQuantity().unmodifyMult("personnel_shortage");
            }
        }

        // Production Scaling
        supply(Commodities.BETA_CORE, size - 4);
        supply(Commodities.OMEGA_CORE, size - 5);
        supply(Commodities.HEAVY_MACHINERY, size - 1);
        supply(Commodities.HAND_WEAPONS, size);

        if (!isFunctional()) {
            supply(Commodities.BETA_CORE, 0);
            supply(Commodities.OMEGA_CORE, 0);
        }
    }

    private String[] getAllSupplied() {
        return new String[0];
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!isFunctional() || market.isPlayerOwned()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;

        if (daysElapsed >= REFRESH_DAYS) {
            daysElapsed = 0;
            refreshMarketInventory();
        }
    }

    private void refreshMarketInventory() {
        FactionAPI faction = market.getFaction();

        // 1. Weapon Blueprints
        injectWeaponBlueprint(Submarkets.GENERIC_MILITARY, 1.0f, faction);
        injectWeaponBlueprint(Submarkets.SUBMARKET_BLACK, 0.5f, faction);

        // 2. Guaranteed Special Items
        injectGuaranteedItem(Submarkets.SUBMARKET_OPEN);
        injectGuaranteedItem(Submarkets.SUBMARKET_BLACK);
        injectGuaranteedItem(Submarkets.GENERIC_MILITARY);
    }

    private void injectWeaponBlueprint(String submarketId, float chance, FactionAPI faction) {
        if (!market.hasSubmarket(submarketId) || Math.random() > chance) return;

        CargoAPI cargo = market.getSubmarket(submarketId).getCargo();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        picker.addAll(faction.getKnownWeapons());

        String weaponId = picker.pick();
        if (weaponId != null) {
            cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, weaponId), 1);
        }
    }

    private void injectGuaranteedItem(String submarketId) {
        if (!market.hasSubmarket(submarketId)) return;

        CargoAPI cargo = market.getSubmarket(submarketId).getCargo();
        WeightedRandomPicker<String> itemPicker = new WeightedRandomPicker<>();
        itemPicker.addAll(SPECIAL_ITEMS);

        String itemId = itemPicker.pick();
        if (itemId != null) {
            cargo.addSpecial(new SpecialItemData(itemId, null), 1);
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        return true;
    }
}