package UFP.data.listeners;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier;

/**
 * Cleaned placeholder to retain architecture layout compatibility with EPSConduits handles
 * without running broken out-of-sequence calculations that cause ammo weapon desync loops.
 */
public class ESM_WeaponPreFireGater implements WeaponRangeModifier {

    public ESM_WeaponPreFireGater(ShipAPI ship, Object engine) {
        // Retained for backward constructor linking compatibility
    }

    @Override
    public float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) { return 0f; }

    @Override
    public float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) { return 1f; }

    @Override
    public float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon) { return 0f; }
}