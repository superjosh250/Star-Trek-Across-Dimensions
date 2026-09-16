package UFP.data.systems;

import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.awt.Color;

public class SurveyorScanning extends BaseShipSystemScript {

    private static final float SIGHT_RADIUS_MULT = 1.5f;       // 1.5x = +50%
    private static final float SHIELD_RESTORE_PERCENT = 0.20f; // +20% shield HP

    // Used to ensure the shield restore happens once per activation (SELF ONLY)
    private static final String RESTORE_KEY = "ufp_surveyorScanning_restored";

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // Use a modifier id that is unique per *source ship* to prevent collisions
        // when multiple ships have the same system.
        final String modId = id + "_" + ship.getId();

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        // If system is not actually ON, remove bonuses from self + allies and reset latch.
        // (Apply() can be called across activation states; we gate bonuses to ACTIVE only.) [3](https://starsector.fandom.com/wiki/Modding_ship_systems_overview)
        if (state != State.ACTIVE) {
            removeBonusesFromAlliesAndSelf(engine, ship, modId);
            ship.getCustomData().remove(RESTORE_KEY);
            return;
        }

        // SYSTEM IS ACTIVE: apply bonuses to self + allies
        applyBonusesToShip(ship, modId);                 // self
        applyBonusesToAllies(engine, ship, modId);       // allies

        // Shield restore: SELF ONLY, once per activation
        if (!ship.getCustomData().containsKey(RESTORE_KEY)) {
            restoreShieldHP(ship);
            ship.setCustomData(RESTORE_KEY, Boolean.TRUE);
        }

        // Optional status text (player ship only)
        float selfRangeBonus = getBeamRangeBonus(ship);
        engine.maintainStatusForPlayerShip(
                id,
                "graphics/icons/hullsys/sensor_array.png",
                "Surveyor Scanning",
                String.format("+%d%% Sight | +%.0f Beam Range | +20%% Shield HP (self)",
                        (int) ((SIGHT_RADIUS_MULT - 1f) * 100f),
                        selfRangeBonus),
                false
        );
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship == null || engine == null) return;

        final String modId = id + "_" + ship.getId();

        removeBonusesFromAlliesAndSelf(engine, ship, modId);
        ship.getCustomData().remove(RESTORE_KEY);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state != State.ACTIVE) return null;
        if (index == 0) return new StatusData("+50% sight radius (fleet aura)", false);
        if (index == 1) return new StatusData("+Beam range (fleet aura)", false);
        return null;
    }

    // --------------------
    // Ally aura logic
    // --------------------

    private void applyBonusesToAllies(CombatEngineAPI engine, ShipAPI source, String modId) {
        // CombatEngineAPI.getShips() returns the list of ships in the combat engine. [2](https://jaghaimo.github.io/starsector-api/interfacecom_1_1fs_1_1starfarer_1_1api_1_1combat_1_1CombatEngineAPI.html)
        for (ShipAPI other : engine.getShips()) {
            if (other == null) continue;
            if (other == source) continue;
            if (other.isHulk()) continue; // don't buff hulks (common pattern) [1](https://fractalsoftworks.com/forum/index.php?topic=5519.0)
            if (!engine.isEntityInPlay(other)) continue; // avoid entities not in play [2](https://jaghaimo.github.io/starsector-api/interfacecom_1_1fs_1_1starfarer_1_1api_1_1combat_1_1CombatEngineAPI.html)

            // Allied check by owner (same approach as typical AOE system scripts) [1](https://fractalsoftworks.com/forum/index.php?topic=5519.0)
            if (other.getOwner() != source.getOwner()) continue;

            applyBonusesToShip(other, modId);
        }
    }

    private void removeBonusesFromAlliesAndSelf(CombatEngineAPI engine, ShipAPI source, String modId) {
        // Remove from self
        unapplyBonusesFromShip(source, modId);

        // Remove from allies (and any ship that might still have the modifier)
        for (ShipAPI other : engine.getShips()) {
            if (other == null) continue;
            if (!engine.isEntityInPlay(other)) continue;
            unapplyBonusesFromShip(other, modId);
        }
    }

    private void applyBonusesToShip(ShipAPI target, String modId) {
        MutableShipStatsAPI s = target.getMutableStats();
        s.getSightRadiusMod().modifyMult(modId, SIGHT_RADIUS_MULT);

        float rangeBonus = getBeamRangeBonus(target);
        s.getBeamWeaponRangeBonus().modifyFlat(modId, rangeBonus);
    }

    private void unapplyBonusesFromShip(ShipAPI target, String modId) {
        MutableShipStatsAPI s = target.getMutableStats();
        s.getSightRadiusMod().unmodify(modId);
        s.getBeamWeaponRangeBonus().unmodify(modId);
    }

    // --------------------
    // Existing logic
    // --------------------

    private float getBeamRangeBonus(ShipAPI ship) {
        switch (ship.getHullSize()) {
            case FRIGATE: return 500f;
            case DESTROYER: return 600f;
            case CRUISER: return 750f;
            case CAPITAL_SHIP: return 825f;
            default: return 0f;
        }
    }

    private void restoreShieldHP(ShipAPI ship) {
        if (ship.getShield() == null) return;

        float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
        float currentHP = ShieldHitpointManager.getShieldHP(ship);
        float restoreAmount = maxHP * SHIELD_RESTORE_PERCENT;

        ShieldHitpointManager.setShieldHP(ship, currentHP + restoreAmount);

        Global.getCombatEngine().addFloatingText(
                ship.getLocation(),
                "+20% Shield HP",
                12f,
                Color.CYAN,
                ship,
                0.5f,
                0.5f
        );
    }
}