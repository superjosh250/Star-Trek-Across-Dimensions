package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase.PatrolFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.BattleAPI;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class McKinleyStation extends DrydockAdvanced implements FleetEventListener {

    public static final String MEMORY_KEY_MCKINLEY_BUFFED = "$ufp_mckinley_patrol_buffed";
    protected float returningPatrolValue = 0f;

    @Override
    public void apply() {
        super.apply();

        if (!isFunctional()) return;

        // --- SUBMARKET INTEGRATION ---
        if (market != null && !market.hasSubmarket(DimensionsCrossedIDS.SUBMARKET_MCKINLEY)) {
            market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_MCKINLEY);
        }

        // --- OVERRIDE FLEET SIZE MULTIPLIER FOR MCKINLEY ---
        // Sets McKinley's specific base multiplier to 1.75x
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMult(getModId() + "_fleet_size", 1.75f, getNameForModifier());

        // System synergy stack (4+ drydocks in system) remains compatible if met
        if (getDrydockCountInSystem() >= 4) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId() + "_system_4_bonus", 1.25f, getNameForModifier() + " (System Drydock Network)");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .unmodifyMult(getModId() + "_system_4_bonus");
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market != null && market.hasSubmarket(DimensionsCrossedIDS.SUBMARKET_MCKINLEY)) {
            market.removeSubmarket(DimensionsCrossedIDS.SUBMARKET_MCKINLEY);
        }

        // Clean up McKinley fleet size modifier
        if (market != null) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_fleet_size");
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_system_4_bonus");
        }
    }

    // --- FLEET BUFF IMPLEMENTATION ---
    public CampaignFleetAPI spawnFleet(RouteData route) {
        if (route.getCustom() instanceof PatrolFleetData) {
            PatrolFleetData custom = (PatrolFleetData) route.getCustom();
            PatrolType type = custom.type;
            Random random = route.getRandom();
            if (random == null) random = new Random();

            // 1. Create base patrol fleet using MilitaryBase's static generator method
            CampaignFleetAPI fleet = MilitaryBase.createPatrol(type, market.getFactionId(), route, market, null, random);

            if (fleet == null || fleet.isEmpty()) return null;

            // 2. Append McKinley bonus ships based on patrol type
            if (type == PatrolType.FAST) {
                // Light Fleets: 1 random ship from light pool
                WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
                picker.add(DimensionsCrossedIDS.NEW_ORLEANS);
                picker.add(DimensionsCrossedIDS.SPRINGFIELD);
                picker.add(DimensionsCrossedIDS.CHEYENNE);
                picker.add(DimensionsCrossedIDS.CHALLENGER);

                String variantId = picker.pick();
                if (variantId != null) {
                    addBonusShipToFleet(fleet, variantId);
                }

            } else if (type == PatrolType.COMBAT) {
                // Medium Fleets: 1 random variant from medium pool
                WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
                picker.add(DimensionsCrossedIDS.NEBULA);
                picker.add(DimensionsCrossedIDS.NEBULA_SENSOR);
                picker.add(DimensionsCrossedIDS.NEBULA_WARP);
                picker.add(DimensionsCrossedIDS.NEBULA_NX);

                String variantId = picker.pick();
                if (variantId != null) {
                    addBonusShipToFleet(fleet, variantId);
                }

            } else if (type == PatrolType.HEAVY) {
                // Heavy Fleets: 1 guaranteed Galaxy
                addBonusShipToFleet(fleet, DimensionsCrossedIDS.GALAXY);

                // Heavy Fleets: 1 additional random ship from medium pool
                WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
                picker.add(DimensionsCrossedIDS.NEBULA);
                picker.add(DimensionsCrossedIDS.NEBULA_SENSOR);
                picker.add(DimensionsCrossedIDS.NEBULA_WARP);
                picker.add(DimensionsCrossedIDS.NEBULA_NX);

                String variantId = picker.pick();
                if (variantId != null) {
                    addBonusShipToFleet(fleet, variantId);
                }
            }

            // 3. Complete vanilla MilitaryBase fleet initialization
            fleet.addEventListener(this);
            market.getContainingLocation().addEntity(fleet);
            fleet.setFacing((float) Math.random() * 360f);
            fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

            fleet.addScript(new PatrolAssignmentAIV4(fleet, route));
            fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);

            if (custom.spawnFP <= 0) {
                custom.spawnFP = fleet.getFleetPoints();
            }

            return fleet;
        }

        return null;
    }

    public String getRouteSourceId() {
        return getMarket().getId() + "_" + getId();
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (!isFunctional()) return;

        if (reason == FleetDespawnReason.REACHED_DESTINATION) {
            RouteData route = RouteManager.getInstance().getRoute(getRouteSourceId(), fleet);
            if (route != null && route.getCustom() instanceof PatrolFleetData) {
                PatrolFleetData custom = (PatrolFleetData) route.getCustom();
                if (custom.spawnFP > 0) {
                    // Ensure proper floating point division between fleet FP and initial spawn FP
                    float fraction = (float) fleet.getFleetPoints() / (float) custom.spawnFP;
                    returningPatrolValue += fraction;
                }
            }
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        // Optional: add custom logic when patrol battles occur
    }

    /**
     * Helper method to instantiate and append a free ship variant to a campaign fleet.
     */
    private void addBonusShipToFleet(CampaignFleetAPI fleet, String variantId) {
        try {
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(member);
        } catch (Throwable t) {
            Global.getLogger(McKinleyStation.class).error("Failed to add bonus ship variant '" + variantId + "' to patrol fleet", t);
        }
    }

    // --- TOOLTIP DISPLAY ---
    @Override
    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        return true;
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            Color h = Misc.getHighlightColor();
            float opad = 10f;

            tooltip.addPara("Establishes the %s submarket on the market.", opad, h, "McKinley Station");

            Industry starfleetOps = market.getIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS);
            if (starfleetOps != null && starfleetOps.isFunctional()) {
                tooltip.addPara("Augmenting %s patrols:", 4f, h, "Starfleet Operations");
                tooltip.addPara("  • Medium Patrols: +1 free %s", 2f, h, "Nebula");
                tooltip.addPara("  • Heavy Patrols: +1 free %s", 2f, h, "Galaxy");
                tooltip.addPara("  • Taskforce Patrols: +1 bonus %s", 2f, h, "Galaxy");
            }
        }
    }
}