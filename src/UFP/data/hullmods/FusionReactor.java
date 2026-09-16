package UFP.data.hullmods;

import UFP.data.util.BaseShieldHitpointsRecovery;
import UFP.data.util.EnergySystemManager;
import UFP.data.util.ESM_Weapons;
import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;

public class FusionReactor extends BaseHullMod {

    public static final String HULLMOD_ID = "fusion_reactor";

    private static final float WEAPON_ENERGY_INCREASE = 1.075f;
    private static final float SOFT_FLUX_REDUCTION = 0.95f;
    private static final float SHIELD_DAMAGE_MULT = 0.975f;

    private static final float SPEED_BONUS = 25f;
    private static final float ACCEL_BONUS = 5f;

    private static final BaseShieldHitpointsRecovery.ShieldOnRecoveryHandler FUSION_RECOVERY_HANDLER =
            (ship, engine, amount, maxHP) -> {

                if (ship == null || ship.getShield() == null
                        || ShieldHitpointManager.isShieldDisabled(ship)) {
                    return true;
                }

                float hp = ShieldHitpointManager.getShieldHP(ship);

                ShieldHitpointManager.setShieldHP(
                        ship,
                        hp + (maxHP * 0.015f * amount)
                );

                return true;
            };

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize,
                                               MutableShipStatsAPI stats,
                                               String id) {

        // -5% flux buildup
        ESM_Weapons.modifyWeaponFluxCostMult(stats, id, SOFT_FLUX_REDUCTION);

        // -2.5% shield damage taken
        stats.getShieldDamageTakenMult().modifyMult(id, SHIELD_DAMAGE_MULT);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {

        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm =
                EnergySystemManager.getOrCreateTracker(
                        Global.getCombatEngine(),
                        ship
                );

        if (esm != null) {
            esm.setHasPowerSystem(true);

            // +7.5% ESM weapon energy consumption
            esm.setWeaponEnergyCostMultiplier(WEAPON_ENERGY_INCREASE);
        }

        ship.setCustomData(
                BaseShieldHitpointsRecovery.CUSTOM_SHIELD_ON_RECOVERY_HANDLER_KEY,
                FUSION_RECOVERY_HANDLER
        );

        boolean active =
                ship.getShield() != null
                        && ship.getShield().isOn()
                        && ship.getFluxTracker() != null
                        && ship.getFluxTracker().getFluxLevel() < 0.30f;

        if (active) {
            ship.getMutableStats().getMaxSpeed()
                    .modifyFlat(HULLMOD_ID, SPEED_BONUS);

            ship.getMutableStats().getAcceleration()
                    .modifyFlat(HULLMOD_ID, ACCEL_BONUS);
        } else {
            ship.getMutableStats().getMaxSpeed()
                    .unmodify(HULLMOD_ID);

            ship.getMutableStats().getAcceleration()
                    .unmodify(HULLMOD_ID);
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip,
                                          ShipAPI.HullSize hullSize,
                                          ShipAPI ship,
                                          float width,
                                          boolean isForModSpec) {

        Color good = new Color(120, 255, 140);
        Color system = new Color(100, 200, 255);

        tooltip.addSectionHeading("Fusion Reactor Specifications",
                Alignment.MID, 10f);

        tooltip.addPara(
                "A compact high-output fusion reactor designed to provide continuous power generation without warp-drive capability.",
                6f
        );

        tooltip.addPara(
                "Weapon energy consumption increased by 7.5%%.",
                4f, good, "7.5%"
        );

        tooltip.addPara(
                "Reduces soft flux buildup by 5%%.",
                4f, good, "5%"
        );

        tooltip.addPara(
                "Reduces shield damage taken by 2.5%%.",
                4f, good, "2.5%"
        );

        tooltip.addPara(
                "While shields are active, Shield HP regeneration is increased by 1.5%% per second.",
                4f, good, "1.5%"
        );

        tooltip.addPara(
                "While shields are active and flux remains below 30%%: +25 maximum speed and +5 acceleration.",
                4f, good, "+25", "+5", "30%"
        );

        tooltip.addPara(
                "Provides a valid Energy System Manager power source.",
                4f, system, "Energy System Manager"
        );
    }
}