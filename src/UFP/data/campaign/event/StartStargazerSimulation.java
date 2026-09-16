package UFP.data.campaign.event;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import UFP.data.util.BaseShieldHitpointsRecovery;
import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class StartStargazerSimulation extends BaseCommandPlugin implements InteractionDialogPlugin {

    private static final String SHIP_NAME_PLAYER = "USS Enterprise D";
    private static final String SHIP_NAME_ENEMY = "USS Stargazer";

    protected InteractionDialogAPI dialog;
    protected InteractionDialogPlugin originalPlugin;
    protected Map<String, MemoryAPI> memoryMap;
    protected CampaignFleetAPI simEnemyFleet;
    protected StargazerScriptWatcher watcher;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.originalPlugin = dialog.getPlugin();

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        clearFlags(globalMemory, targetMemory);

        if (playerOwnsStargazer()) {
            setFlag("$ufp_has_stargazer", true, globalMemory, targetMemory);
            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
            return true;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI location = playerFleet != null ? playerFleet.getContainingLocation() : null;

        CampaignFleetAPI simPlayerFleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Simulation Fleet", true);
        if (location != null) {
            simPlayerFleet.setContainingLocation(location);
            simPlayerFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI playerShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_ENTERPRISE_D);
        playerShip.setShipName(SHIP_NAME_PLAYER);
        simPlayerFleet.getFleetData().addFleetMember(playerShip);
        simPlayerFleet.getFleetData().setFlagship(playerShip);

        simEnemyFleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Federation Target", true);
        if (location != null) {
            simEnemyFleet.setContainingLocation(location);
            simEnemyFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI enemyShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_STARGAZER);
        enemyShip.setShipName(SHIP_NAME_ENEMY);

        PersonAPI captain = Global.getFactory().createPerson();
        captain.setPersonality(Personalities.RECKLESS);
        enemyShip.setCaptain(captain);

        simEnemyFleet.getFleetData().addFleetMember(enemyShip);

        BattleCreationContext context = new BattleCreationContext(simPlayerFleet, FleetGoal.ATTACK, simEnemyFleet, FleetGoal.ATTACK);
        context.aiRetreatAllowed = false;
        context.fightToTheLast = true;
        context.enemyDeployAll = true;

        watcher = new StargazerScriptWatcher();
        Global.getSector().addScript(watcher);

        dialog.setPlugin(this);
        dialog.startBattle(context);

        return true;
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        dialog.setPlugin(originalPlugin);

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        boolean shieldWasBroken = watcher != null && watcher.wasShieldBroken();
        if (watcher != null) {
            watcher.markDone();
        }

        boolean enemyDefeated = false;
        boolean playerSurvived = false;

        if (battleResult != null) {
            if (battleResult.getLoserResult() != null && battleResult.getLoserResult().getFleet() == simEnemyFleet) {
                enemyDefeated = !battleResult.getLoserResult().getDestroyed().isEmpty()
                        || !battleResult.getLoserResult().getDisabled().isEmpty();
            } else if (battleResult.getWinnerResult() != null && battleResult.getWinnerResult().getFleet() == simEnemyFleet) {
                enemyDefeated = !battleResult.getWinnerResult().getDestroyed().isEmpty()
                        || !battleResult.getWinnerResult().getDisabled().isEmpty();
            }

            if (battleResult.getWinnerResult() != null && battleResult.getWinnerResult().getFleet() != simEnemyFleet) {
                playerSurvived = true;
            }
        }

        if (enemyDefeated && playerSurvived && shieldWasBroken) {
            FleetMemberAPI rewardShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_STARGAZER);
            rewardShip.setShipName(SHIP_NAME_ENEMY);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(rewardShip);
            setFlag("$ufp_sim_stargazer_won", true, globalMemory, targetMemory);
        } else {
            setFlag("$ufp_sim_stargazer_failed", true, globalMemory, targetMemory);
        }

        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    private boolean playerOwnsStargazer() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null) continue;
            if (DimensionsCrossedIDS.USS_STARGAZER.equalsIgnoreCase(member.getHullId())) return true;
            if (member.getVariant() != null && DimensionsCrossedIDS.USS_STARGAZER.equalsIgnoreCase(member.getVariant().getHullVariantId())) return true;
            if (SHIP_NAME_ENEMY.equalsIgnoreCase(member.getShipName())) return true;
        }
        return false;
    }

    private void setFlag(String key, boolean value, MemoryAPI globalMem, MemoryAPI targetMem) {
        if (globalMem != null) globalMem.set(key, value);
        if (targetMem != null) targetMem.set(key, value);
    }

    private void clearFlags(MemoryAPI globalMem, MemoryAPI targetMem) {
        String[] keys = {"$ufp_has_stargazer", "$ufp_sim_stargazer_won", "$ufp_sim_stargazer_failed"};
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

    public static class StargazerScriptWatcher implements EveryFrameScript {
        private boolean done = false;
        private boolean pluginAdded = false;
        private final StargazerCombatPlugin combatPlugin = new StargazerCombatPlugin();

        @Override
        public boolean isDone() { return done; }

        @Override
        public boolean runWhilePaused() { return true; }

        @Override
        public void advance(float amount) {
            if (done) return;
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null && !pluginAdded) {
                engine.addPlugin(combatPlugin);
                pluginAdded = true;
            }
        }

        public boolean wasShieldBroken() {
            return combatPlugin.wasShieldBroken();
        }

        public void markDone() {
            this.done = true;
        }
    }

    public static class StargazerCombatPlugin extends BaseEveryFrameCombatPlugin {
        private boolean shieldWasBroken = false;
        private boolean handlerInjected = false;

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            ShipAPI playerShip = engine.getPlayerShip();
            if (playerShip == null || !playerShip.isAlive()) return;

            // Silently suppress passive shield-on ticks for Enterprise D in this simulation
            if (!handlerInjected) {
                for (ShipAPI ship : engine.getShips()) {
                    if (ship != null && ship.getHullSpec() != null &&
                            (DimensionsCrossedIDS.USS_ENTERPRISE_D.equalsIgnoreCase(ship.getHullSpec().getHullId())
                                    || DimensionsCrossedIDS.USS_ENTERPRISE_D.equalsIgnoreCase(ship.getHullSpec().getBaseHullId()))) {

                        ship.setCustomData(
                                BaseShieldHitpointsRecovery.CUSTOM_SHIELD_ON_RECOVERY_HANDLER_KEY,
                                (BaseShieldHitpointsRecovery.ShieldOnRecoveryHandler) (s, eng, amt, maxHP) -> true
                        );
                    }
                }
                handlerInjected = true;
            }

            if (ShieldHitpointManager.isShieldDisabled(playerShip)
                    || (playerShip.getFluxTracker() != null && playerShip.getFluxTracker().isOverloaded())) {
                shieldWasBroken = true;
            }
        }

        public boolean wasShieldBroken() {
            return shieldWasBroken;
        }
    }
}