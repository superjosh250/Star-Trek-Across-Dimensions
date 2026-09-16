package UFP.data.campaign.submarket;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;
import com.fs.starfarer.api.util.Misc;

import java.lang.reflect.Method;
import java.util.*;

/**
 * UFP faction-specific military market.
 * Correctly intercepts ship generation to guarantee a strict 75% fitted variant / 25% bare hull split.
 */
public class FedMilitaryMarket extends MilitarySubmarketPlugin {

    private static final String ADV_DRYDOCK_ID = "ufp_advanced_drydock";

    // Guaranteed extra ships
    private static final int EXTRA_CAPITALS = 1;
    private static final int EXTRA_CRUISERS = 1;
    private static final int EXTRA_DESTROYERS = 2;
    private static final int EXTRA_FRIGATES = 2;

    // --- Blueprint controls ---
    private static final int GUARANTEED_BP_ALWAYS = 2;
    private static final int ROTATION_DAYS = 15;

    private static final String MEM_KEY_BP_ROT_KEY   = "$ufp_fedMil_bpRotKey";
    private static final String MEM_KEY_BP_GUAR_KEYS = "$ufp_fedMil_bpGuaranteedKeys";

    // Cached pools of BASE HULL IDs (e.g., "eagle", "falcon")
    private transient List<String> autoCaps;
    private transient List<String> autoCruisers;
    private transient List<String> autoDestroyers;
    private transient List<String> autoFrigates;
    private transient boolean poolsBuilt = false;

    @Override
    public String getName() {
        return "Starfleet Market";
    }

