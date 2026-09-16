package UFP.data.campaign.event;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class StartEnterpriseESimulation extends BaseCommandPlugin implements InteractionDialogPlugin {

    private static final String SHIP_NAME_PLAYER = "USS Enterprise E";
    private static final String SHIP_NAME_ENEMY = "Borg Cube";

    protected InteractionDialogAPI dialog;
    protected InteractionDialogPlugin originalPlugin;
    protected Map<String, MemoryAPI> memoryMap;
    protected CampaignFleetAPI simEnemyFleet;
    protected CampaignFleetAPI simPlayerFleet;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.originalPlugin = dialog.getPlugin();

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        clearFlags(globalMemory, targetMemory);

        if (playerOwnsEnterpriseE()) {
            setFlag("$ufp_has_enterprise_e", true, globalMemory, targetMemory);
            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
            return true;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI location = playerFleet != null ? playerFleet.getContainingLocation() : null;

        // Construct Isolated Simulation Player Fleet
        simPlayerFleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Simulation Fleet", true);
        if (location != null) {
            simPlayerFleet.setContainingLocation(location);
            simPlayerFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        // Flagship: USS Enterprise E (Player assigned as captain to force piloting)
        FleetMemberAPI playerShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_ENTERPRISE_E);
        playerShip.setShipName(SHIP_NAME_PLAYER);
        playerShip.setCaptain(Global.getSector().getPlayerPerson());
        simPlayerFleet.getFleetData().addFleetMember(playerShip);
        simPlayerFleet.getFleetData().setFlagship(playerShip);

        // Simulation Escorts
        String[] escorts = {
                DimensionsCrossedIDS.EXCELSIOR,
                DimensionsCrossedIDS.EXCELSIOR,
                DimensionsCrossedIDS.AKIRA,
                DimensionsCrossedIDS.STEAMRUNNER,
                DimensionsCrossedIDS.SABER,
                DimensionsCrossedIDS.DEFIANT
        };

        for (String escortHullId : escorts) {
            FleetMemberAPI escort = Global.getFactory().createFleetMember(FleetMemberType.SHIP, escortHullId);
            PersonAPI escortCaptain = Global.getFactory().createPerson();
            escortCaptain.setPersonality(Personalities.STEADY);
            escort.setCaptain(escortCaptain);
            simPlayerFleet.getFleetData().addFleetMember(escort);
        }

        simPlayerFleet.getFleetData().setSyncNeeded();
        simPlayerFleet.getFleetData().syncIfNeeded();

        // Construct Enemy Fleet: Borg Cube
        simEnemyFleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Borg Threat", true);
        if (location != null) {
            simEnemyFleet.setContainingLocation(location);
            simEnemyFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI enemyCube = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.CUBE_BOBW);
        enemyCube.setShipName(SHIP_NAME_ENEMY);

        PersonAPI cubeCaptain = Global.getFactory().createPerson();
        cubeCaptain.setPersonality(Personalities.RECKLESS);
        enemyCube.setCaptain(cubeCaptain);

        simEnemyFleet.getFleetData().addFleetMember(enemyCube);

        simEnemyFleet.getFleetData().setSyncNeeded();
        simEnemyFleet.getFleetData().syncIfNeeded();

        // Battle Context Options
        BattleCreationContext context = new BattleCreationContext(simPlayerFleet, FleetGoal.ATTACK, simEnemyFleet, FleetGoal.ATTACK);
        context.aiRetreatAllowed = false;
        context.fightToTheLast = true;
        context.enemyDeployAll = true; // Forces enemy AI auto-deployment

        dialog.setPlugin(this);
        dialog.startBattle(context);

        // Remove the real campaign fleet from the generated battle so only simPlayerFleet can deploy
        BattleAPI battle = simPlayerFleet.getBattle();
        if (battle == null && playerFleet != null) {
            battle = playerFleet.getBattle();
        }
        if (battle != null && playerFleet != null && battle.isInvolved(playerFleet)) {
            battle.leave(playerFleet, false);
        }

        return true;
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        dialog.setPlugin(originalPlugin);

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        boolean cubeDestroyed = false;
        boolean anyPlayerShipLost = false;

        if (battleResult != null) {
            EngagementResultForFleetAPI playerResult = null;
            EngagementResultForFleetAPI enemyResult = null;

            if (battleResult.getWinnerResult() != null && battleResult.getWinnerResult().getFleet() == simPlayerFleet) {
                playerResult = battleResult.getWinnerResult();
                enemyResult = battleResult.getLoserResult();
            } else if (battleResult.getLoserResult() != null && battleResult.getLoserResult().getFleet() == simPlayerFleet) {
                playerResult = battleResult.getLoserResult();
                enemyResult = battleResult.getWinnerResult();
            }

            if (playerResult != null) {
                // Returns true if any ship in the player's simulation fleet was destroyed or disabled
                anyPlayerShipLost = !playerResult.getDestroyed().isEmpty() || !playerResult.getDisabled().isEmpty();
            } else {
                anyPlayerShipLost = true;
            }

            if (enemyResult != null) {
                cubeDestroyed = !enemyResult.getDestroyed().isEmpty() || !enemyResult.getDisabled().isEmpty();
            }
        }

        // Must destroy Borg Cube AND suffer 0 friendly losses to succeed
        if (cubeDestroyed && !anyPlayerShipLost) {
            FleetMemberAPI rewardShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_ENTERPRISE_E);
            rewardShip.setShipName(SHIP_NAME_PLAYER);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(rewardShip);
            setFlag("$ufp_sim_ent_e_won", true, globalMemory, targetMemory);
        } else {
            setFlag("$ufp_sim_ent_e_failed", true, globalMemory, targetMemory);
        }

        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    private boolean playerOwnsEnterpriseE() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null) continue;
            if (DimensionsCrossedIDS.USS_ENTERPRISE_E.equalsIgnoreCase(member.getHullId())) return true;
            if (member.getVariant() != null && DimensionsCrossedIDS.USS_ENTERPRISE_E.equalsIgnoreCase(member.getVariant().getHullVariantId())) return true;
            if (SHIP_NAME_PLAYER.equalsIgnoreCase(member.getShipName())) return true;
        }
        return false;
    }

    private void setFlag(String key, boolean value, MemoryAPI globalMem, MemoryAPI targetMem) {
        if (globalMem != null) globalMem.set(key, value);
        if (targetMem != null) targetMem.set(key, value);
    }

    private void clearFlags(MemoryAPI globalMem, MemoryAPI targetMem) {
        String[] keys = {"$ufp_has_enterprise_e", "$ufp_sim_ent_e_won", "$ufp_sim_ent_e_failed"};
        for (String key : keys) {
            if (globalMem != null) globalMem.unset(key);
            if (targetMem != null) targetMem.unset(key);
        }
    }

    @Override public void init(InteractionDialogAPI dialog) { if (originalPlugin != null) originalPlugin.init(dialog); }
    @Override public void optionSelected(String optionText, Object optionData) { if (originalPlugin != null) originalPlugin.optionSelected(optionText, optionData); }
    @Override public void optionMousedOver(String optionText, Object optionData) { if (originalPlugin != null) originalPlugin.optionMousedOver(optionText, optionData); }
    @Override public void advance(float amount) { if (originalPlugin != null) originalPlugin.advance(amount); }
    @Override public Object getContext() { return originalPlugin != null ? originalPlugin.getContext() : null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return memoryMap; }
}