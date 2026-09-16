package BORG.data.campaign.industry;

import java.util.*;

import BORG.data.campaign.ids.BorgIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Pair;

public class Shipyard extends HeavyIndustry {

    private static final float REFRESH_DAYS = 30f;
    private static final int INJECT_CAPITALS = 2;
    private static final int INJECT_CRUISERS = 3;
    private static final int MAX_BLUEPRINTS = 5;

    private static final float ACCESSIBILITY_BONUS = 1.2f;
    private static final float STABILITY_BONUS = 1f;

    private static final float CORRUPTED_QUALITY_BONUS = 0.1f;
    private static final float PRISTINE_QUALITY_BONUS = 0.2f;
    private static final String MEM_INIT_DONE  = "$borg_shipyard_init_done";
    private static final String MEM_DAYS_SINCE = "$borg_shipyard_days_since";

    // ✅ Track injected content explicitly
    private final List<String> lastInjectedShipHullIds = new ArrayList<>();
    private final List<String> lastInjectedBlueprintHullIds = new ArrayList<>();

    @Override
    public void apply() {
        super.apply(true);

        int size = market.getSize();

        demand(Commodities.ALPHA_CORE, 1);
        demand(Commodities.AI_CORES, 2);
        demand(BorgIDS.DRONES, size);
        demand(Commodities.METALS, size);
        demand(Commodities.RARE_METALS, size);
        demand(Commodities.FUEL, size);
        demand(DimensionsCrossedIDS.DILITHIUM, size);

        supply(Commodities.SHIPS, size);
        supply(Commodities.SHIP_WEAPONS, size);

        Pair<String, Integer> deficit = getMaxDeficit(
                BorgIDS.DRONES,
                Commodities.METALS,
                Commodities.RARE_METALS,
                Commodities.FUEL
        );

        int maxDeficit = Math.max(0, size - 1);
        if (deficit.two > maxDeficit) deficit.two = maxDeficit;

        applyDeficitToProduction(1, deficit,
                Commodities.SHIPS,
                Commodities.SHIP_WEAPONS
        );

        market.getAccessibilityMod().modifyFlat(getModId(0), ACCESSIBILITY_BONUS, getNameForModifier());
        market.getStability().modifyFlat(getModId(1), STABILITY_BONUS, getNameForModifier());

        // =========================
        // ✅ FLEET SIZE BONUS (NEW)
        // +100% fleet size always; if market size > 8, add +25% more (total +125%)
        // =========================
        float fleetSizeBonus = 100f; // +100%
        if (size > 8) fleetSizeBonus += 25f; // +25% extra

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyPercent(getModId() + "_fleetSize", fleetSizeBonus, "Shipyard capacity");

        if (!isFunctional()) {
            supply.clear();
            return;
        }

        if (!market.getMemoryWithoutUpdate().getBoolean(MEM_INIT_DONE)) {
            refreshInjectedStock();
            market.getMemoryWithoutUpdate().set(MEM_INIT_DONE, true);
            market.getMemoryWithoutUpdate().set(MEM_DAYS_SINCE, 0f);
        }
    }

    // =========================
    // ✅ CLEANUP (NEW)
    // Remove fleet size modifier when industry is removed/disabled
    // =========================
    @Override
    public void unapply() {
        super.unapply();

        if (market != null) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .unmodify(getModId() + "_fleetSize");
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (market == null || !isFunctional()) return;

        float days = Global.getSector().getClock().convertToDays(amount);

        float since = 0f;
        if (market.getMemoryWithoutUpdate().contains(MEM_DAYS_SINCE)) {
            since = market.getMemoryWithoutUpdate().getFloat(MEM_DAYS_SINCE);
        }

        since += days;

        if (since >= REFRESH_DAYS) {
            since -= REFRESH_DAYS;
            refreshInjectedStock();
        }

        market.getMemoryWithoutUpdate().set(MEM_DAYS_SINCE, since);
    }

    private void applyNanoforgeBonus() {
        // Clear previous nanoforge bonus each apply-cycle
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD)
                .unmodifyFlat(getModId(2));
        if (special == null) return;

        float bonus = 0f;
        String label = null;
        if (Items.CORRUPTED_NANOFORGE.equals(special.getId())) {
            bonus = CORRUPTED_QUALITY_BONUS;
            label = "Corrupted nanoforge";
        } else if (Items.PRISTINE_NANOFORGE.equals(special.getId())) {
            bonus = PRISTINE_QUALITY_BONUS;
            label = "Pristine nanoforge";
        }

