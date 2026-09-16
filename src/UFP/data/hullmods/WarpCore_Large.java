package UFP.data.hullmods;

import UFP.data.util.EnergySystemManager;
import UFP.data.util.WarpDriveManager;
import UFP.data.util.ESM_Weapons;
import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.Color;

public class WarpCore_Large extends BaseHullMod {

    public static final String HULLMOD_ID = "warp_core_large";
    private static final String SHIELD_MOD_KEY = "UFP_WarpCoreLarge_ShieldHP_Modified";
    private static final String RESTORE_TIMER_KEY = "UFP_WarpCoreLarge_RestoreTimer";

    static {
        WarpDriveManager.registerWarpDriveHullMod(HULLMOD_ID);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (hullSize == ShipAPI.HullSize.CRUISER) {
            ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 0.90f);
        } else if (hullSize == ShipAPI.HullSize.CAPITAL_SHIP) {
            ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 1.025f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(Global.getCombatEngine(), ship);
        if (esm != null) {
            esm.setHasPowerSystem(true);

            if (ship.getHullSize() == ShipAPI.HullSize.CRUISER) {
                // --- CRUISER CORE INTEGRATION ---
                esm.modifyMaxEnergyPercent(HULLMOD_ID, 0.20f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, 0.15f);
                esm.setWeaponEnergyCostMultiplier(1.075f);

                Global.getCombatEngine().applyDamage(
                        ship,
                        ship.getLocation(),
                        5f * amount,
                        DamageType.ENERGY,
                        0f,
                        true,
                        false,
                        ship
                );

                if (!ship.getCustomData().containsKey(SHIELD_MOD_KEY)) {
                    if (ship.getCustomData().containsKey("universal_shield_hp_max")) {
                        float currentMax = ShieldHitpointManager.getMaxShieldHP(ship);
                        float currentHP = ShieldHitpointManager.getShieldHP(ship);

                        ship.getCustomData().put("universal_shield_hp_max", currentMax * 1.05f);
                        ShieldHitpointManager.setShieldHP(ship, currentHP * 1.05f);
                        ship.getCustomData().put(SHIELD_MOD_KEY, true);
                    }
                }

            } else if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
                // --- CAPITAL SHIP CORE INTEGRATION ---
                esm.modifyMaxEnergyPercent(HULLMOD_ID, 0.0f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, 0.025f);
                esm.setWeaponEnergyCostMultiplier(1.02f);

                // Interval-based Shield HP Recovery
                if (ship.getShield() != null && ship.getShield().isOn() && !ShieldHitpointManager.isShieldDisabled(ship)) {

                    // Check for Ambassador variants
                    String vId = ship.getVariant().getHullVariantId();
                    boolean isAmbassador = vId.equals("fed_ambassador") ||
                            vId.equals("fed_ambassador_refit") ||
                            vId.equals("fed_ambassador_retrofit");

                    float interval = isAmbassador ? 25f : 20f;
                    float percent = isAmbassador ? 0.10f : 0.025f;

                    float timer = 0f;
                    if (ship.getCustomData().containsKey(RESTORE_TIMER_KEY)) {
                        timer = (Float) ship.getCustomData().get(RESTORE_TIMER_KEY);
                    }

                    timer += amount;

                    if (timer >= interval) {
                        float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
                        ShieldHitpointManager.setShieldHP(ship, ShieldHitpointManager.getShieldHP(ship) + (maxHP * percent));

                        Global.getCombatEngine().addFloatingText(ship.getLocation(), "+" + (int)(percent * 100) + "% Shield HP",
                                12f, Color.GREEN, ship, 0.5f, 0.5f);

                        timer = 0f;
                    }
                    ship.setCustomData(RESTORE_TIMER_KEY, timer);
                }
            } else {
                esm.unmodifyMaxEnergy(HULLMOD_ID);
                esm.unmodifyRechargeRate(HULLMOD_ID);
                esm.setWeaponEnergyCostMultiplier(1.0f);
            }
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;

        Color goodColor = new Color(120, 255, 140);
        Color badColor = new Color(255, 120, 120);
        Color systemColor = new Color(100, 200, 255);

        tooltip.addSectionHeading("Heavy Warp Core Configuration", Alignment.MID, 10f);
        tooltip.addPara("An industrial-grade matter-antimatter singular reactor designed to sustain high-yield output profiles across massive tonnage hull frameworks.", 6f);

        tooltip.addSectionHeading("Energy & Combat Specifications", Alignment.MID, 10f);
        tooltip.addPara("Power Grid Status: Registers as an active main reactor matrix node within the local ESM core layout.", 4f, systemColor, "main reactor matrix node");

        boolean showCruiser = (ship == null) || (hullSize == ShipAPI.HullSize.CRUISER);
        boolean showCapital = (ship == null) || (hullSize == ShipAPI.HullSize.CAPITAL_SHIP);

        if (showCruiser) {
            tooltip.addSectionHeading("Cruiser Class Specifications", Alignment.MID, 10f);
            tooltip.addPara("• Core Overclock: Massive power grid surge (+20%% ESM Max Energy, +15%% ESM Recharge Rate).", 4f, goodColor, "+20%", "+15%");
            tooltip.addPara("• Flux Optimization: Reduces soft flux accumulation during tactical weapon discharge sequences by 10%%.", 4f, goodColor, "10%");
            tooltip.addPara("• Shield Amplification: Expands structural displacement barrier density (+5%% Max Shield HP).", 4f, goodColor, "+5%");
            tooltip.addPara("• Thermal Bleedout: High-intensity output causes structural containment stress, dealing 5 damage per second directly to the hull and armor matrix.", 4f, badColor, "5 damage per second");
            tooltip.addPara("• System Overhead: Overclocked power lines increase ESM weapon discharge energy consumption by 7.5%%.", 4f, badColor, "7.5%");
        }

        if (showCapital) {
            tooltip.addSectionHeading("Capital Class Specifications", Alignment.MID, 10f);
            tooltip.addPara("• Stabilized Output: Baseline displacement grid configuration (+0%% ESM Max Energy, +2.5%% ESM Recharge Rate).", 4f, goodColor, "+0%", "+2.5%");
            tooltip.addPara("• Reactive Stabilization: Active shield projection matrices periodically feed back into recovery grids (+2.5%% Shield HP every 20s; Ambassador variants restore 10%% every 25s).", 4f, goodColor, "+2.5%", "20s", "10%", "25s");
            tooltip.addPara("• Induction Interference: High mass displacement slightly restricts weapon cooling ventilation (+2.5%% soft flux build-up increase).", 4f, badColor, "+2.5%");
            tooltip.addPara("• System Draw: Massive frame distribution loads increase ESM weapon discharge energy consumption by 2%%.", 4f, badColor, "2%");
        }

        tooltip.addSectionHeading("Campaign Properties", Alignment.MID, 10f);
        tooltip.addPara("Warp Capability: Designated as an active, registered Warp Drive component framework. Satisfies fleet security checks ensuring access to Jump Point travel vector fields.", 4f, goodColor, "Warp Drive", "Jump Point");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        ShipAPI.HullSize size = ship.getHullSize();
        return size == ShipAPI.HullSize.CRUISER || size == ShipAPI.HullSize.CAPITAL_SHIP;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship != null) {
            ShipAPI.HullSize size = ship.getHullSize();
            if (size == ShipAPI.HullSize.FRIGATE || size == ShipAPI.HullSize.DESTROYER || size == ShipAPI.HullSize.FIGHTER) {
                return "Heavy-displacement warp core reactors require a large cruiser or capital ship chassis to handle baseline structural and energetic containment fields.";
            }
        }
        return "Incompatible hull size configuration.";
    }
}