    @Override
    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true);
        sinceLastCargoUpdate = 0f;

        if (!okToUpdateShipsAndWeapons()) return;
        sinceSWUpdate = 0f;

        final String factionId = resolveUfpFactionId();

        // Completely clear old ships out before regenerating
        getCargo().getMothballedShips().clear();
        pruneWeapons(0f);

        final int size = market.getSize();

        int maxTier = computeMaxTierFromMarketSize(size);
        if (hasCommission()) {
            maxTier = Math.min(3, maxTier + 1);
        }

        int weapons = 10 + Math.max(0, size - 3) * 4;
        int fighters = 2 + Math.max(0, size - 3);
        int hullmods = 3 + Math.max(0, size - 3);

        addWeapons(weapons, weapons + 4, maxTier, factionId);
        addFighters(fighters, fighters + 2, maxTier, factionId);
        addHullMods(maxTier, hullmods, factionId);

        float combatPts = 60f + size * 18f;
        float freighterPts = 6f;
        float tankerPts = 4f;
        float transportPts = 4f;
        float linerPts = 0f;
        float utilityPts = 3f;
        float qualityMod = hasCommission() ? 0.15f : -0.10f;

        CommodityOnMarketAPI shipsCom = market.getCommodityData(Commodities.SHIPS);
        if (shipsCom != null) {
            freighterPts += Math.min(10f, shipsCom.getMaxSupply() * 1.5f);
        }
        CommodityOnMarketAPI fuelCom = market.getCommodityData(Commodities.FUEL);
        if (fuelCom != null) {
            tankerPts += Math.min(10f, fuelCom.getMaxSupply() * 1.5f);
        }

        addShips(factionId, combatPts, freighterPts, tankerPts, transportPts, linerPts, utilityPts, null, qualityMod, FactionAPI.ShipPickMode.PRIORITY_THEN_ALL, null);

        int maxShipSize = hasCommission() ? 4 : 3;
        addShips(factionId, 45f + size * 10f, 0f, 0f, 0f, 0f, 0f, null, qualityMod - 0.25f, null, null, maxShipSize);

        List<FleetMemberAPI> totalGeneratedPool = new ArrayList<>(getCargo().getMothballedShips().getMembersListCopy());
        totalGeneratedPool.addAll(generateGuaranteedExtraShipsVariants(factionId));

        addBlueprintsWithRotationAndProduction(factionId);

        if (!totalGeneratedPool.isEmpty()) {
            getCargo().getMothballedShips().clear();

            Collections.shuffle(totalGeneratedPool, itemGenRandom);
            float quality = Misc.getShipQuality(market, factionId);

            for (FleetMemberAPI member : totalGeneratedPool) {
                if (member == null) continue;

                String coreHullId = member.getHullId();

                if (itemGenRandom.nextFloat() < 0.75f) {
                    String validVariantId = findValidVariantForHull(coreHullId, factionId);
                    if (validVariantId != null) {
                        addShip(validVariantId, false, quality);
                    } else {
                        addShip(member.getVariant().getHullVariantId(), false, quality);
                    }
                } else {
                    String plainHullId = coreHullId + "_Hull";
                    addShip(plainHullId, true, quality);
                }
            }
        }

        getCargo().sort();
        getCargo().removeEmptyStacks();
    }

    private List<FleetMemberAPI> generateGuaranteedExtraShipsVariants(String factionId) {
        ensureAutoPoolsBuilt(factionId);
        List<FleetMemberAPI> extraShips = new ArrayList<>();

        gatherVariantsFromPool(autoCaps, EXTRA_CAPITALS, factionId, extraShips);
        gatherVariantsFromPool(autoCruisers, EXTRA_CRUISERS, factionId, extraShips);
        gatherVariantsFromPool(autoDestroyers, EXTRA_DESTROYERS, factionId, extraShips);
        gatherVariantsFromPool(autoFrigates, EXTRA_FRIGATES, factionId, extraShips);

        return extraShips;
    }

    private void gatherVariantsFromPool(List<String> pool, int count, String factionId, List<FleetMemberAPI> outputList) {
        if (count <= 0 || pool == null || pool.isEmpty()) return;

        int added = 0;
        int attempts = 0;
        int maxAttempts = Math.max(10, pool.size() * 5);

        while (added < count && attempts < maxAttempts) {
            String hullId = pool.get(attempts % pool.size());
            attempts++;

            String targetVariantId = findValidVariantForHull(hullId, factionId);
            if (targetVariantId == null) {
                targetVariantId = hullId + "_Hull";
            }

            try {
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, targetVariantId);
                if (member != null) {
                    outputList.add(member);
                    added++;
                }
            } catch (Exception ignored) {}
        }
    }

    private String findValidVariantForHull(String hullId, String factionId) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        List<String> candidateVariants = new ArrayList<>();

        if (faction != null && faction.getVariantOverrides() != null) {
            for (String varId : faction.getVariantOverrides().keySet()) {
                try {
                    ShipVariantAPI var = Global.getSettings().getVariant(varId);
                    if (var != null && var.getHullSpec() != null && hullId.equals(var.getHullSpec().getHullId())) {
                        candidateVariants.add(varId);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (candidateVariants.isEmpty()) {
            try {
                List<String> sysVariants = Global.getSettings().getHullIdToVariantListMap().get(hullId);
                if (sysVariants != null) {
                    for (String varId : sysVariants) {
                        if (varId != null && !varId.endsWith("_Hull")) {
                            candidateVariants.add(varId);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!candidateVariants.isEmpty()) {
            return candidateVariants.get(itemGenRandom.nextInt(candidateVariants.size()));
        }

        return null;
    }

    private void addBlueprintsWithRotationAndProduction(String factionId) {
        if (market == null) return;

        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction == null) return;

        int cycle = Global.getSector().getClock().getCycle();
        int month = Global.getSector().getClock().getMonth();
        int day = Global.getSector().getClock().getDay();
        int period = Math.min(1, Math.max(0, day / ROTATION_DAYS));
        long rotKey = cycle * 1000L + month * 10L + period;

        Long lastRot = null;
        if (market.getMemoryWithoutUpdate().contains(MEM_KEY_BP_ROT_KEY)) {
            lastRot = market.getMemoryWithoutUpdate().getLong(MEM_KEY_BP_ROT_KEY);
        }

        if (lastRot == null || lastRot != rotKey) {
            removeGuaranteedBlueprintStacksFromCargo();
            market.getMemoryWithoutUpdate().set(MEM_KEY_BP_ROT_KEY, rotKey);
            market.getMemoryWithoutUpdate().unset(MEM_KEY_BP_GUAR_KEYS);
        }

        List<String> guaranteedKeys = getOrComputeGuaranteedKeysForRotation(faction, rotKey);
        ensureStacksForKeysPresent(getCargo(), guaranteedKeys);

        int extraTarget = getExtraBlueprintTargetFromProduction();
        if (extraTarget > 0) {
            ensureExtraBlueprintsPresent(faction, rotKey, guaranteedKeys, extraTarget);
        }
    }

    private int getExtraBlueprintTargetFromProduction() {
        CommodityOnMarketAPI bp = market.getCommodityData(Commodities.BLUEPRINTS);
        if (bp == null) return 0;
        return Math.min(10, Math.max(0, bp.getMaxSupply()) / 2);
    }

    private void ensureExtraBlueprintsPresent(FactionAPI faction, long rotKey, List<String> guaranteedKeys, int extraTarget) {
        int existing = countBlueprintStacks(getCargo());
        int desired = GUARANTEED_BP_ALWAYS + extraTarget;
        if (existing >= desired) return;

        int need = desired - existing;
        Pools pools = buildUsableBlueprintPools(faction);
        Random r = new Random((market.getId().hashCode() * 31L) ^ (rotKey * 997L) ^ 0xBEEF1234L);

        for (int i = 0; i < need; i++) {
            SpecialItemData data = pickFromPoolsDeterministic(pools, r);
            if (data == null) break;

            String key = keyOf(data);
            if (guaranteedKeys.contains(key)) {
                for (int tries = 0; tries < 10; tries++) {
                    SpecialItemData alt = pickFromPoolsDeterministic(pools, r);
                    if (alt == null) break;
                    if (!guaranteedKeys.contains(keyOf(alt))) {
                        data = alt;
                        break;
                    }
                }
            }
            getCargo().addSpecial(data, 1f);
        }
    }

    private int countBlueprintStacks(CargoAPI cargo) {
        int count = 0;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack == null || !stack.isSpecialStack()) continue;
            SpecialItemData d = stack.getSpecialDataIfSpecial();
            if (d == null) continue;

            String id = d.getId();
            if (Items.SHIP_BP.equals(id) || Items.WEAPON_BP.equals(id) || Items.FIGHTER_BP.equals(id) || Items.INDUSTRY_BP.equals(id)) {
                count++;
            }
        }
        return count;
    }

    private List<String> getOrComputeGuaranteedKeysForRotation(FactionAPI faction, long rotKey) {
        Object stored = market.getMemoryWithoutUpdate().get(MEM_KEY_BP_GUAR_KEYS);
        if (stored instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) stored;
            if (keys.size() == GUARANTEED_BP_ALWAYS) return keys;
        }

        Pools pools = buildUsableBlueprintPools(faction);
        Random r = new Random((market.getId().hashCode() * 13L) ^ (rotKey * 99991L));

        List<String> keys = new ArrayList<String>();
        for (int i = 0; i < GUARANTEED_BP_ALWAYS; i++) {
            SpecialItemData data = pickFromPoolsDeterministic(pools, r);
            if (data == null) break;

            String key = keyOf(data);
            if (key == null || key.isEmpty()) {
                continue;
            }

            if (keys.contains(key)) {
                for (int tries = 0; tries < 20; tries++) {
                    SpecialItemData alt = pickFromPoolsDeterministic(pools, r);
                    if (alt == null) break;

                    String altKey = keyOf(alt);
                    if (altKey == null || altKey.isEmpty()) {
                        continue;
                    }

                    if (!keys.contains(altKey)) {
                        data = alt;
                        key = altKey;
                        break;
                    }
                }
            }

            if (key != null && !key.isEmpty()) {
                keys.add(key);
            }
        }

        while (keys.size() < GUARANTEED_BP_ALWAYS) {
            keys.add(keys.isEmpty() ? (Items.WEAPON_BP + "|") : keys.get(0));
        }

        market.getMemoryWithoutUpdate().set(MEM_KEY_BP_GUAR_KEYS, keys);
        return keys;
    }

    private void ensureStacksForKeysPresent(CargoAPI cargo, List keys) {
        if (cargo == null || keys == null) return;

        for (Object obj : keys) {
            if (!(obj instanceof String)) continue;

            String key = (String) obj;
            SpecialItemData raw = fromKey(key);
            if (raw == null) continue;

            SpecialItemData data = raw;

            // HARD VALIDATION: fighter blueprints must resolve from a valid wingId
            if (Items.FIGHTER_BP.equals(raw.getId())) {
                data = tryCreateValidatedFighterBlueprint(raw.getData());
                if (data == null) {
                    Global.getLogger(FedMilitaryMarket.class)
                            .warn("BLOCKED INVALID fighter BP during restore -> " + raw.getData());
                    continue;
                }
            }

            // Additional safety net before adding any BP item
            if (Items.FIGHTER_BP.equals(data.getId())) {
                FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(data.getData());
                if (wing == null) {
                    Global.getLogger(FedMilitaryMarket.class)
                            .warn("BLOCKED INVALID fighter BP before cargo insert -> " + data.getData());
                    continue;
                }
            }

            if (!cargoAlreadyHasSpecial(cargo, data)) {
                cargo.addSpecial(data, 1);
            }
        }
    }

    private void removeGuaranteedBlueprintStacksFromCargo() {
        Object stored = market.getMemoryWithoutUpdate().get(MEM_KEY_BP_GUAR_KEYS);
        if (!(stored instanceof List)) return;

        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) stored;
        if (keys.isEmpty()) return;

        Set<String> keySet = new HashSet<>(keys);

        for (CargoStackAPI stack : getCargo().getStacksCopy()) {
            if (stack == null || !stack.isSpecialStack()) continue;
            SpecialItemData d = stack.getSpecialDataIfSpecial();
            if (d == null) continue;

            String id = d.getId();
            if (!(Items.SHIP_BP.equals(id) || Items.WEAPON_BP.equals(id) || Items.FIGHTER_BP.equals(id) || Items.INDUSTRY_BP.equals(id))) {
                continue;
            }

            if (keySet.contains(keyOf(d))) {
                getCargo().removeStack(stack);
            }
        }
    }

    private Pools buildUsableBlueprintPools(FactionAPI faction) {
        Pools p = new Pools();
        if (faction == null) return p;

        List<String> hullIds = new ArrayList<String>(faction.getHullFrequency().keySet());
        for (String hullId : hullIds) {
            try {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec == null) continue;
                p.ships.add(new SpecialItemData(Items.SHIP_BP, hullId));
            } catch (Exception ex) {
                Global.getLogger(FedMilitaryMarket.class)
                        .warn("Skipping invalid ship BP hullId: " + hullId, ex);
            }
        }

        List<String> knownWeapons = tryGetKnownList(faction, "getKnownWeapons");
        for (String weaponId : knownWeapons) {
            if (weaponId == null || weaponId.trim().isEmpty()) continue;
            p.weapons.add(new SpecialItemData(Items.WEAPON_BP, weaponId));
        }

        // ----------------------------
        // FIGHTERS
        // ----------------------------
        List<String> knownFighters = tryGetKnownList(faction, "getKnownFighters");
        for (String wingId : knownFighters) {
            SpecialItemData fighterBp = tryCreateValidatedFighterBlueprint(wingId);
            if (fighterBp != null) {
                p.fighters.add(fighterBp);
            }
        }

        List<String> knownIndustries = tryGetKnownList(faction, "getKnownIndustries");
        for (String industryId : knownIndustries) {
            if (industryId == null || industryId.trim().isEmpty()) continue;
            p.industries.add(new SpecialItemData(Items.INDUSTRY_BP, industryId));
        }

        return p;
    }

    private List<String> tryGetKnownList(FactionAPI faction, String methodName) {
        try {
            Method m = faction.getClass().getMethod(methodName);
            Object out = m.invoke(faction);
            if (out instanceof Collection) {
                List<String> list = new ArrayList<>();
                for (Object o : (Collection<?>) out) {
                    if (o instanceof String) list.add((String) o);
                }
                return list;
            }
        } catch (Throwable ignored) { }
        return Collections.emptyList();
    }

    private SpecialItemData pickFromPoolsDeterministic(Pools pools, Random r) {
        if (pools == null || r == null) return null;

        int roll = r.nextInt(100);

        if (roll < 45 && !pools.ships.isEmpty()) {
            return pools.ships.get(r.nextInt(pools.ships.size()));
        }
        if (roll < 75 && !pools.weapons.isEmpty()) {
            return pools.weapons.get(r.nextInt(pools.weapons.size()));
        }
        if (roll < 90 && !pools.fighters.isEmpty()) {
            return pools.fighters.get(r.nextInt(pools.fighters.size()));
        }
        if (!pools.industries.isEmpty()) {
            return pools.industries.get(r.nextInt(pools.industries.size()));
        }

        if (!pools.ships.isEmpty()) return pools.ships.get(r.nextInt(pools.ships.size()));
        if (!pools.weapons.isEmpty()) return pools.weapons.get(r.nextInt(pools.weapons.size()));
        if (!pools.fighters.isEmpty()) return pools.fighters.get(r.nextInt(pools.fighters.size()));
        if (!pools.industries.isEmpty()) return pools.industries.get(r.nextInt(pools.industries.size()));

        return null;
    }

    private static class Pools {
        List<SpecialItemData> ships = new ArrayList<SpecialItemData>();
        List<SpecialItemData> weapons = new ArrayList<SpecialItemData>();
        List<SpecialItemData> fighters = new ArrayList<SpecialItemData>();
        List<SpecialItemData> industries = new ArrayList<SpecialItemData>();
    }

    private String keyOf(SpecialItemData data) {
        if (data == null) return "";

        // Normalize fighter blueprints to a validated wingId-backed item before serializing
        if (Items.FIGHTER_BP.equals(data.getId())) {
            SpecialItemData validated = tryCreateValidatedFighterBlueprint(data.getData());
            if (validated == null) {
                Global.getLogger(FedMilitaryMarket.class)
                        .warn("Refusing to serialize invalid fighter BP key -> " + data.getData());
                return "";
            }
            data = validated;
        }

        return data.getId() + "|" + (data.getData() == null ? "" : data.getData());
    }

    private SpecialItemData fromKey(String key) {
        if (key == null || key.trim().isEmpty()) return null;

        String[] parts = key.split("\\|", 2);
        if (parts.length == 0) return null;

        String id = parts[0] == null ? "" : parts[0].trim();
        String data = parts.length > 1 && parts[1] != null ? parts[1].trim() : "";

        if (id.isEmpty()) return null;

        return new SpecialItemData(id, data);
    }

    private SpecialItemData tryCreateValidatedFighterBlueprint(String wingId) {
        if (wingId == null || wingId.trim().isEmpty()) {
            return null;
        }

        try {
            FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
            if (wingSpec == null) {
                return null;
            }

            String variantId = wingSpec.getVariantId();
            if (variantId == null || variantId.trim().isEmpty()) {
                return null;
            }

            ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
            if (variant == null || variant.getHullSpec() == null) {
                return null;
            }

            return new SpecialItemData(Items.FIGHTER_BP, wingId);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean cargoAlreadyHasSpecial(CargoAPI cargo, SpecialItemData wanted) {
        if (cargo == null || wanted == null) return false;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack == null || !stack.isSpecialStack()) continue;
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            String wantedData = wanted.getData() == null ? "" : wanted.getData();
            String actualData = data.getData() == null ? "" : data.getData();

            if (wanted.getId().equals(data.getId()) && wantedData.equals(actualData)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, SubmarketPlugin.TransferAction action) {
        if (stack == null || action != TransferAction.PLAYER_BUY || !stack.isSpecialStack()) {
            return super.isIllegalOnSubmarket(stack, action);
        }

        SpecialItemData data = stack.getSpecialDataIfSpecial();
        if (data == null) return super.isIllegalOnSubmarket(stack, action);

        String id = data.getId();
        if (!(Items.SHIP_BP.equals(id) || Items.WEAPON_BP.equals(id) || Items.FIGHTER_BP.equals(id) || Items.INDUSTRY_BP.equals(id))) {
            return super.isIllegalOnSubmarket(stack, action);
        }

        FactionAPI targetFaction = Global.getSector().getFaction(resolveUfpFactionId());
        RepLevel level = targetFaction != null ? targetFaction.getRelToPlayer().getLevel() : RepLevel.NEUTRAL;
        RepLevel req = Items.SHIP_BP.equals(id) ? RepLevel.FAVORABLE : (Items.INDUSTRY_BP.equals(id) ? RepLevel.COOPERATIVE : RepLevel.NEUTRAL);

        if (hasCommission()) {
            req = downgradeOne(req);
        }

        return level.ordinal() < req.ordinal();
    }

    private RepLevel downgradeOne(RepLevel level) {
        return RepLevel.values()[Math.max(RepLevel.SUSPICIOUS.ordinal(), level.ordinal() - 1)];
    }

    private void ensureAutoPoolsBuilt(String factionId) {
        if (poolsBuilt && autoCaps != null) return;

        autoCaps = new ArrayList<>();
        autoCruisers = new ArrayList<>();
        autoDestroyers = new ArrayList<>();
        autoFrigates = new ArrayList<>();

        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction == null) {
            poolsBuilt = true;
            return;
        }

        for (String hullId : faction.getHullFrequency().keySet()) {
            ShipHullSpecAPI spec;
            try {
                spec = Global.getSettings().getHullSpec(hullId);
            } catch (Exception ex) { continue; }
            if (spec == null) continue;

            if (spec.getHints() != null) {
                if (spec.getHints().contains(ShipTypeHints.STATION)) continue;
                if (spec.getHints().contains(ShipTypeHints.MODULE)) continue;
            }

            switch (spec.getHullSize()) {
                case CAPITAL_SHIP -> autoCaps.add(hullId);
                case CRUISER -> autoCruisers.add(hullId);
                case DESTROYER -> autoDestroyers.add(hullId);
                case FRIGATE -> autoFrigates.add(hullId);
                default -> { }
            }
        }

        long seed = itemGenRandom != null ? itemGenRandom.nextLong() : new Random().nextLong();
        Collections.shuffle(autoCaps, new Random(seed ^ 0xA11CE));
        Collections.shuffle(autoCruisers, new Random(seed ^ 0xB22DF));
        Collections.shuffle(autoDestroyers, new Random(seed ^ 0xC33EF));
        Collections.shuffle(autoFrigates, new Random(seed ^ 0xD44F1));

        poolsBuilt = true;
    }

    private String resolveUfpFactionId() {
        return Global.getSector().getFaction(DimensionsCrossedIDS.UFP) != null ? DimensionsCrossedIDS.UFP : market.getFactionId();
    }

    private int computeMaxTierFromMarketSize(int size) {
        if (size >= 7) return 3;
        if (size >= 5) return 2;
        if (size >= 3) return 1;
        return 0;
    }
}