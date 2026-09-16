package UFP.data.campaign.industry;

import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import UFP.data.plugins.StarfleetCommandFleets;
import UFP.data.plugins.StarfleetCommandFleets.SpecialFleetSelector;
import UFP.data.plugins.StarfleetCommandFleets.SpecialTaskforceFleetData;
import UFP.data.plugins.StarfleetTaskforceType;

import TERRAN.data.campaign.ids.TerranIDS;

public class StarfleetCommand extends MilitaryBase implements MarketImmigrationModifier, SpecialFleetSelector {

    protected int improvementLevel = 0;

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (market == null || incoming == null || incoming.getWeight() == null) return;

        if (isFunctional()) {
            incoming.getWeight().modifyFlat(getModId(0), 2f, getNameForModifier());
        }
    }

    // =========================================================
    // TASKFORCE & PATROL DELEGATION LOGIC
    // =========================================================
    public int getMaxTaskforces() {
        if (market == null) return 0;

        int size = market.getSize();
        if (size < 7) return 0;
        if (size == 7) return 2;
        if (size == 8 || size == 9) return 4;
        return 6; // size >= 10
    }

    public int getTaskforceCount() {
        return StarfleetTaskforceType.getTaskforceCount(this);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (market == null || !isFunctional()) return;
        if (Float.isNaN(amount) || Float.isInfinite(amount) || amount <= 0f) return;

        // Standard taskforce patrol handling
        returningPatrolValue = StarfleetTaskforceType.handleAdvance(this, getMaxTaskforces(), tracker, returningPatrolValue, amount);

        // Manage Galaxy Wing One, Starfleet Capital Taskforce, & Terran Empire unique spawns
        StarfleetCommandFleets.checkAndSpawnSpecialFleets(this);
    }

    // =========================================================
    // SPECIAL FLEET FILTER / OVERRIDE (SpecialFleetSelector)
    // =========================================================
    @Override
    public boolean shouldSpawnFleetType(String customTag) {
        if (customTag == null || market == null) return false;

        String factionId = market.getFactionId();
        if (factionId == null) return true;

        boolean isTerranModActive = StarfleetCommandFleets.isTerranEmpireActive();
        boolean isTerranFaction = TerranIDS.TERRAN != null && TerranIDS.TERRAN.equals(factionId);

        // Skip/block Terran fleets completely if the mod is not active
        if (!isTerranModActive) {
            if (StarfleetCommandFleets.TAG_TERRAN_GALAXY_WING.equals(customTag) ||
                    StarfleetCommandFleets.TAG_IMPERIAL_FLEET_ONE.equals(customTag)) {
                return false;
            }
            return true; // Fall back to standard fleet behavior
        }

        // Mod IS active: Check faction specific overrides
        if (isTerranFaction) {
            // Terran Faction gets ONLY Terran Fleets, skipping standard Starfleet taskforces
            if (StarfleetCommandFleets.TAG_GALAXY_WING.equals(customTag) ||
                    StarfleetCommandFleets.TAG_STARFLEET_TASKFORCE_ONE.equals(customTag)) {
                return false;
            }
            return StarfleetCommandFleets.TAG_TERRAN_GALAXY_WING.equals(customTag) ||
                    StarfleetCommandFleets.TAG_IMPERIAL_FLEET_ONE.equals(customTag);
        } else {
            // Non-Terran factions get standard fleets, skipping Terran fleets
            if (StarfleetCommandFleets.TAG_TERRAN_GALAXY_WING.equals(customTag) ||
                    StarfleetCommandFleets.TAG_IMPERIAL_FLEET_ONE.equals(customTag)) {
                return false;
            }
            return StarfleetCommandFleets.TAG_GALAXY_WING.equals(customTag) ||
                    StarfleetCommandFleets.TAG_STARFLEET_TASKFORCE_ONE.equals(customTag);
        }
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        if (route == null) return null;

        CampaignFleetAPI fleet = StarfleetTaskforceType.handleSpawnFleet(this, route);
        if (fleet != null) {
            return fleet;
        }
        return super.spawnFleet(route);
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (market == null || !isFunctional() || fleet == null) return;

        RouteData route = RouteManager.getInstance().getRoute(getRouteSourceId(), fleet);

        // If the route exists and is our custom fleet data, handle it safely
        if (route != null) {
            if (route.getCustom() instanceof SpecialTaskforceFleetData custom) {
                if (custom != null && custom.customTag != null) {
                    StarfleetCommandFleets.notifyFleetDespawned(custom.customTag);
                }
            }
            // Safe to call super since we know the route exists
            super.reportFleetDespawnedToListener(fleet, reason, param);
        }
    }

    // =========================================================
    // MAIN APPLY METHOD
    // =========================================================
    @Override
    public void apply() {
        super.apply(); // Runs MilitaryBase baseline setup

        if (market == null || market.getStats() == null) return;

        int size = market.getSize();
        int baseDemand = size <= 5 ? 3 : (size <= 8 ? 4 : (size == 9 ? 5 : 6));
        int baseSupply = size == 3 ? 3 : (size <= 5 ? 4 : (size == 6 ? 5 : (size <= 8 ? 6 : (size == 9 ? 7 : 8))));

        int demandReduction = 0;
        int bonusSupply = 0;
        int patrolBonus = 0;

        float fleetSizeMult = 3.00f;
        float accessibility = 0.25f;
        float stability = 4f;
        float shipBonus = 0.20f;
        float groundDefenseMult = 1.0f;
        float upkeepMult = 1.0f;

        if (improvementLevel >= 1) bonusSupply += 1;
        if (improvementLevel >= 2) demandReduction += 1;
        if (improvementLevel >= 3) fleetSizeMult += 0.10f;
        if (improvementLevel >= 4) shipBonus += 0.20f;
        if (improvementLevel >= 5) {
            stability += 2f;
            fleetSizeMult += 0.30f;
            accessibility += 0.125f;
        }

        if (special != null) {
            if (Items.CRYOARITHMETIC_ENGINE.equals(special.getId())) {
                bonusSupply += 1;
                stability += 1f;
                fleetSizeMult += 0.50f;
                shipBonus += 0.05f;
            } else if (Items.DRONE_REPLICATOR.equals(special.getId())) {
                bonusSupply += 2;
                groundDefenseMult += 0.65f;
                upkeepMult *= 0.90f;
            }
        }

        if (aiCoreId != null) {
            demandReduction += 1;
            switch (aiCoreId) {
                case Commodities.GAMMA_CORE -> patrolBonus += 1;
                case Commodities.ALPHA_CORE -> { bonusSupply += 1; patrolBonus += 1; upkeepMult *= 0.75f; }
                case Commodities.OMEGA_CORE -> { bonusSupply += 2; patrolBonus += 2; upkeepMult *= 0.75f; fleetSizeMult += 0.25f; }
            }
        }

        // Clean up conditional modifiers first to prevent stale stats when items or cores change
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId(1));
        getUpkeep().unmodifyMult(getModId(0));

        market.getStability().modifyFlat(getModId(0), stability, getNameForModifier());
        market.getAccessibilityMod().modifyFlat(getModId(0), accessibility, getNameForModifier());
        getUpkeep().modifyMult(getModId(0), upkeepMult, getNameForModifier());

        // Sanitized fleetSizeMult to prevent NaN in Nexerelin fleet point calculations
        if (Float.isNaN(fleetSizeMult) || Float.isInfinite(fleetSizeMult)) {
            fleetSizeMult = 3.00f;
        }
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMult(getModId(0), fleetSizeMult, getNameForModifier());

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId(0), shipBonus, getNameForModifier());

        if (groundDefenseMult > 1.0f) {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                    .modifyMult(getModId(1), groundDefenseMult, getNameForModifier() + " (Special Item)");
        }

        applyHighCommandBonuses();

        // --- PATROL COUNT CALCULATION ---
        int light = 0, medium = 0, heavy = 0;

        if (size == 3) {
            light = 3; medium = 0; heavy = 0;
        } else if (size == 4) {
            light = 4; medium = 1; heavy = 0;
        } else if (size == 5) {
            light = 5; medium = 2; heavy = 0;
        } else if (size == 6) {
            light = 5; medium = 3; heavy = 2;
        } else if (size == 7) {
            light = 6; medium = 4; heavy = 4;
        } else if (size == 8 || size == 9) {
            light = 7; medium = 5; heavy = 5;
        } else if (size >= 10) {
            light = 8; medium = 6; heavy = 6;
        }

        medium += patrolBonus;
        heavy += patrolBonus;

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(getModId(0), light);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(getModId(0), medium);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).modifyFlat(getModId(0), heavy);

        int finalDemand = Math.max(0, baseDemand - demandReduction);
        demand(Commodities.SUPPLIES, finalDemand);
        demand(Commodities.FUEL, finalDemand);
        demand(Commodities.SHIPS, finalDemand);

        if (DimensionsCrossedIDS.STARFLEET_OFFICERS != null) {
            demand(DimensionsCrossedIDS.STARFLEET_OFFICERS, Math.max(0, (size >= 8 ? 4 : size >= 7 ? 3 : 2) - demandReduction));
        }
        if (DimensionsCrossedIDS.DILITHIUM != null) {
            demand(DimensionsCrossedIDS.DILITHIUM, size >= 9 ? 3 : size >= 6 ? 2 : 1);
        }

        int finalSupply = baseSupply + bonusSupply;
        supply(Commodities.CREW, finalSupply);
        supply(Commodities.MARINES, finalSupply);

        if (DimensionsCrossedIDS.CADETS != null) {
            supply(DimensionsCrossedIDS.CADETS, finalSupply);
        }

        market.addTransientImmigrationModifier(this);
    }

    protected void applyHighCommandBonuses() {
        if (market == null || market.getStats() == null) return;

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD)
                .modifyFlat(getModId(2), OFFICER_PROB_MOD_HIGH_COMMAND, getNameForModifier() + " (High Command)");

        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(getModId(2), 1f + DEFENSE_BONUS_COMMAND, getNameForModifier() + " (High Command)");
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null || market.getStats() == null) return;

        market.getStability().unmodifyFlat(getModId(0));
        market.getAccessibilityMod().unmodifyFlat(getModId(0));
        getUpkeep().unmodifyMult(getModId(0));

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(0));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId(1));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId(2));

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId(2));

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).unmodifyFlat(getModId(0));
    }
}