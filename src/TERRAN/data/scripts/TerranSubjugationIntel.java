package TERRAN.data.scripts;

import TERRAN.data.campaign.ids.TerranIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.io.Serializable;
import java.util.Set;

public class TerranSubjugationIntel extends BaseIntelPlugin implements Serializable {
    private static final long serialVersionUID = 1L;

    private final CampaignFleetAPI fleet;
    private final PlanetAPI target;
    private final SectorEntityToken source;
    private final float bountyReward;

    private boolean isRetreating = false;
    private boolean missionSuccessful = false;
    private boolean playerEarnedReward = false;
    private boolean targetSubjugated = false;

    public TerranSubjugationIntel(CampaignFleetAPI fleet, PlanetAPI target, SectorEntityToken source, float bountyReward) {
        this.fleet = fleet;
        this.target = target;
        this.source = source;
        this.bountyReward = bountyReward;
    }

    public CampaignFleetAPI getFleet() {
        return fleet;
    }

    public void triggerRetreat() {
        this.isRetreating = true;
        sendUpdateIfPlayerHasIntel(new Object(), false);
    }

    // Called ONLY when the player participates (solo or assisting) and destroys the fleet
    public void completeMissionPlayerDefeated() {
        if (!missionSuccessful && !targetSubjugated) {
            missionSuccessful = true;
            playerEarnedReward = true;

            FactionAPI defenderFaction = target.getMarket() != null ? target.getMarket().getFaction() : null;
            String defenderName = defenderFaction != null ? defenderFaction.getDisplayName() : "Defense Command";

            Global.getSector().getPlayerFleet().getCargo().getCredits().add(bountyReward);
            Global.getSector().getCampaignUI().addMessage(
                    "Bounty Collected! " + defenderName + " transferred " + Misc.getWithDGS(bountyReward) + " credits for neutralizing the invasion fleet.",
                    Color.GREEN
            );
            endAfterDelay();
        }
    }

    // Called if colony defenses / NPCs destroy the fleet without player involvement
    public void resolveWithoutPlayer() {
        if (!missionSuccessful && !targetSubjugated) {
            missionSuccessful = true;
            playerEarnedReward = false;
            Global.getSector().getCampaignUI().addMessage(
                    "The Terran Subjugation Fleet targeting " + target.getName() + " was neutralized by local defensive forces.",
                    Color.CYAN
            );
            endAfterDelay();
        }
    }

    // Called when the Terran invasion succeeds and colonizes the target world
    public void targetSubjugated() {
        if (!missionSuccessful && !targetSubjugated) {
            targetSubjugated = true;
            playerEarnedReward = false;
            Global.getSector().getCampaignUI().addMessage(
                    "Subjugation Complete: " + target.getName() + " has fallen to the Terran invasion fleet.",
                    Color.RED
            );
            endAfterDelay();
        }
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        if (target != null && target.getMarket() != null) {
            return target.getMarket().getFaction();
        }
        return super.getFactionForUIColors();
    }

    @Override
    public String getName() {
        if (targetSubjugated) {
            return "Subjugation Complete: " + target.getName() + " Captured";
        }
        if (isEnded()) {
            return playerEarnedReward ? "Invasion Fleet Destroyed (Bounty Claimed)" : "Invasion Repelled by Colony Defenses";
        }
        if (isRetreating) {
            return "Defeated Terran Fleet: Retreating";
        }
        return "Terran Subjugation Bounty: " + target.getName();
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color highlight = Misc.getHighlightColor();
        FactionAPI defenderFaction = target.getMarket() != null ? target.getMarket().getFaction() : null;
        FactionAPI attackerFaction = Global.getSector().getFaction(TerranIDS.TERRAN);

        Color defenderColor = defenderFaction != null ? defenderFaction.getColor() : Misc.getTextColor();
        Color attackerColor = attackerFaction != null ? attackerFaction.getColor() : Misc.getNegativeHighlightColor();

        // 1. Terminal / Ended States
        if (targetSubjugated) {
            info.addPara("The Terran Subjugation Fleet successfully bypassed all orbital defenses and forced the capitulation of %s. A permanent military colony has been established.", 10f, highlight, target.getName());
            return;
        }

        if (isEnded()) {
            if (playerEarnedReward) {
                info.addPara("You successfully intercepted and destroyed the Terran Subjugation Fleet. The defending command paid you %s credits.", 10f, highlight, Misc.getWithDGS(bountyReward));
            } else {
                info.addPara("The invasion attempt on " + target.getName() + " was neutralized by system defenses without player intervention.", 10f);
            }
            return;
        }

        if (isRetreating) {
            info.addPara("The fleet's assault failed! Surviving ships are limping back to " + source.getName() + ".", 10f);
            return;
        }

        // 2. Requirement 2: Attacker Movement Directive Notice
        info.addSectionHeading("Terran Empire Movement Directive", attackerColor, attackerColor.darker(), Alignment.MID, 15f);
        info.addPara("A hostile Terran Subjugation Fleet launched from %s is currently en route to bombard and subjugate %s.", 10f, highlight, source.getName(), target.getName());

        // 3. Requirement 1: Defender Bounty Contract Notice
        if (defenderFaction != null) {
            info.addSectionHeading("Defensive Defense Contract (" + defenderFaction.getDisplayName() + ")", defenderColor, defenderColor.darker(), Alignment.MID, 15f);
            info.addPara("The %s high command offers a bounty of %s credits to any mercenary commander who assists in destroying this invasion fleet.", 10f, highlight, defenderFaction.getDisplayName(), Misc.getWithDGS(bountyReward));
        }

        // 4. Target Intelligence Summary
        info.addSectionHeading("Target Intelligence", defenderColor, defenderColor.darker(), Alignment.MID, 15f);
        info.addPara("Target World: " + target.getName(), 5f);
        info.addPara("System Location: " + target.getContainingLocation().getName(), 5f);
        if (target.getMarket() != null) {
            info.addPara("Target Stability: %s", 5f, highlight, String.valueOf((int) target.getMarket().getStabilityValue()));
        }
    }

    // Requirements 3 & 4: Snap map directly to fleet (wherever it is) or planet target
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (fleet != null && fleet.isAlive() && fleet.getContainingLocation() != null) {
            return fleet;
        }
        return target;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "system_bounty");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MILITARY);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add(Tags.INTEL_BOUNTY);
        return tags;
    }
}