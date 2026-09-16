package UFP.data.systems;

import UFP.data.plugins.SupportCoordinationVisualEffectV2;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import UFP.data.util.ShieldHitpointManager;
import UFP.data.util.UFPTextureManager;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SupportCoordination extends BaseShipSystemScript {

    private static final float RANGE_BONUS = 0.15f;
    private static final float ARMOR_DAMAGE_REDUCTION = 0.10f;
    private static final float HULL_DAMAGE_REDUCTION = 0.15f;
    private static final float ARMOR_RESTORE_PERCENT = 0.30f;
    private static final float HULL_RESTORE_PERCENT = 0.15f;
    private static final float HULL_RESTORE_THRESHOLD = 0.80f;
    private static final float EFFECT_RADIUS = 3750f;

    private static final float SELF_SHIELD_RESTORE_PERCENT = 0.45f;
    private static final float ALLY_SHIELD_RESTORE_PERCENT = 0.12f;
    private static final float SHIELD_DAMAGE_REDUCTION = 0.15f;

    private static boolean texturesPreloaded = false;
    private SupportCoordinationVisualEffectV2 visualEffect;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        CombatEngineAPI engine = Global.getCombatEngine();

        if (state != State.ACTIVE || effectLevel < 1f) {
            cleanup(ship, stats, id);
            return;
        }

        if (!texturesPreloaded) {
            UFPTextureManager.preloadTextures();
            texturesPreloaded = true;
        }

        ship.setCustomData("support_coordination_active", true);

        if (visualEffect == null || visualEffect.isExpired()) {
            visualEffect = new SupportCoordinationVisualEffectV2();
            visualEffect.init(ship);
            engine.addLayeredRenderingPlugin(visualEffect);
        }

        List<ShipAPI> affectedAllies = new ArrayList<>();
        List<ShipAPI> nearbyShips = CombatUtils.getShipsWithinRange(ship.getLocation(), EFFECT_RADIUS);

        for (ShipAPI ally : nearbyShips) {
            if (ally.getOwner() == ship.getOwner() && ally != ship) {
                applyAllyBuffs(ally, id);
                restoreAllyShieldHP(engine, ally);
                affectedAllies.add(ally);
            }
        }

        ship.setCustomData("support_coordination_affected_allies", affectedAllies);
        restoreArmor(ship);
        restoreHull(ship);
        stats.getShieldDamageTakenMult().modifyMult(id, 1f - SHIELD_DAMAGE_REDUCTION);
        ship.setCustomData("support_coordination_shield_boost", true);
        restoreSelfShieldHP(engine, ship);
    }

    private void cleanup(ShipAPI ship, MutableShipStatsAPI stats, String id) {
        if (visualEffect != null) {
            visualEffect.cleanup();
            visualEffect = null;
        }

        stats.getBallisticWeaponRangeBonus().unmodify(id);
        stats.getEnergyWeaponRangeBonus().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);

        ship.removeCustomData("support_coordination_active");
        ship.removeCustomData("support_coordination_visual_effect_added");
        ship.removeCustomData("support_coordination_shield_boost");
        ship.removeCustomData("support_coordination_self_restored");

        List<ShipAPI> affectedAllies = (List<ShipAPI>) ship.getCustomData().get("support_coordination_affected_allies");
        if (affectedAllies != null) {
            for (ShipAPI ally : affectedAllies) {
                ally.removeCustomData("support_coordination_ally_restored");
                ally.removeCustomData("support_coordination_shield_boost");

                MutableShipStatsAPI allyStats = ally.getMutableStats();
                allyStats.getBallisticWeaponRangeBonus().unmodify(id);
                allyStats.getEnergyWeaponRangeBonus().unmodify(id);
                allyStats.getArmorDamageTakenMult().unmodify(id);
                allyStats.getHullDamageTakenMult().unmodify(id);
                allyStats.getShieldDamageTakenMult().unmodify(id);
            }
            ship.removeCustomData("support_coordination_affected_allies");
        }
    }

    private void applyAllyBuffs(ShipAPI ally, String id) {
        MutableShipStatsAPI allyStats = ally.getMutableStats();
        allyStats.getBallisticWeaponRangeBonus().modifyPercent(id, RANGE_BONUS * 100f);
        allyStats.getEnergyWeaponRangeBonus().modifyPercent(id, RANGE_BONUS * 100f);
        allyStats.getArmorDamageTakenMult().modifyMult(id, 1f - ARMOR_DAMAGE_REDUCTION);
        allyStats.getHullDamageTakenMult().modifyMult(id, 1f - HULL_DAMAGE_REDUCTION);
        allyStats.getShieldDamageTakenMult().modifyMult(id, 1f - SHIELD_DAMAGE_REDUCTION);
        ally.setCustomData("support_coordination_shield_boost", true);
    }

    private void restoreAllyShieldHP(CombatEngineAPI engine, ShipAPI ally) {
        Boolean restored = (Boolean) ally.getCustomData().get("support_coordination_ally_restored");
        if (restored == null || !restored) {
            float maxHP = ShieldHitpointManager.getMaxShieldHP(ally);
            float currentHP = ShieldHitpointManager.getShieldHP(ally);
            float restoreAmount = maxHP * ALLY_SHIELD_RESTORE_PERCENT;
            ShieldHitpointManager.setShieldHP(ally, Math.min(currentHP + restoreAmount, maxHP));
            engine.addFloatingText(ally.getLocation(), "+" + Math.round(restoreAmount) + " SP (support)", 14f, Color.CYAN, ally, 0.5f, 0.5f);
            ally.setCustomData("support_coordination_ally_restored", true);
        }
    }

    private void restoreSelfShieldHP(CombatEngineAPI engine, ShipAPI ship) {
        Boolean restoredSelf = (Boolean) ship.getCustomData().get("support_coordination_self_restored");
        if (restoredSelf == null || !restoredSelf) {
            float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
            float currentHP = ShieldHitpointManager.getShieldHP(ship);
            float restoreAmount = maxHP * SELF_SHIELD_RESTORE_PERCENT;
            ShieldHitpointManager.setShieldHP(ship, Math.min(currentHP + restoreAmount, maxHP));
            engine.addFloatingText(ship.getLocation(), "+" + Math.round(restoreAmount) + " SP (self)", 16f, Color.GREEN, ship, 0.5f, 0.5f);
            ship.setCustomData("support_coordination_self_restored", true);
        }
    }

    private void restoreArmor(ShipAPI ship) {
        ArmorGridAPI armorGrid = ship.getArmorGrid();
        float[][] armorCells = armorGrid.getGrid();
        float maxCellArmor = armorGrid.getMaxArmorInCell();
        for (int x = 0; x < armorCells.length; x++) {
            for (int y = 0; y < armorCells[0].length; y++) {
                float currentArmor = armorCells[x][y];
                float restoreAmount = (maxCellArmor - currentArmor) * ARMOR_RESTORE_PERCENT;
                armorCells[x][y] = Math.min(currentArmor + restoreAmount, maxCellArmor);
            }
        }
        ship.syncWithArmorGridState();
    }

    private void restoreHull(ShipAPI ship) {
        float currentHull = ship.getHitpoints();
        float maxHull = ship.getMaxHitpoints();
        if (currentHull < maxHull * HULL_RESTORE_THRESHOLD) {
            float restoreAmount = currentHull * HULL_RESTORE_PERCENT;
            ship.setHitpoints(Math.min(currentHull + restoreAmount, maxHull));
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Cleanup handled in apply()
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state != State.ACTIVE || effectLevel < 1f) return null;

        if (index == 0) {
            return new StatusData("Allies: +15% range, -10% armor dmg, -15% hull/shield dmg", false);
        }
        if (index == 1) {
            return new StatusData("Self: restoring armor, hull, and shield HP", false);
        }
        if (index == 2) {
            float selfUpRegen = 7f;
            float selfDownRegen = 25f;
            float allyUpRegen = 10f;
            float allyDownRegen = 30f;

            String regenText = String.format(
                    "Boosting shield regen: Self [Up: %.0f%%/5s, Down: %.0f%%/10s], Allies [Up: %.0f%%/5s, Down: %.0f%%/10s]",
                    selfUpRegen, selfDownRegen, allyUpRegen, allyDownRegen
            );

            return new StatusData(regenText, false);
        }
        return null;
    }
}