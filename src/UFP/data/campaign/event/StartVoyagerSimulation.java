package UFP.data.campaign.event;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
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

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import java.util.List;
import java.util.Map;

public class StartVoyagerSimulation extends BaseCommandPlugin implements InteractionDialogPlugin {

    private static final String PLAYER_SHIP_VARIANT = DimensionsCrossedIDS.USS_VOYAGER;
    private static final String ENEMY_SHIP_VARIANT = DimensionsCrossedIDS.CUBE_BOBW;
    private static final String SHIP_NAME = "USS Voyager";

    protected InteractionDialogAPI dialog;
    protected InteractionDialogPlugin originalPlugin;
    protected Map<String, MemoryAPI> memoryMap;
    protected CampaignFleetAPI simEnemyFleet;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.originalPlugin = dialog.getPlugin();

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        clearFlags(globalMemory, targetMemory);

        if (playerOwnsVoyager()) {
            setFlag("$ufp_has_voyager", true, globalMemory, targetMemory);
            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
            return true;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI location = playerFleet != null ? playerFleet.getContainingLocation() : null;

        // Create isolated player simulation fleet (1 ship only)
        CampaignFleetAPI simPlayerFleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Simulation Fleet", true);
        if (location != null) {
            simPlayerFleet.setContainingLocation(location);
            simPlayerFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI playerShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, PLAYER_SHIP_VARIANT);
        playerShip.setShipName(SHIP_NAME);
        simPlayerFleet.getFleetData().addFleetMember(playerShip);
        simPlayerFleet.getFleetData().setFlagship(playerShip);

        // Spawn 2 enemy Borg Cubes
        simEnemyFleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Borg Collective", true);
        if (location != null) {
            simEnemyFleet.setContainingLocation(location);
            simEnemyFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        for (int i = 0; i < 2; i++) {
            FleetMemberAPI enemy = Global.getFactory().createFleetMember(FleetMemberType.SHIP, ENEMY_SHIP_VARIANT);
            PersonAPI captain = Global.getFactory().createPerson();
            captain.setPersonality(Personalities.RECKLESS);
            enemy.setCaptain(captain);
            simEnemyFleet.getFleetData().addFleetMember(enemy);
        }

        BattleCreationContext context = new BattleCreationContext(simPlayerFleet, FleetGoal.ATTACK, simEnemyFleet, FleetGoal.ATTACK);
        context.aiRetreatAllowed = false;
        context.fightToTheLast = true;
        context.enemyDeployAll = true;

        dialog.setPlugin(this);
        dialog.startBattle(context);

        return true;
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        dialog.setPlugin(originalPlugin);

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        int destroyedCubes = 0;

        if (battleResult != null) {
            if (battleResult.getLoserResult() != null && battleResult.getLoserResult().getFleet() == simEnemyFleet) {
                destroyedCubes = battleResult.getLoserResult().getDestroyed().size()
                        + battleResult.getLoserResult().getDisabled().size();
            } else if (battleResult.getWinnerResult() != null && battleResult.getWinnerResult().getFleet() == simEnemyFleet) {
                destroyedCubes = battleResult.getWinnerResult().getDestroyed().size()
                        + battleResult.getWinnerResult().getDisabled().size();
            }
        }

        // Win Condition: Must destroy both (2) Borg Cubes
        if (destroyedCubes >= 2) {
            FleetMemberAPI rewardShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, PLAYER_SHIP_VARIANT);
            rewardShip.setShipName(SHIP_NAME);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(rewardShip);
            setFlag("$ufp_sim_voyager_won", true, globalMemory, targetMemory);
        } else {
            setFlag("$ufp_sim_voyager_failed", true, globalMemory, targetMemory);
        }

        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    private void setFlag(String key, boolean value, MemoryAPI globalMem, MemoryAPI targetMem) {
        if (globalMem != null) globalMem.set(key, value);
        if (targetMem != null) targetMem.set(key, value);
    }

    private void clearFlags(MemoryAPI globalMem, MemoryAPI targetMem) {
        String[] keys = {"$ufp_has_voyager", "$ufp_sim_voyager_won", "$ufp_sim_voyager_failed"};
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

    private boolean playerOwnsVoyager() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null) continue;
            if (PLAYER_SHIP_VARIANT.equalsIgnoreCase(member.getHullId())) return true;
            if (member.getVariant() != null && PLAYER_SHIP_VARIANT.equalsIgnoreCase(member.getVariant().getHullVariantId())) return true;
            if (SHIP_NAME.equalsIgnoreCase(member.getShipName())) return true;
        }
        return false;
    }
}