package UFP.data.systems;

import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CommandAuthority extends BaseShipSystemScript {

    // --- CONFIGURATION KEYS ---
    public static final float SELF_BURST_RESTORE_PCT = 0.30f;
    public static final float ALLY_BURST_RESTORE_PCT = 0.10f;

    public static final float SELF_REGEN_PER_SEC = 0.0125f;
    public static final float ALLY_REGEN_PER_SEC = 0.00075f;

    public static final float SELF_SPEED_MULT = 0.50f;
    public static final float SELF_HULL_ARMOR_DAMAGE_MULT = 0.25f;

    public static final float ALLY_RANGE_BONUS_PCT = 0.20f;
    public static final float ALLY_HULL_DAMAGE_MULT = 0.60f;
    public static final float ALLY_ARMOR_DAMAGE_MULT = 0.80f;

    public static final float EFFECT_RANGE = 3000f;
    public static final float ACTIVE_COMMAND_POINTS = 99f;

    public static final boolean DISABLE_WEAPONS_WHEN_ACTIVE = true;
    public static final String AUTOLAUNCH_WING_ID = "shuttle_type11_x3_wing";

    public static final String PLATFORM_VARIANT_ID = "fed_shieldDeploymentPlatform";
    private static final String PLATFORM_ENTITY_KEY = "UFP_CmdAuth_FakeModuleRef";
    private static final String SYSTEM_TRIGGERED_KEY = "UFP_CmdAuth_ActiveBurstDone";
    private static final String NEEDS_CLEANUP_KEY = "UFP_CmdAuth_NeedsCleanup";

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null || !ship.isAlive()) return;

        String uniqueModId = id + "_" + ship.getId();
        boolean isActive = (state == State.IN || state == State.ACTIVE || state == State.OUT);
        float amount = engine.getElapsedInLastFrame();

        if (isActive) {
            ship.getCustomData().put(NEEDS_CLEANUP_KEY, true);

            if (effectLevel > 0f && !ship.getCustomData().containsKey(SYSTEM_TRIGGERED_KEY)) {
                ship.getCustomData().put(SYSTEM_TRIGGERED_KEY, true);

                // 1. SELF BURST
                float selfMax = ShieldHitpointManager.getMaxShieldHP(ship);
                ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + (selfMax * SELF_BURST_RESTORE_PCT));
                engine.addFloatingText(ship.getLocation(), "+" + (int)(SELF_BURST_RESTORE_PCT * 100) + "% Shield HP", 12f, Color.GREEN, ship, 0.5f, 0.5f);

                // 2. ALLY BURST
                float rangeSq = EFFECT_RANGE * EFFECT_RANGE;
                for (ShipAPI other : engine.getShips()) {
                    if (other == ship || other.getOwner() != ship.getOwner() || other.isFighter() || !other.isAlive()) continue;

                    if (Vector2f.sub(ship.getLocation(), other.getLocation(), new Vector2f()).lengthSquared() <= rangeSq) {
                        float allyMax = ShieldHitpointManager.getMaxShieldHP(other);
                        ShieldHitpointManager.setShieldHP(other, ShieldHitpointManager.getShieldHP(other) + (allyMax * ALLY_BURST_RESTORE_PCT));
                        engine.addFloatingText(other.getLocation(), "+" + (int)(ALLY_BURST_RESTORE_PCT * 100) + "% Shield HP", 12f, Color.GREEN, other, 0.5f, 0.5f);

                        if (other.getShield() != null && !other.getShield().isOn()) other.getShield().toggleOn();
                    }
                }

                // 3. Command Points
                try {
                    CombatTaskManagerAPI taskManager = engine.getFleetManager(ship.getOwner()).getTaskManager(false);
                    if (taskManager != null && taskManager.getCommandPointsStat() != null) {
                        float currentCP = taskManager.getCommandPointsStat().getModifiedValue();
                        taskManager.getCommandPointsStat().modifyFlat(uniqueModId, ACTIVE_COMMAND_POINTS - currentCP);
                    }
                } catch (Throwable ignored) {}

                if (ship.getShield() != null && !ship.getShield().isOn()) {
                    ship.getShield().toggleOn();
                }

                autolaunchSupportWings(ship, engine);
            }

            // REGENERATION
            float selfMax = ShieldHitpointManager.getMaxShieldHP(ship);
            ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + (selfMax * SELF_REGEN_PER_SEC * amount));

            stats.getMaxSpeed().modifyMult(id, SELF_SPEED_MULT);
            stats.getHullDamageTakenMult().modifyMult(id, SELF_HULL_ARMOR_DAMAGE_MULT);
            stats.getArmorDamageTakenMult().modifyMult(id, SELF_HULL_ARMOR_DAMAGE_MULT);

            if (DISABLE_WEAPONS_WHEN_ACTIVE) {
                for (WeaponAPI weapon : ship.getAllWeapons()) {
                    weapon.setForceNoFireOneFrame(true);
                }

                if (engine.getPlayerShip() == ship) {
                    String icon = ship.getSystem() != null ? ship.getSystem().getSpecAPI().getIconSpriteName() : "graphics/icons/disabled.png";
                    engine.maintainStatusForPlayerShip(uniqueModId, icon, "COMMAND AUTHORITY", "WEAPONS OFFLINE", true);
                }
            }

            manageFakeModuleSlot(ship, engine, uniqueModId, effectLevel);
            maintainAuraBuffs(ship, engine, uniqueModId, amount);

        } else {
            if (ship.getCustomData().containsKey(NEEDS_CLEANUP_KEY)) {
                cleanUpSystemState(ship, engine, stats, id, uniqueModId);
                ship.getCustomData().remove(NEEDS_CLEANUP_KEY);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (!(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        cleanUpSystemState(ship, Global.getCombatEngine(), stats, id, id + "_" + ship.getId());
        ship.getCustomData().remove(NEEDS_CLEANUP_KEY);
    }

    private void autolaunchSupportWings(ShipAPI host, CombatEngineAPI engine) {
        List<Vector2f> bays = new ArrayList<Vector2f>();
        if (host.getHullSpec() != null) {
            for (WeaponSlotAPI slot : host.getHullSpec().getAllWeaponSlotsCopy()) {
                if (slot.getWeaponType() == WeaponAPI.WeaponType.LAUNCH_BAY) {
                    bays.add(slot.computePosition(host));
                }
            }
        }
        if (bays.isEmpty()) bays.add(new Vector2f(host.getLocation()));

        for (int i = 0; i < 4; i++) {
            try {
                engine.getFleetManager(host.getOwner()).spawnShipOrWing(
                        AUTOLAUNCH_WING_ID, bays.get(i % bays.size()), host.getFacing(), 0f
                );
            } catch (Throwable ignored) {}
        }
    }

    private void manageFakeModuleSlot(ShipAPI host, CombatEngineAPI engine, String uniqueModId, float effectLevel) {
        ShipAPI platform = (ShipAPI) host.getCustomData().get(PLATFORM_ENTITY_KEY);

        if (platform == null || !platform.isAlive() || !engine.isEntityInPlay(platform)) {
            try {
                CombatEntityAPI spawned = engine.getFleetManager(host.getOwner()).spawnShipOrWing(PLATFORM_VARIANT_ID, host.getLocation(), host.getFacing(), 0f);
                if (spawned instanceof ShipAPI) {
                    platform = (ShipAPI) spawned;
                    platform.setShipAI(null);
                    platform.setOwner(host.getOwner());
                    host.getCustomData().put(PLATFORM_ENTITY_KEY, platform);
                }
            } catch (Throwable ignored) {}
        }

        if (platform != null && platform.isAlive()) {
            platform.getLocation().set(host.getLocation());
            platform.getVelocity().set(host.getVelocity());
            platform.setFacing(host.getFacing());
            platform.setAngularVelocity(host.getAngularVelocity());
            platform.setExtraAlphaMult(effectLevel);

            // Prevent platform from taking damage or colliding when main shield is lowered
            platform.setCollisionClass(CollisionClass.SHIP);

            if (platform.getShield() != null) {
                platform.getMutableStats().getShieldUnfoldRateMult().modifyPercent(uniqueModId, 300f);
                if (!platform.getShield().isOn()) platform.getShield().toggleOn();
            }
        }
    }

    private void maintainAuraBuffs(final ShipAPI host, CombatEngineAPI engine, String modId, float amount) {
        float rangeSq = EFFECT_RANGE * EFFECT_RANGE;

        if (host.getSystem() == null) return;
        ShipSystemAPI.SystemState systemState = host.getSystem().getState();
        if (systemState != ShipSystemAPI.SystemState.IN && systemState != ShipSystemAPI.SystemState.ACTIVE) {
            return;
        }

        float pulse = (float) Math.sin(engine.getTotalElapsedTime(true) * 2f) * 0.5f + 0.5f;

        for (ShipAPI ship : engine.getShips()) {
            if (ship == host || ship.getOwner() != host.getOwner() || ship.isFighter() || ship.isDrone() || !ship.isAlive()) continue;

            MutableShipStatsAPI allyStats = ship.getMutableStats();
            if (Vector2f.sub(ship.getLocation(), host.getLocation(), new Vector2f()).lengthSquared() <= rangeSq) {
                allyStats.getBallisticWeaponRangeBonus().modifyPercent(modId, ALLY_RANGE_BONUS_PCT * 100f);
                allyStats.getEnergyWeaponRangeBonus().modifyPercent(modId, ALLY_RANGE_BONUS_PCT * 100f);
                allyStats.getMissileWeaponRangeBonus().modifyPercent(modId, ALLY_RANGE_BONUS_PCT * 100f);
                allyStats.getHullDamageTakenMult().modifyMult(modId, ALLY_HULL_DAMAGE_MULT);
                allyStats.getArmorDamageTakenMult().modifyMult(modId, ALLY_ARMOR_DAMAGE_MULT);

                float allyMax = ShieldHitpointManager.getMaxShieldHP(ship);
                ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + (allyMax * ALLY_REGEN_PER_SEC * amount));

                Color pulseColor = new Color(100, 200, 255, (int)(pulse * 150));
                ship.setJitter(modId, pulseColor, pulse * 2f, 2, 2f);

            } else {
                clearAllyStats(ship, modId);
            }
        }
    }

    private void clearAllyStats(ShipAPI ship, String modId) {
        MutableShipStatsAPI allyStats = ship.getMutableStats();
        allyStats.getBallisticWeaponRangeBonus().unmodify(modId);
        allyStats.getEnergyWeaponRangeBonus().unmodify(modId);
        allyStats.getMissileWeaponRangeBonus().unmodify(modId);
        allyStats.getHullDamageTakenMult().unmodify(modId);
        allyStats.getArmorDamageTakenMult().unmodify(modId);
        ship.setJitter(modId, Color.WHITE, 0f, 0, 0f);
    }

    private void cleanUpSystemState(ShipAPI ship, CombatEngineAPI engine, MutableShipStatsAPI stats, String systemId, String uniqueModId) {
        ship.getCustomData().remove(SYSTEM_TRIGGERED_KEY);

        if (engine == null) return;

        try {
            CombatTaskManagerAPI taskManager = engine.getFleetManager(ship.getOwner()).getTaskManager(false);
            if (taskManager != null && taskManager.getCommandPointsStat() != null) {
                taskManager.getCommandPointsStat().unmodify(uniqueModId);
            }
        } catch (Throwable ignored) {}

        if (ship.getCustomData().containsKey(PLATFORM_ENTITY_KEY)) {
            ShipAPI platform = (ShipAPI) ship.getCustomData().get(PLATFORM_ENTITY_KEY);
            if (platform != null) {
                platform.getMutableStats().getShieldUnfoldRateMult().unmodify(uniqueModId);
                platform.setHulk(true);
                platform.setExtraAlphaMult(0f);
                platform.setCollisionClass(CollisionClass.NONE);

                if (engine.isEntityInPlay(platform)) {
                    engine.removeEntity(platform);
                }
            }
            ship.getCustomData().remove(PLATFORM_ENTITY_KEY);
        }

        stats.getMaxSpeed().unmodify(systemId);
        stats.getHullDamageTakenMult().unmodify(systemId);
        stats.getArmorDamageTakenMult().unmodify(systemId);

        for (ShipAPI combatShip : engine.getShips()) {
            if (combatShip.getOwner() == ship.getOwner()) {
                clearAllyStats(combatShip, uniqueModId);
            }
        }
    }
}