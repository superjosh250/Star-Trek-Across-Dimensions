package UFP.data.hullmods;

import UFP.data.util.EnergySystemManager;
import UFP.data.util.WarpDriveManager;
import UFP.data.util.ESM_Weapons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;

public class WarpCore_Intrepid extends BaseHullMod {

    public static final String HULLMOD_ID = "ufp_warp_core_intrepid";
    private static final String SHIELD_RECHARGE_MOD_ID = HULLMOD_ID + "_shield_recharge";

    static {
        WarpDriveManager.registerWarpDriveHullMod(HULLMOD_ID);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize,
                                               MutableShipStatsAPI stats,
                                               String id) {

        // Standardized shared penalty: Engine performance reduction (-7.5% max speed)
        stats.getMaxSpeed().modifyPercent(id, -7.5f);

        // Conditional checks for target hull size scaling parameters
        if (hullSize == ShipAPI.HullSize.DESTROYER) {
            // Destroyer soft flux accumulation degradation penalty (+7.5%)
            ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 1.075f);
        } else if (hullSize == ShipAPI.HullSize.CRUISER) {
            // Cruiser soft flux accumulation degradation penalty (+5.0%)
            ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 1.05f);
        }

        // Apply dedicated campaign propulsion enhancements if matched to the specific spaceframe
        if (stats.getVariant() != null && stats.getVariant().getHullSpec() != null) {
            String hullId = stats.getVariant().getHullSpec().getHullId();

            if ("fed_intrepid".equals(hullId) || "terran_intrepid".equals(hullId) || "fed_intrepid_armored".equals(hullId)
            || "fed_intrepid_voyager".equals(hullId)) {
                stats.getMaxBurnLevel().modifyFlat(id, 2f);
            }
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {

        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm =
                EnergySystemManager.getOrCreateTracker(Global.getCombatEngine(), ship);

        if (esm != null) {

            // Declares this system layout explicitly as a primary operational reactor core
            esm.setHasPowerSystem(true);

            ShipAPI.HullSize size = ship.getHullSize();

            // Apply size-dependent energy constraints and recharge penalties
            if (size == ShipAPI.HullSize.DESTROYER) {
                esm.modifyMaxEnergyPercent(HULLMOD_ID, -0.075f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, -0.10f);
            } else if (size == ShipAPI.HullSize.CRUISER) {
                esm.modifyMaxEnergyPercent(HULLMOD_ID, -0.125f);
                esm.modifyRechargeRatePercent(HULLMOD_ID, -0.10f);
            } else {
                esm.unmodifyMaxEnergy(HULLMOD_ID);
                esm.unmodifyRechargeRate(HULLMOD_ID);
            }

            // Framework Action: +2% ESM energy recharge rate amplification while the shield is active
            String hullId = ship.getHullSpec().getHullId();
            String baseHullId = ship.getHullSpec().getBaseHullId();

            boolean isIntrepid =
                    "fed_intrepid".equals(hullId)
                            || "terran_intrepid".equals(hullId)
                            || "fed_intrepid_armored".equals(hullId)
                            || "fed_intrepid_voyager".equals(hullId)
                            || "fed_intrepid".equals(baseHullId)
                            || "terran_intrepid".equals(baseHullId)
                            || "fed_intrepid_armored".equals(baseHullId)
                            || "fed_intrepid_voyager".equals(baseHullId);

            if (isIntrepid) {
                if (ship.getShield() != null && ship.getShield().isOn()) {
                    esm.modifyRechargeRatePercent(SHIELD_RECHARGE_MOD_ID, 0.02f);
                } else {
                    esm.unmodifyRechargeRate(SHIELD_RECHARGE_MOD_ID);
                }
            }
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip,
                                          ShipAPI.HullSize hullSize,
                                          ShipAPI ship,
                                          float width,
                                          boolean isForModSpec) {

        if (tooltip == null) return;

        Color goodColor = new Color(120, 255, 140);
        Color badColor = new Color(255, 120, 120);
        Color systemColor = new Color(100, 200, 255);

        tooltip.addSectionHeading("Class-9 Sustained Warp Core Assembly", Alignment.MID, 10f);

        tooltip.addPara(
                "A specialized, high-velocity propulsion core optimized for long-range reconnaissance operations. While exceptionally agile in deep space, its variable geometry output creates heavy conversion inefficiencies when squeezed into standard tactical combat grids.",
                6f
        );

        tooltip.addSectionHeading("Combat System Specifications", Alignment.MID, 10f);

        tooltip.addPara(
                "• Engine Restraints: Core thermal displacement dampening reduces maximum tactical speed by 7.5%%.",
                4f,
                badColor,
                "7.5%"
        );

        boolean showDestroyer = (ship == null) || (hullSize == ShipAPI.HullSize.DESTROYER);
        boolean showCruiser = (ship == null) || (hullSize == ShipAPI.HullSize.CRUISER);

        if (showDestroyer) {
            tooltip.addSectionHeading("Destroyer Grid Tolerances", Alignment.MID, 8f);

            tooltip.addPara(
                    "• Induction Stress: Sub-optimal hull mass resonance restricts capacitor limits (-7.5%% ESM Max Energy, -10%% ESM Recharge).",
                    4f,
                    badColor,
                    "-7.5%",
                    "-10%"
            );

            tooltip.addPara(
                    "• Flux Bleed: Structural conversion grids leak soft flux, increasing weapon dissipation requirements by 7.5%%.",
                    4f,
                    badColor,
                    "7.5%"
            );
        }

        if (showCruiser) {
            tooltip.addSectionHeading("Cruiser Grid Tolerances", Alignment.MID, 8f);

            tooltip.addPara(
                    "• Displacement Inefficiency: High frame mass causes noticeable power grid resistance (-12.5%% ESM Max Energy, -10%% ESM Recharge).",
                    4f,
                    badColor,
                    "-12.5%",
                    "-10%"
            );

            tooltip.addPara(
                    "• Flux Bleed: Structural conversion grids leak soft flux, increasing weapon dissipation requirements by 5%%.",
                    4f,
                    badColor,
                    "5%"
            );
        }

        tooltip.addSectionHeading("Energy System Architecture Integration", Alignment.MID, 10f);

        tooltip.addPara(
                "Power Grid Status: Registers as a fully functional main reactor matrix node within the local ESM core layout.",
                4f,
                systemColor,
                "main reactor matrix node"
        );

        String targetHullId =
                (ship != null && ship.getHullSpec() != null)
                        ? ship.getHullSpec().getHullId()
                        : null;

        if (targetHullId == null
                || "fed_intrepid".equals(targetHullId)
                || "terran_intrepid".equals(targetHullId)) {

            tooltip.addSectionHeading("Intrepid Frame Synchronization", Alignment.MID, 8f);

            tooltip.addPara(
                    "• Shield Synergy: Harmonic envelope loops feedback into the core while shields are sustained (+2%% ESM Recharge Rate).",
                    4f,
                    goodColor,
                    "+2%"
            );

            tooltip.addSectionHeading("Campaign Properties", Alignment.MID, 10f);

            tooltip.addPara(
                    "• Variable Geometry Nacelles: Field geometry modifications drastically augment interstellar cruise limits (+2 Max Burn Speed).",
                    4f,
                    goodColor,
                    "+2"
            );
        }

        tooltip.addPara(
                "• Warp Capability: Designated as an active, registered Warp Drive component framework. Satisfies fleet security checks ensuring access to Jump Point travel vector fields.",
                4f,
                goodColor,
                "Warp Drive",
                "Jump Point"
        );
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {

        if (ship == null || ship.getHullSpec() == null) return false;

        String hullId = ship.getHullSpec().getHullId();
        String baseHullId = ship.getHullSpec().getBaseHullId();
        ShipAPI.HullSize size = ship.getHullSize();

        boolean isValidChassis =
                "fed_intrepid".equals(hullId)
                        || "terran_intrepid".equals(hullId)
                        || "fed_intrepid".equals(baseHullId)
                        || "terran_intrepid".equals(baseHullId);

        boolean isValidSize =
                size == ShipAPI.HullSize.DESTROYER
                        || size == ShipAPI.HullSize.CRUISER;

        return isValidChassis && isValidSize;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {

        if (ship != null && ship.getHullSpec() != null) {

            String hullId = ship.getHullSpec().getHullId();
            String baseHullId = ship.getHullSpec().getBaseHullId();

            if (!"fed_intrepid".equals(hullId)
                    && !"terran_intrepid".equals(hullId)
                    && !"fed_intrepid".equals(baseHullId)
                    && !"terran_intrepid".equals(baseHullId)) {

                return "Incompatible infrastructure. This core assembly requires a dedicated Intrepid-class spaceframe.";
            }
        }

        return "Standard spatial warp matrix structures cannot be safely retrofitted onto this chassis size configuration.";
    }
}