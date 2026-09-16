package UFP.data.campaign.industry;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PrasFleetCommand extends StarfleetOperations {

    private static final String FLEET_TYPE_PRIVATE_GUARD = "$PATROL_PRIVATE_GUARD";

    private static final String KEY_TASKFORCE_ID = "$pras_private_taskforce_id";
    private static final String KEY_PRIVATE_GUARD_ID = "$pras_private_guard_id";
    private static final String KEY_LAST_OFFICER_REFRESH = "$pras_last_officer_refresh";
    private static final String KEY_IS_PRAS_OFFICER = "$pras_is_custom_officer";
    private static final String KEY_GUARD_DESPAWN_TIMER = "$pras_guard_despawn_timer";

    private static final float THREAT_CHECK_RADIUS = 9000f;
    private static final float PRIVATE_GUARD_DESPAWN_DELAY_DAYS = 3f;
    private static final float ACCESSIBILITY_BONUS = 0.75f;
    private static final float SHIP_QUALITY_BONUS = 1.50f;
    private static final float FLEET_QUALITY_BONUS = 0.25f;
    private static final float OFFICER_REFRESH_INTERVAL_DAYS = 30f;

    private static final String OMEGA_CORE_ID = DimensionsCrossedIDS.OMEGA_CORE;

    @Override
    public void apply() {
        super.apply();

        if (market == null || market.getStats() == null || !isFunctional()) return;

        int effectiveSize = getEffectiveMarketSize();

        int baseDemand;
        int baseSupply;

        if (effectiveSize <= 3) {
            baseDemand = 3;
            baseSupply = 3;
        } else if (effectiveSize <= 5) {
            baseDemand = 3;
            baseSupply = 4;
        } else if (effectiveSize == 6) {
            baseDemand = 4;
            baseSupply = 5;
        } else if (effectiveSize <= 8) {
            baseDemand = 4;
            baseSupply = 6;
        } else if (effectiveSize == 9) {
            baseDemand = 5;
            baseSupply = 7;
        } else {
            baseDemand = 6;
            baseSupply = 8;
        }

        int demandMod = (isImproved() && getMaxImprovementLevel() >= 1) ? -1 : 0;
        int supplyMod = (isImproved() && getMaxImprovementLevel() >= 2) ? 1 : 0;

        int finalDemand = Math.max(0, baseDemand + demandMod);
        int finalSupply = Math.max(0, baseSupply + supplyMod);

        // DEMANDS
        demand(Commodities.SUPPLIES, finalDemand);
        demand(Commodities.FUEL, finalDemand);
        demand(Commodities.SHIPS, finalDemand);
        demand(Commodities.SHIP_WEAPONS, finalDemand);

        if (DimensionsCrossedIDS.DILITHIUM != null) {
            demand(DimensionsCrossedIDS.DILITHIUM, finalDemand);
        }
        if (DimensionsCrossedIDS.CADETS != null) {
            demand(DimensionsCrossedIDS.CADETS, Math.max(1, finalDemand - 1));
        }
        demand(Commodities.METALS, finalDemand);
        demand(Commodities.RARE_METALS, Math.max(0, finalDemand - 2));

        // SUPPLIES
        supply(Commodities.SUPPLIES, finalSupply);
        supply(Commodities.CREW, finalSupply);
        supply(Commodities.MARINES, finalSupply);

        supply(Commodities.HAND_WEAPONS, Math.max(1, finalSupply - 2));
        supply(Commodities.HEAVY_MACHINERY, Math.max(1, finalSupply - 2));

        applyDeficitToProduction(1, getMaxDeficit(Commodities.METALS, Commodities.RARE_METALS, Commodities.SUPPLIES), Commodities.HAND_WEAPONS);
        applyDeficitToProduction(1, getMaxDeficit(Commodities.METALS, Commodities.RARE_METALS, Commodities.SUPPLIES), Commodities.HEAVY_MACHINERY);

        // COMBAT FLEET SIZE MULTIPLIER & EXTRA FLEET POINTS (Sanitized for Nexerelin calculations)
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMult(getModId() + "_base", 2.25f, getNameForModifier());

        float extraFP = Math.max(0f, (effectiveSize - 2) * 0.25f);
        if (Float.isNaN(extraFP) || Float.isInfinite(extraFP)) {
            extraFP = 0f;
        }
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyFlat(getModId() + "_size_fp", extraFP, getNameForModifier());

        // Market-wide bonuses
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(), SHIP_QUALITY_BONUS, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId(), FLEET_QUALITY_BONUS, getNameForModifier());

        market.getAccessibilityMod().modifyFlat(getModId(), ACCESSIBILITY_BONUS, getNameForModifier());

        applyStabilityBonus();
        applySpecialItemBonuses(effectiveSize);
        applyOmegaCoreBonusIfPresent();
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null || market.getStats() == null) return;

        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId());
        market.getAccessibilityMod().unmodifyFlat(getModId());

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_base");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(getModId() + "_size_fp");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_cryo");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_alpha");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getOmegaModId());
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId() + "_cryo");

        market.getStability().unmodifyFlat(getModId());
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        if (market == null || market.getStats() == null) return;
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMult(getModId() + "_alpha", 1.15f, "Alpha core (" + getNameForModifier() + ")");
    }

    private void applyOmegaCoreBonusIfPresent() {
        if (market == null || market.getStats() == null) return;
        String core = getAICoreId();
        if (OMEGA_CORE_ID != null && OMEGA_CORE_ID.equals(core)) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getOmegaModId(), 1.30f, "Omega core (" + getNameForModifier() + ")");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getOmegaModId());
        }
    }

    private String getOmegaModId() {
        return getModId() + "_omega";
    }

    private void applySpecialItemBonuses(int size) {
        if (market == null || market.getStats() == null) return;

        // Clear item modifiers first to prevent ghost modifiers when swapping items
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_cryo");
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId() + "_cryo");

        if (special == null) return;

        if (Items.CRYOARITHMETIC_ENGINE.equals(special.getId())) {
            market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId() + "_cryo", 0.50f);
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId() + "_cryo", 1.25f, "Cryoarithmetic Engine");
        } else if (Items.DRONE_REPLICATOR.equals(special.getId())) {
            supply(Commodities.SUPPLIES, size + 1);
            supply(Commodities.HAND_WEAPONS, Math.max(1, size - 1));
            supply(Commodities.HEAVY_MACHINERY, Math.max(1, size - 1));
            demand(Commodities.SUPPLIES, Math.max(1, size - 3));
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return;
        if (Global.getSector().getEconomy().isSimMode() || !isFunctional()) return;
        if (Float.isNaN(amount) || Float.isInfinite(amount) || amount <= 0f) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        if (Float.isNaN(days) || Float.isInfinite(days) || days <= 0f) return;

        ensurePermanentTaskforce();
        updatePrivateGuard(days);
        refreshOfficersIfNeeded(days);
    }

    private void applyStabilityBonus() {
        if (market == null || market.getPrimaryEntity() == null) return;
        SectorEntityToken primary = market.getPrimaryEntity();
        int bonus = 1;

        if (primary instanceof CustomCampaignEntityAPI c) {
            String type = c.getCustomEntityType();
            if (DimensionsCrossedIDS.STATION_PRAS != null && DimensionsCrossedIDS.STATION_PRAS.equals(type)) {
                bonus = 5;
            }
        }
        market.getStability().modifyFlat(getModId(), bonus, getNameForModifier());
    }

    private void ensurePermanentTaskforce() {
        if (market == null || market.getContainingLocation() == null) return;

        String id = market.getMemoryWithoutUpdate().getString(KEY_TASKFORCE_ID);
        CampaignFleetAPI existing = id != null ? (CampaignFleetAPI) market.getContainingLocation().getEntityById(id) : null;

        if (existing == null || !existing.isAlive()) {
            CampaignFleetAPI tf = spawnPrasTaskforce();
            if (tf != null) {
                market.getMemoryWithoutUpdate().set(KEY_TASKFORCE_ID, tf.getId());
            }
        }
    }

    private CampaignFleetAPI spawnPrasTaskforce() {
        if (market == null || market.getPrimaryEntity() == null || market.getContainingLocation() == null) return null;

        String factionId = market.getFactionId();
        if (factionId == null) return null;

        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(factionId, "Pras Private Taskforce", true);
        if (fleet == null) return null;

        fleet.setName("Pras Private Taskforce");

        addShips(fleet,
                DimensionsCrossedIDS.SOVEREIGN_D1, 2, DimensionsCrossedIDS.GALAXY, 5, DimensionsCrossedIDS.AMBASSADOR, 2,
                DimensionsCrossedIDS.EXCELSIOR, 6, DimensionsCrossedIDS.STEAMRUNNER, 6, DimensionsCrossedIDS.DEFIANT, 20
        );

        market.getContainingLocation().addEntity(fleet);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        PersonAPI commander = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(factionId),
                7,
                SkillPickPreference.ANY,
                false,
                null,
                true,
                true,
                -1,
                new Random()
        );
        if (commander != null) {
            commander.setRankId(Ranks.SPACE_ADMIRAL);
            commander.setPostId(Ranks.POST_PATROL_COMMANDER);
            fleet.setCommander(commander);
        }

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, market.getPrimaryEntity(), 999999f, "Patrolling");
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 1f);

        return fleet;
    }

    private void addShips(CampaignFleetAPI fleet, Object... shipIdCountPairs) {
        if (fleet == null || shipIdCountPairs == null) return;

        for (int i = 0; i < shipIdCountPairs.length; i += 2) {
            String variantId = (String) shipIdCountPairs[i];
            if (variantId == null) continue;

            int count = (Integer) shipIdCountPairs[i + 1];
            for (int j = 0; j < count; j++) {
                fleet.getFleetData().addFleetMember(variantId);
            }
        }
        fleet.getFleetData().setSyncNeeded();
        fleet.getFleetData().syncIfNeeded();
    }

    private void updatePrivateGuard(float days) {
        if (market == null || market.getContainingLocation() == null) return;

        boolean threat = isThreatPresent();

        String guardId = market.getMemoryWithoutUpdate().getString(KEY_PRIVATE_GUARD_ID);
        CampaignFleetAPI guard = guardId != null ? (CampaignFleetAPI) market.getContainingLocation().getEntityById(guardId) : null;

        if (threat) {
            if (guard == null || !guard.isAlive()) {
                guard = spawnPrivateGuard();
                if (guard != null) {
                    market.getMemoryWithoutUpdate().set(KEY_PRIVATE_GUARD_ID, guard.getId());
                    guard.getMemoryWithoutUpdate().unset(KEY_GUARD_DESPAWN_TIMER);
                }
            } else {
                guard.getMemoryWithoutUpdate().unset(KEY_GUARD_DESPAWN_TIMER);
            }
        } else {
            if (guard != null && guard.isAlive()) {
                Float timer = (Float) guard.getMemoryWithoutUpdate().get(KEY_GUARD_DESPAWN_TIMER);
                if (timer == null || Float.isNaN(timer) || Float.isInfinite(timer)) {
                    timer = 0f;
                }
                timer += days;

                if (timer >= PRIVATE_GUARD_DESPAWN_DELAY_DAYS) {
                    market.getContainingLocation().removeEntity(guard);
                    market.getMemoryWithoutUpdate().unset(KEY_PRIVATE_GUARD_ID);
                } else {
                    guard.getMemoryWithoutUpdate().set(KEY_GUARD_DESPAWN_TIMER, timer);
                }
            }
        }
    }

    private boolean isThreatPresent() {
        if (market == null || market.getPrimaryEntity() == null || market.getContainingLocation() == null) return false;

        SectorEntityToken primary = market.getPrimaryEntity();
        for (CampaignFleetAPI f : market.getContainingLocation().getFleets()) {
            if (f == null || !f.isAlive()) continue;
            if (f.getFaction() == null || market.getFaction() == null) continue;

            if (!f.getFaction().isHostileTo(market.getFaction())) continue;
            float dist = Misc.getDistance(f.getLocation(), primary.getLocation());
            if (Float.isNaN(dist) || Float.isInfinite(dist)) continue;

            if (dist <= THREAT_CHECK_RADIUS) return true;
        }
        return false;
    }

    private CampaignFleetAPI spawnPrivateGuard() {
        if (market == null || market.getPrimaryEntity() == null || market.getContainingLocation() == null) return null;

        Random random = new Random();
        float combat = 75f + random.nextFloat() * 25f;

        // Passed explicit 1.0f for fleetMult instead of null to prevent Nexerelin pool calculation NPEs
        FleetParamsV3 params = new FleetParamsV3(
                market,
                (Vector2f) null,
                market.getFactionId(),
                1.0f,
                FleetFactory.PatrolType.HEAVY.getFleetType(),
                combat,
                0f, 0f, 0f, 0f, 0f,
                0f
        );
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setName("Private Guard Detachment");
        fleet.getMemoryWithoutUpdate().set(FLEET_TYPE_PRIVATE_GUARD, true);

        market.getContainingLocation().addEntity(fleet);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, market.getPrimaryEntity(), 30f, "Responding to threat");

        return fleet;
    }

    private void refreshOfficersIfNeeded(float days) {
        if (market == null) return;

        Float t = (Float) market.getMemoryWithoutUpdate().get(KEY_LAST_OFFICER_REFRESH);
        if (t == null || Float.isNaN(t) || Float.isInfinite(t)) {
            t = 0f;
        }
        t += days;

        if (t >= OFFICER_REFRESH_INTERVAL_DAYS) {
            t = 0f;
            refreshOfficerCandidates(4);
        }
        market.getMemoryWithoutUpdate().set(KEY_LAST_OFFICER_REFRESH, t);
    }

    private void refreshOfficerCandidates(int targetCount) {
        if (market == null || market.getCommDirectory() == null) return;

        List<PersonAPI> toRemove = new ArrayList<>();
        for (PersonAPI p : market.getPeopleCopy()) {
            if (p != null && p.getMemoryWithoutUpdate() != null && p.getMemoryWithoutUpdate().getBoolean(KEY_IS_PRAS_OFFICER)) {
                toRemove.add(p);
            }
        }

        for (PersonAPI p : toRemove) {
            market.removePerson(p);
            market.getCommDirectory().removePerson(p);
        }

        for (int i = 0; i < targetCount; i++) {
            PersonAPI p = OfficerManagerEvent.createOfficer(
                    Global.getSector().getFaction(market.getFactionId()),
                    5,
                    SkillPickPreference.ANY,
                    false,
                    null,
                    true,
                    true,
                    -1,
                    new Random()
            );
            if (p == null) continue;

            p.setRankId(Ranks.SPACE_COMMANDER);
            p.setPostId(Ranks.POST_OFFICER);
            p.getMemoryWithoutUpdate().set(KEY_IS_PRAS_OFFICER, true);

            market.addPerson(p);
            market.getCommDirectory().addPerson(p, 0);
        }
    }
}