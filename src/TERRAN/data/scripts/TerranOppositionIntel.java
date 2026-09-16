package TERRAN.data.scripts;

import TERRAN.data.campaign.ids.TerranIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.io.Serializable;
import java.util.Set;

public class TerranOppositionIntel extends BaseIntelPlugin implements Serializable, FleetEventListener {
    private static final long serialVersionUID = 1L;

    private final CampaignFleetAPI oppositionFleet;
    private final PlanetAPI targetPlanet;
    private final float bountyReward;

    private boolean isDestroyedByPlayer = false;
    private boolean isEnded = false;

    public TerranOppositionIntel(CampaignFleetAPI oppositionFleet, PlanetAPI targetPlanet, float bountyReward) {
        this.oppositionFleet = oppositionFleet;
        this.targetPlanet = targetPlanet;
        this.bountyReward = bountyReward;

        // Allows Starsector's Intel Manager to track location and clear expired intel
        setPostingLocation(targetPlanet);

        if (oppositionFleet != null) {
            oppositionFleet.addEventListener(this);
        }
    }

    // Auto-clear logic: clears after 90 days if resolved/ended, 365 days max total lifetime
    public float getLifetime() {
        return 365f;
    }

    @Override
    public boolean shouldRemoveIntel() {
        if (isEnded() && getDaysSincePlayerVisible() > 90f) {
            return true;
        }
        return super.shouldRemoveIntel();
    }

    public CampaignFleetAPI getOppositionFleet() {
        return oppositionFleet;
    }

    @Override
    public String getName() {
        if (isEnded) {
            return isDestroyedByPlayer ? "Imperial Bounty Claimed: " + oppositionFleet.getName() : "Target Neutralized: " + oppositionFleet.getName();
        }
        return "Imperial Bounty: " + oppositionFleet.getName();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(TerranIDS.TERRAN);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color highlight = Misc.getHighlightColor();
        FactionAPI terranFaction = Global.getSector().getFaction(TerranIDS.TERRAN);
        FactionAPI fleetFaction = oppositionFleet.getFaction();

        Color terranColor = terranFaction != null ? terranFaction.getColor() : Misc.getTextColor();
        Color fleetColor = fleetFaction != null ? fleetFaction.getColor() : Misc.getHighlightColor();

        String systemName = targetPlanet.getStarSystem() != null ? targetPlanet.getStarSystem().getNameWithLowercaseType() : "Unknown System";

        if (isEnded) {
            if (isDestroyedByPlayer) {
                info.addPara("You successfully intercepted and destroyed %s. The Terran High Command transferred %s credits to your account.",
                        10f, highlight, oppositionFleet.getName(), Misc.getWithDGS(bountyReward));
            } else {
                info.addPara("The fleet %s was destroyed or disbanded without player intervention.", 10f, highlight, oppositionFleet.getName());
            }
            return;
        }

        // Imperial Bounty Contract Details
        info.addSectionHeading("Imperial War Contract (Terran Empire)", terranColor, terranColor.darker(), Alignment.MID, 15f);
        info.addPara("The Terran High Command has issued an individual war contract targeting the hostile force %s, currently responding to the invasion of %s in the %s.",
                10f, highlight, oppositionFleet.getName(), targetPlanet.getName(), systemName);

        info.addPara("Bounty Payout (15%% Fleet Value): %s credits", 10f, highlight, Misc.getWithDGS(bountyReward));
        info.addPara("Condition: Paid ONLY if your fleet engages and destroys this specific task force.", 5f, Misc.getNegativeHighlightColor());

        // Target Fleet Status & Location Details
        info.addSectionHeading("Target Intelligence", fleetColor, fleetColor.darker(), Alignment.MID, 15f);
        info.addPara("Defended World: %s", 5f, highlight, targetPlanet.getName());
        info.addPara("Target Star System: %s", 5f, highlight, systemName);
        info.addPara("Hostile Faction: " + fleetFaction.getDisplayName(), 5f);

        if (oppositionFleet.getContainingLocation() != null) {
            info.addPara("Current Fleet Location: " + oppositionFleet.getContainingLocation().getName(), 5f);
        }
        info.addPara("Fleet Strength: %s Fleet Points", 5f, highlight, String.valueOf(oppositionFleet.getFleetPoints()));
    }

    // Enables "Show on Map" directly for this specific fleet or target planet
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (oppositionFleet != null && oppositionFleet.isAlive() && oppositionFleet.getContainingLocation() != null) {
            return oppositionFleet;
        }
        return targetPlanet;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "system_bounty");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MILITARY);
        tags.add(Tags.INTEL_BOUNTY);
        return tags;
    }

    // --- Battle & Despawn Listeners ---
    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (!isEnded) {
            isEnded = true;
            endAfterDelay();
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (battle == null || isEnded) return;

        boolean fleetDestroyed = !fleet.isAlive() || fleet.getFleetData().getMembersListCopy().isEmpty();
        boolean playerInvolved = battle.isPlayerInvolved();

        if (fleetDestroyed) {
            if (playerInvolved) {
                Object playerSide = battle.getSideFor(Global.getSector().getPlayerFleet());
                Object fleetSide = battle.getSideFor(fleet);

                if (playerSide != null && fleetSide != null && playerSide != fleetSide) {
                    // Paid ONLY if player destroyed the fleet
                    isDestroyedByPlayer = true;
                    isEnded = true;

                    Global.getSector().getPlayerFleet().getCargo().getCredits().add(bountyReward);
                    Global.getSector().getCampaignUI().addMessage(
                            "IMPERIAL BOUNTY COLLECTED: The Terran Empire transferred " + Misc.getWithDGS(bountyReward) + " credits for destroying " + fleet.getName() + "!",
                            Color.GREEN
                    );
                    endAfterDelay();
                    return;
                }
            }
            // Destroyed by NPCs -> no payout
            isEnded = true;
            endAfterDelay();
        }
    }
}