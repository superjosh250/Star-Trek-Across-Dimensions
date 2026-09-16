package UFP.data.campaign.industry;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.List;
import java.util.Random;

public class University extends BaseIndustry {

    private float daysElapsed = 0f;
    private static final float REFRESH_DAYS = 10f;

    @Override
    public void apply() {
        super.apply(true);

        int size = market.getSize();

        // Demands
        demand(DimensionsCrossedIDS.CIVILIAN, size + 2);
        demand(Commodities.SUPPLIES, size - 2);
        demand(Commodities.FOOD, size);
        demand(Commodities.DOMESTIC_GOODS, size);
        demand(Commodities.LUXURY_GOODS, size - 1);
        demand(Commodities.DRUGS, size + 5);

        // Production (Assuming "scientist" is defined in commodities.csv)
        supply(DimensionsCrossedIDS.SCIENTIST, size + 2);

        if (!isFunctional()) {
            supply(DimensionsCrossedIDS.SCIENTIST, 0);
        }
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!isFunctional() || market.isPlayerOwned()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;

        if (daysElapsed >= REFRESH_DAYS) {
            daysElapsed = 0;
            injectBlueprints();
        }
    }

    private void injectBlueprints() {
        FactionAPI faction = market.getFaction();

        // Define probabilities
        tryInject(Submarkets.SUBMARKET_OPEN, 0.25f, faction);
        tryInject(Submarkets.SUBMARKET_BLACK, 0.50f, faction);
        tryInject(Submarkets.GENERIC_MILITARY, 1.00f, faction);
    }

    private void tryInject(String submarketId, float chance, FactionAPI faction) {
        if (!market.hasSubmarket(submarketId)) return;
        if (Math.random() > chance) return;

        SubmarketAPI submarket = market.getSubmarket(submarketId);
        CargoAPI cargo = submarket.getCargo();

        // Get a random blueprint from the faction's known library
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        picker.addAll(faction.getKnownShips());
        picker.addAll(faction.getKnownWeapons());
        picker.addAll(faction.getKnownFighters());

        String id = picker.pick();
        if (id != null) {
            // Determine item type
            String itemType = Items.SHIP_BP;
            if (faction.getKnownWeapons().contains(id)) itemType = Items.WEAPON_BP;
            if (faction.getKnownFighters().contains(id)) itemType = Items.FIGHTER_BP;

            cargo.addSpecial(new SpecialItemData(itemType, id), 1);
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        return true;
    }

    public boolean showInConstraintList() {
        return true;
    }
}