package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.HashSet;
import java.util.Set;

public class PristineCargoHolds extends BaseHullMod {

    public static final float CARGO_BONUS = 125f;
    public static final float FUEL_BONUS = 25f;
    public static final float MAX_CREW_MULT = 0.4f; // 60% reduction means 40% remains
    public static final float MIN_CREW_MULT = 0.6f; // 40% reduction means 60% remains

    // Set of incompatible hullmod IDs
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
    static {
        BLOCKED_HULLMODS.add("expanded_cargo_holds");
        BLOCKED_HULLMODS.add("cargo_expansion_mki");
        BLOCKED_HULLMODS.add("cargo_expansion_mkii");
        BLOCKED_HULLMODS.add("cargo_expansion_mkiii");
    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCargoMod().modifyPercent(id, CARGO_BONUS);
        stats.getFuelMod().modifyPercent(id, FUEL_BONUS);
        stats.getMaxCrewMod().modifyMult(id, MAX_CREW_MULT);
        stats.getMinCrewMod().modifyMult(id, MIN_CREW_MULT);
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        // Only for Cruisers
        if (ship.getHullSize() != HullSize.CRUISER)
            return false;

        // Check for incompatible hullmods
        for (String modId : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(modId))
                return false;
        }

        return true;
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getHullSize() != HullSize.CRUISER) {
            return "Can only be installed on cruiser class ships";
        }

        for (String modId : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(modId)) {
                return "Incompatible with other cargo expansion hullmods";
            }
        }

        return null;
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0)
            return "Cruisers";
        if (index == 1)
            return "" + (int) CARGO_BONUS + "%";
        if (index == 2)
            return "" + (int) FUEL_BONUS + "%";
        if (index == 3)
            return "60%"; // Max crew reduction
        if (index == 4)
            return "40%"; // Min crew reduction
        return null;
    }
}
