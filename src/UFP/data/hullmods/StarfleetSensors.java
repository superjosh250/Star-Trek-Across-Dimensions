package UFP.data.hullmods;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.hullmods.HighResSensors;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class StarfleetSensors extends HighResSensors {

    // Station Specialized Bonus Constants
    private static final float STATION_COMBAT_SIGHT_BONUS_PERCENT = 400f;
    private static final float STATION_WEAPON_RANGE_BONUS_PERCENT = 40f;
    private static final float ALLIED_WEAPON_RANGE_BONUS_PERCENT = 7.5f;

    // UFP Base Sensor Maps
    private static final Map<HullSize, Float> ufpBaseCampaign = new HashMap<>();
    private static final Map<HullSize, Float> ufpBaseCombat = new HashMap<>();

    static {
        // Base Campaign Sensor Range (su)
        ufpBaseCampaign.put(HullSize.FRIGATE, 100f);
        ufpBaseCampaign.put(HullSize.DESTROYER, 125f);
        ufpBaseCampaign.put(HullSize.CRUISER, 200f);
        ufpBaseCampaign.put(HullSize.CAPITAL_SHIP, 275f);

        // Base Tactical Combat Sight Radius (u)
        ufpBaseCombat.put(HullSize.FRIGATE, 1500f);
        ufpBaseCombat.put(HullSize.DESTROYER, 2250f);
        ufpBaseCombat.put(HullSize.CRUISER, 2500f);
        ufpBaseCombat.put(HullSize.CAPITAL_SHIP, 3400f);
    }

    /**
     * Special Sensor Package Definition Container
     */
    private static class SpecialSensorPackage {
        final String displayName;
        final String tagKey;
        final float campaignBonus;   // su
        final float combatBonus;     // u
        final float weaponRangeMult; // percentage e.g. 7.5f = +7.5%

        SpecialSensorPackage(String displayName, String tagKey, float campaignBonus, float combatBonus, float weaponRangeMult) {
            this.displayName = displayName;
            this.tagKey = tagKey.toLowerCase();
            this.campaignBonus = campaignBonus;
            this.combatBonus = combatBonus;
            this.weaponRangeMult = weaponRangeMult;
        }
    }

    // Master Registry of Special Sensor Packages (order defines priority when > 2 match)
    private static final List<SpecialSensorPackage> SPECIAL_PACKAGES = new ArrayList<>();

    static {
        SPECIAL_PACKAGES.add(new SpecialSensorPackage("Survey Array", "SURVEYOR", 75f, 250f, 7.5f));
        SPECIAL_PACKAGES.add(new SpecialSensorPackage("Research Sensor Array", "RESEARCH_SENSOR_ARRAY", 150f, 25f, 5.0f));
        SPECIAL_PACKAGES.add(new SpecialSensorPackage("Tactical Sensor Package", "TACTICAL_SENSOR_PACKAGE", 25f, 150f, 20.0f));
        SPECIAL_PACKAGES.add(new SpecialSensorPackage("Veteran Sensor Suite", "VETERAN_SENSOR_PACKAGE", 50f, 50f, 7.5f));
        SPECIAL_PACKAGES.add(new SpecialSensorPackage("External Sensor Pod", "SENSOR_POD", 700f, 400f, 2.5f));
        SPECIAL_PACKAGES.add(new SpecialSensorPackage("Exploration Sensor Suite", "EXPLORATORY_SENSOR_PACKAGE", 250f, 250f, 12.5f));
    }

    private static boolean isStation(ShipHullSpecAPI spec) {
        return spec != null && spec.getHints() != null && spec.getHints().contains(ShipTypeHints.STATION);
    }

    /**
     * Helper to resolve active and skipped packages for a given hull spec.
     * Enforces a maximum of 2 distinct applied packages without duplicate tag stacking.
     */
    @SuppressWarnings("unchecked")
    private static List<SpecialSensorPackage>[] getResolvedPackages(ShipHullSpecAPI spec) {
        List<SpecialSensorPackage> applied = new ArrayList<>();
        List<SpecialSensorPackage> skipped = new ArrayList<>();

        if (spec == null) {
            return new List[]{applied, skipped};
        }

        Set<String> seenKeys = new HashSet<>();

        for (SpecialSensorPackage pkg : SPECIAL_PACKAGES) {
            if (spec.hasTag(pkg.tagKey) || spec.hasTag(pkg.tagKey.toUpperCase())) {
                if (seenKeys.contains(pkg.tagKey)) {
                    continue; // Prevent duplicate tag stacking
                }
                seenKeys.add(pkg.tagKey);

                if (applied.size() < 2) {
                    applied.add(pkg);
                } else {
                    skipped.add(pkg);
                }
            }
        }

        return new List[]{applied, skipped};
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        float campaignSensorTotal = ufpBaseCampaign.getOrDefault(hullSize, 100f);
        float combatSightTotal = ufpBaseCombat.getOrDefault(hullSize, 1500f);
        float weaponRangePercentTotal = 0f;

        ShipHullSpecAPI spec = stats.getFleetMember() != null ? stats.getFleetMember().getHullSpec() : null;

        if (spec != null) {
            List<SpecialSensorPackage>[] resolved = getResolvedPackages(spec);
            List<SpecialSensorPackage> appliedPackages = resolved[0];

            for (SpecialSensorPackage pkg : appliedPackages) {
                campaignSensorTotal += pkg.campaignBonus;
                combatSightTotal += pkg.combatBonus;
                weaponRangePercentTotal += pkg.weaponRangeMult;
            }
        }

        // Apply campaign sensor range modifier
        stats.getDynamic().getMod(Stats.HRS_SENSOR_RANGE_MOD).modifyFlat(id, campaignSensorTotal);

        // Apply tactical sight radius
        if (isSMod(stats)) {
            stats.getSightRadiusMod().modifyFlat(id, combatSightTotal);
        }

        // Apply specialized Station bonuses
        if (isStation(spec)) {
            stats.getSightRadiusMod().modifyPercent(id, STATION_COMBAT_SIGHT_BONUS_PERCENT);
            weaponRangePercentTotal += STATION_WEAPON_RANGE_BONUS_PERCENT;
        }

        // Apply total weapon range bonus
        if (weaponRangePercentTotal > 0f) {
            stats.getBallisticWeaponRangeBonus().modifyPercent(id, weaponRangePercentTotal);
            stats.getEnergyWeaponRangeBonus().modifyPercent(id, weaponRangePercentTotal);
            stats.getMissileWeaponRangeBonus().modifyPercent(id, weaponRangePercentTotal);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // Station Fleet Weapon Range Aura Logic (+7.5% to all allied ships)
        if (isStation(ship.getHullSpec())) {
            String auraId = "ufp_starfleet_sensors_station_aura_" + ship.getId();
            int owner = ship.getOwner();

            for (ShipAPI other : engine.getShips()) {
                if (other == null || !other.isAlive() || other.isFighter() || other.isHulk()) continue;
                if (other.getOwner() == owner && other != ship) {
                    MutableShipStatsAPI otherStats = other.getMutableStats();
                    otherStats.getBallisticWeaponRangeBonus().modifyPercent(auraId, ALLIED_WEAPON_RANGE_BONUS_PERCENT);
                    otherStats.getEnergyWeaponRangeBonus().modifyPercent(auraId, ALLIED_WEAPON_RANGE_BONUS_PERCENT);
                    otherStats.getMissileWeaponRangeBonus().modifyPercent(auraId, ALLIED_WEAPON_RANGE_BONUS_PERCENT);
                }
            }
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + ufpBaseCampaign.getOrDefault(hullSize, 100f).intValue();
        if (index == 1) return "" + ufpBaseCombat.getOrDefault(hullSize, 1500f).intValue();
        return null;
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize, ShipAPI ship) {
        return getDescriptionParam(index, hullSize);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 3f;
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        // Clamp tooltip extensions strictly to specific ship instances with hull specs
        if (!isForModSpec && ship != null && ship.getHullSpec() != null) {
            ShipHullSpecAPI spec = ship.getHullSpec();

            if (isStation(spec)) {
                tooltip.addSectionHeading("Station Array Tactical Command Matrix", Alignment.MID, opad);
                tooltip.addPara("• Orbital Combat Sight Radius: %s", pad, h, "+" + (int) STATION_COMBAT_SIGHT_BONUS_PERCENT + "%");
                tooltip.addPara("• Station Primary Weapon Range: %s", pad, h, "+" + (int) STATION_WEAPON_RANGE_BONUS_PERCENT + "%");
                tooltip.addPara("• Fleet Coordination Uplink (Allied Weapon Range): %s", pad, h, "+" + ALLIED_WEAPON_RANGE_BONUS_PERCENT + "%");
            }

            List<SpecialSensorPackage>[] resolved = getResolvedPackages(spec);
            List<SpecialSensorPackage> appliedPackages = resolved[0];
            List<SpecialSensorPackage> skippedPackages = resolved[1];

            if (!appliedPackages.isEmpty()) {
                tooltip.addSectionHeading("Active Exploratory Sensor Packages", Alignment.MID, opad);

                for (SpecialSensorPackage pkg : appliedPackages) {
                    tooltip.addPara("• %s: +%s su campaign range, +%s u combat sight, +%s weapon range.",
                            pad,
                            h,
                            pkg.displayName,
                            "" + (int) pkg.campaignBonus,
                            "" + (int) pkg.combatBonus,
                            "" + (int) pkg.weaponRangeMult + "%"
                    );
                }

                if (!skippedPackages.isEmpty()) {
                    StringBuilder skippedNames = new StringBuilder();
                    for (int i = 0; i < skippedPackages.size(); i++) {
                        if (i > 0) skippedNames.append(", ");
                        skippedNames.append(skippedPackages.get(i).displayName);
                    }

                    tooltip.addPara("Sensor Architecture Limit Reached: The following secondary packages exceed the 2-package stacking cap and are inactive: %s",
                            opad,
                            bad,
                            skippedNames.toString()
                    );
                }
            }

            MutableShipStatsAPI stats = ship.getMutableStats();
            float baseCampaign = ufpBaseCampaign.getOrDefault(hullSize, 100f);
            float currentCampaign = stats.getDynamic().getMod(Stats.HRS_SENSOR_RANGE_MOD).computeEffective(baseCampaign);

            tooltip.addPara("Total Campaign Sensor Range Contribution: %s su", opad, h, "" + (int) currentCampaign);
        }
    }
}