        if (bonus != 0f) {
            market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD)
                    .modifyFlat(getModId(2), bonus, label);
        }
    }

    private void refreshInjectedStock() {
        SubmarketAPI mil  = market.getSubmarket(Submarkets.GENERIC_MILITARY);
        SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
        if (mil == null && open == null) return;

        if (mil != null) {
            removeInjectedShips(mil);
            removeInjectedBlueprints(mil);
        }
        if (open != null) {
            removeInjectedBlueprints(open);
        }

        List<String> knownHullIds = buildKnownHullPool(market.getFaction());
        if (knownHullIds.isEmpty()) return;

        Random rng = new Random(
                market.getId().hashCode() ^ Global.getSector().getClock().getTimestamp()
        );

        List<String> capitals = filterByHullSize(knownHullIds, HullSize.CAPITAL_SHIP);
        List<String> cruisers = filterByHullSize(knownHullIds, HullSize.CRUISER);

        Collections.shuffle(capitals, rng);
        Collections.shuffle(cruisers, rng);

        List<String> pickedCaps = capitals.subList(0, Math.min(INJECT_CAPITALS, capitals.size()));
        List<String> pickedCruisers = cruisers.subList(0, Math.min(INJECT_CRUISERS, cruisers.size()));

        lastInjectedShipHullIds.clear();
        lastInjectedShipHullIds.addAll(pickedCaps);
        lastInjectedShipHullIds.addAll(pickedCruisers);

        if (mil != null) {
            injectShipsIntoMilitary(mil, pickedCaps, pickedCruisers);
        }

        List<String> bpPool = new ArrayList<>(knownHullIds);
        Collections.shuffle(bpPool, rng);

        List<String> pickedBps = bpPool.subList(0, Math.min(MAX_BLUEPRINTS, bpPool.size()));
        lastInjectedBlueprintHullIds.clear();
        lastInjectedBlueprintHullIds.addAll(pickedBps);

        if (mil != null) injectBlueprints(mil, pickedBps);
        if (open != null) injectBlueprints(open, pickedBps);
    }

    private void injectShipsIntoMilitary(SubmarketAPI mil, List<String> caps, List<String> cruisers) {
        CargoAPI cargo = mil.getCargo();
        cargo.initMothballedShips(market.getFactionId());
        FleetDataAPI mothballed = cargo.getMothballedShips();

        for (String id : caps) addShip(mothballed, id);
        for (String id : cruisers) addShip(mothballed, id);
    }

    private void addShip(FleetDataAPI data, String hullId) {
        try {
            FleetMemberAPI m = Global.getFactory().createFleetMember(
                    FleetMemberType.SHIP, hullId + "_Hull"
            );
            m.getRepairTracker().setCR(m.getRepairTracker().getMaxCR());
            data.addFleetMember(m);
        } catch (Throwable ignored) {}
    }

    private void removeInjectedShips(SubmarketAPI mil) {
        CargoAPI cargo = mil.getCargo();
        cargo.initMothballedShips(market.getFactionId());
        FleetDataAPI data = cargo.getMothballedShips();

        for (FleetMemberAPI m : data.getMembersListCopy()) {
            if (lastInjectedShipHullIds.contains(m.getHullSpec().getHullId())) {
                data.removeFleetMember(m);
            }
        }
    }

    private void injectBlueprints(SubmarketAPI sub, List<String> hullIds) {
        for (String id : hullIds) {
            sub.getCargo().addSpecial(new SpecialItemData(Items.SHIP_BP, id), 1);
        }
    }

    private void removeInjectedBlueprints(SubmarketAPI sub) {
        for (String id : lastInjectedBlueprintHullIds) {
            sub.getCargo().removeItems(
                    CargoItemType.SPECIAL,
                    new SpecialItemData(Items.SHIP_BP, id),
                    1
            );
        }
    }

    private List<String> buildKnownHullPool(FactionAPI faction) {
        Set<String> pool = new HashSet<>();
        pool.addAll(faction.getHullFrequency().keySet());
        pool.addAll(faction.getAlwaysKnownShips());

        List<String> result = new ArrayList<>();
        for (String id : pool) {
            try {
                Global.getSettings().getHullSpec(id);
                result.add(id);
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private List<String> filterByHullSize(List<String> hullIds, HullSize size) {
        List<String> out = new ArrayList<>();
        for (String id : hullIds) {
            try {
                if (Global.getSettings().getHullSpec(id).getHullSize() == size) {
                    out.add(id);
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        List<InstallableIndustryItemPlugin> list = new ArrayList<>();
        // Uses BaseIndustry/HeavyIndustry special-item framework; wantsToUseSpecialItem handles upgrades.
        list.add(new GenericInstallableItemPlugin(this));
        return list;
    }
}