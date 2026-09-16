package UFP.data.campaign.event;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
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

public class StartEnterpriseASimulation extends BaseCommandPlugin implements InteractionDialogPlugin {

    private static final String SHIP_NAME_PLAYER = "USS Excelsior";
    private static final String SHIP_NAME_ENEMY = "USS Enterprise A";

    protected InteractionDialogAPI dialog;
    protected InteractionDialogPlugin originalPlugin;
    protected Map<String, MemoryAPI> memoryMap;
    protected CampaignFleetAPI simEnemyFleet;
    protected CampaignFleetAPI simPlayerFleet;
    protected EnterpriseAScriptWatcher watcher;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.originalPlugin = dialog.getPlugin();

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        clearFlags(globalMemory, targetMemory);

        if (playerOwnsEnterpriseA()) {
            setFlag("$ufp_has_enterprise_a", true, globalMemory, targetMemory);
            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
            return true;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI location = playerFleet != null ? playerFleet.getContainingLocation() : null;

        simPlayerFleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Simulation Fleet", true);
        if (location != null) {
            simPlayerFleet.setContainingLocation(location);
            simPlayerFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI playerShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_EXCELSIOR);
        playerShip.setShipName(SHIP_NAME_PLAYER);
        simPlayerFleet.getFleetData().addFleetMember(playerShip);
        simPlayerFleet.getFleetData().setFlagship(playerShip);

        simEnemyFleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Federation Target", true);
        if (location != null) {
            simEnemyFleet.setContainingLocation(location);
            simEnemyFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI enemyShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_ENTERPRISE_A);
        enemyShip.setShipName(SHIP_NAME_ENEMY);

        PersonAPI captain = Global.getFactory().createPerson();
        captain.setPersonality(Personalities.RECKLESS);
        enemyShip.setCaptain(captain);

        simEnemyFleet.getFleetData().addFleetMember(enemyShip);

        BattleCreationContext context = new BattleCreationContext(simPlayerFleet, FleetGoal.ATTACK, simEnemyFleet, FleetGoal.ATTACK);
        context.aiRetreatAllowed = false;
        context.fightToTheLast = true;
        context.enemyDeployAll = true;

        watcher = new EnterpriseAScriptWatcher();
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

        boolean enemyShieldWasBroken = watcher != null && watcher.wasEnemyShieldBroken();
        boolean playerDestroyed = watcher != null && watcher.wasPlayerDestroyed();
        boolean enemySurvived = watcher != null && !watcher.wasEnemyDestroyed();

        if (watcher != null) {
            watcher.markDone();
        }

        // Win condition: Enemy shield broken AND Enemy survived AND Player ship was destroyed
        if (enemyShieldWasBroken && enemySurvived && playerDestroyed) {
            FleetMemberAPI rewardShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_ENTERPRISE_A);
            rewardShip.setShipName(SHIP_NAME_ENEMY);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(rewardShip);
            setFlag("$ufp_sim_ent_a_won", true, globalMemory, targetMemory);
        } else {
            setFlag("$ufp_sim_ent_a_failed", true, globalMemory, targetMemory);
        }

        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    private boolean playerOwnsEnterpriseA() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null) continue;
            if (DimensionsCrossedIDS.USS_ENTERPRISE_A.equalsIgnoreCase(member.getHullId())) return true;
            if (member.getVariant() != null && DimensionsCrossedIDS.USS_ENTERPRISE_A.equalsIgnoreCase(member.getVariant().getHullVariantId())) return true;
            if (SHIP_NAME_ENEMY.equalsIgnoreCase(member.getShipName())) return true;
        }
        return false;
    }

    private void setFlag(String key, boolean value, MemoryAPI globalMem, MemoryAPI targetMem) {
        if (globalMem != null) globalMem.set(key, value);
        if (targetMem != null) targetMem.set(key, value);
    }

    private void clearFlags(MemoryAPI globalMem, MemoryAPI targetMem) {
        String[] keys = {"$ufp_has_enterprise_a", "$ufp_sim_ent_a_won", "$ufp_sim_ent_a_failed"};
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

    public static class EnterpriseAScriptWatcher implements EveryFrameScript {
        private boolean done = false;
        private boolean pluginAdded = false;
        private final EnterpriseACombatPlugin combatPlugin = new EnterpriseACombatPlugin();

        @Override public boolean isDone() { return done; }
        @Override public boolean runWhilePaused() { return true; }

        @Override
        public void advance(float amount) {
            if (done) return;
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null && !pluginAdded) {
                engine.addPlugin(combatPlugin);
                pluginAdded = true;
            }
        }

        public boolean wasEnemyShieldBroken() { return combatPlugin.isEnemyShieldBroken(); }
        public boolean wasPlayerDestroyed() { return combatPlugin.isPlayerDestroyed(); }
        public boolean wasEnemyDestroyed() { return combatPlugin.isEnemyDestroyed(); }

        public void markDone() { this.done = true; }
    }

    public static class EnterpriseACombatPlugin extends BaseEveryFrameCombatPlugin {
        private boolean enemyShieldBroken = false;
        private boolean playerDestroyed = false;
        private boolean enemyDestroyed = false;

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            for (ShipAPI ship : engine.getShips()) {
                if (ship == null) continue;

                boolean isEnemyShip = SHIP_NAME_ENEMY.equalsIgnoreCase(ship.getName()) ||
                        (ship.getFleetMember() != null && SHIP_NAME_ENEMY.equalsIgnoreCase(ship.getFleetMember().getShipName())) ||
                        (ship.getHullSpec() != null && DimensionsCrossedIDS.USS_ENTERPRISE_A.equalsIgnoreCase(ship.getHullSpec().getHullId()));

                boolean isPlayerShip = SHIP_NAME_PLAYER.equalsIgnoreCase(ship.getName()) ||
                        (ship.getFleetMember() != null && SHIP_NAME_PLAYER.equalsIgnoreCase(ship.getFleetMember().getShipName())) ||
                        (ship.getHullSpec() != null && DimensionsCrossedIDS.USS_EXCELSIOR.equalsIgnoreCase(ship.getHullSpec().getHullId()));

                if (isEnemyShip) {
                    if (!ship.isAlive() || ship.getHitpoints() <= 0f) {
                        enemyDestroyed = true;
                    } else {
                        // Check custom shield HP depletion, custom shield disabled flag, and standard flux overload
                        boolean customShieldDisabled = ShieldHitpointManager.isShieldDisabled(ship);
                        boolean customHpDepleted = ShieldHitpointManager.getMaxShieldHP(ship) > 0f && ShieldHitpointManager.getShieldHP(ship) <= 0f;
                        boolean fluxOverloaded = ship.getFluxTracker() != null && ship.getFluxTracker().isOverloaded();

                        if (customShieldDisabled || customHpDepleted || fluxOverloaded) {
                            enemyShieldBroken = true;
                        }
                    }
                }

                if (isPlayerShip) {
                    if (!ship.isAlive() || ship.getHitpoints() <= 0f) {
                        playerDestroyed = true;
                    }
                }
            }
        }

        public boolean isEnemyShieldBroken() { return enemyShieldBroken; }
        public boolean isPlayerDestroyed() { return playerDestroyed; }
        public boolean isEnemyDestroyed() { return enemyDestroyed; }
    }
}