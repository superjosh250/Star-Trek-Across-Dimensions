package UFP.data.hullmods;

import UFP.data.util.EnergySystemManager;
import UFP.data.util.WarpDriveManager;
import UFP.data.util.ESM_Weapons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.Color;

public class WarpCore_Sovereign extends BaseHullMod {

    public static final String HULLMOD_ID = "warp_core_sovereign";

    // Unique key to flag that this specific ship instance has already processed its setup check
    private static final String WEAPON_CHECK_KEY = "ufp_sovereign_weapon_checked";

    static {
        // Enforce registration as a valid campaign warp drive mechanism upon class indexing
        WarpDriveManager.registerWarpDriveHullMod(HULLMOD_ID);
    }

    private static boolean isValidSovereignHull(String hullId) {
        return "fed_sovereign".equals(hullId) || "fed_divisionSovereign".equals(hullId) || "terran_sovereign".equals(hullId)
        || "fed_sovereign_enterpriseE".equals(hullId);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Soft Flux build-up mitigation: reduces weapon flux generation profiles by 7.5%
        ESM_Weapons.modifyWeaponFluxCostMult(stats, id, 0.925f);

        // Flat campaign mobility enhancement tailored specifically to the individual spaceframe (+3 Burn Speed)
        if (stats.getVariant() != null && stats.getVariant().getHullSpec() != null) {
            String hullId = stats.getVariant().getHullSpec().getHullId();
            if (isValidSovereignHull(hullId)) {
                stats.getMaxBurnLevel().modifyFlat(id, 3f);
            }
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(Global.getCombatEngine(), ship);
        if (esm != null) {
            // Declares this system layout explicitly as a primary operational reactor core
            esm.setHasPowerSystem(true);

            // OPTIMIZATION: Check if this ship instance has already run its initial loadout evaluation
            if (!ship.getCustomData().containsKey(WEAPON_CHECK_KEY)) {
                boolean hasNonPhaser = false;

                if (ship.getAllWeapons() != null) {
                    for (WeaponAPI weapon : ship.getAllWeapons()) {
                        // Safety filter: Skip visual animations, glows, or built-in ship-system weapons
                        if (weapon.getType() == WeaponAPI.WeaponType.DECORATIVE ||
                                weapon.getType() == WeaponAPI.WeaponType.SYSTEM) {
                            continue;
                        }

                        // If any functional weapon slot contains a non-standard system, flag it
                        if (!"phaser_typeXII".equals(weapon.getId())) {
                            hasNonPhaser = true;
                            break; // Instantly break early out of the loop; condition met
                        }
                    }
                }

                // Apply the architectural multiplier permanently for this combat session based on the scan
                if (hasNonPhaser) {
                    esm.setWeaponEnergyCostMultiplier(1.08f);
                } else {
                    esm.setWeaponEnergyCostMultiplier(1.00f);
                }

                // Place the key token into the ship's data map to ensure this block never executes again
                ship.getCustomData().put(WEAPON_CHECK_KEY, true);
            }
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;

        Color goodColor = new Color(120, 255, 140);
        Color badColor = new Color(255, 120, 120);
        Color systemColor = new Color(100, 200, 255);

        tooltip.addSectionHeading("Class-E Sovereign Core Assembly", Alignment.MID, 10f);
        tooltip.addPara("An advanced, high-yield matter-antimatter reaction assembly engineered specifically to satisfy the immense operational power parameters of state-of-the-art capital tactical frames.", 6f);

        tooltip.addSectionHeading("Combat System Specifications", Alignment.MID, 10f);
        tooltip.addPara("• Flux Optimization: Integrated field mitigators reduce soft flux accumulation during weapon firing cycles by 7.5%%.", 4f, goodColor, "7.5%");

        tooltip.addPara("• Weapon Frequency Mismatch: Core plasma relays are perfectly tuned to phaser matrices. Having any functional weapon system installed that is not a Phaser Type XII forces a permanent 8%% energy draw penalty across the local network layout.", 4f, badColor, "not a Phaser Type XII", "8%");

        tooltip.addSectionHeading("Energy System Architecture Integration", Alignment.MID, 10f);
        tooltip.addPara("Power Grid Status: Registers as a fully functional main reactor matrix node within the local ESM core layout.", 4f, systemColor, "main reactor matrix node");

        tooltip.addSectionHeading("Campaign Properties", Alignment.MID, 10f);
        tooltip.addPara("• Propulsion Array: Specialized sub-space geometry modification maximizes individual strategic cruise ceilings (+3 Max Burn Speed).", 4f, goodColor, "+3");
        tooltip.addPara("• Warp Capability: Designated as an active, registered Warp Drive component framework. Satisfies fleet security checks ensuring access to Jump Point travel vector fields.", 4f, goodColor, "Warp Drive", "Jump Point");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) return false;
        return isValidSovereignHull(ship.getHullSpec().getHullId());
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Incompatible infrastructure. The Class-E Sovereign reactor requires a dedicated Sovereign or Division-class spaceframe structure.";
    }
}