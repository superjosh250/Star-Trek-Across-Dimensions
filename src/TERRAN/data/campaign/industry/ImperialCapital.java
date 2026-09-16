package TERRAN.data.campaign.industry;

import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import TERRAN.data.plugins.ImperialCapitalFleetPatrol;
import TERRAN.data.plugins.ImperialCapitalFleetPatrol.ImperialCapitalFleetData;
import UFP.data.campaign.industry.StarfleetHQ;

public class ImperialCapital extends StarfleetHQ {

    // --- FACTION & PLAYER BUILDING RESTRICTIONS OVERRIDE ---
    @Override
    public String getUnavailableReason() {
        if (market.isPlayerOwned()) {
            return "Imperial Capital cannot be built on player-owned colonies.";
        }
        String factionId = market.getFactionId();
        for (var m : com.fs.starfarer.api.Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId()) && m.hasIndustry(getId())) {
                return "Only one Imperial Capital is permitted per faction.";
            }
        }
        return super.getUnavailableReason();
    }

    // =========================================================
    // ADVANCE TICK & FLEET MANAGEMENT OVERRIDE
    // =========================================================
    @Override
    public void advance(float amount) {
        // Run BaseIndustry advance (skipping StarfleetHQ's advance to avoid spawning HQ fleets)
        super.advance(amount);
        if (!isFunctional()) return;

        // Hook into the Terran capital fleet logic
        ImperialCapitalFleetPatrol.checkAndSpawnDefenseForce(this);
    }

    // =========================================================
    // ROUTE FLEET SPAWNER & LISTENER OVERRIDES
    // =========================================================
    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        if (!(route.getCustom() instanceof ImperialCapitalFleetData)) {
            return null;
        }

        ImperialCapitalFleetData custom = (ImperialCapitalFleetData) route.getCustom();

        // Spawn the Imperial Suppression Battleforce with Terran variants
        CampaignFleetAPI fleet = ImperialCapitalFleetPatrol.createImperialCapitalFleet(market, custom.fleetName);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.addEventListener(this);

        market.getContainingLocation().addEntity(fleet);
        fleet.setFacing((float) Math.random() * 360f);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);

        custom.spawnFP = fleet.getFleetPoints();

        return fleet;
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (!isFunctional()) return;

        String sid = market.getId() + "_imperial_capital_defense";
        RouteData route = RouteManager.getInstance().getRoute(sid, fleet);

        if (route != null && route.getCustom() instanceof ImperialCapitalFleetData) {
            ImperialCapitalFleetData custom = (ImperialCapitalFleetData) route.getCustom();
            boolean wasDestroyed = (reason == FleetDespawnReason.DESTROYED_BY_BATTLE);

            // Notify the Terran fleet controller of despawn / destroyed cooldowns
            ImperialCapitalFleetPatrol.notifyFleetDespawned(custom.customTag, wasDestroyed);
        }
    }
}