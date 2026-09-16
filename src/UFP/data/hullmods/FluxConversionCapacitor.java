package UFP.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FluxConversionCapacitor extends BaseHullMod {

    private static final String SETTINGS_PATH = "data/config/UFPSettings.json";
    private static final String SETTINGS_SECTION = "CapacitorConversion";
    private static final String SETTINGS_VALID_ARRAY = "valid_hullmods_id";

    // Original vent calibration behavior (kept for NON-ESM ships)
    private static final float THRESHOLD = 0.40f;
    private static final float TIME_LOW = 5f;
    private static final float TIME_HIGH = 10f;

    private static final String MOD_ID = "ufp_flux_conversion_capacitor";
    private static final String KEY_VENT_CALIBRATED = MOD_ID + "_ventCalibrated";

    private static volatile Set<String> VALID = null;

    private static Set<String> getValidHullmods() {
        Set<String> v = VALID;
        if (v != null) return v;

        synchronized (FluxConversionCapacitor.class) {
            if (VALID != null) return VALID;
            try {
                JSONObject json = Global.getSettings().loadJSON(SETTINGS_PATH);
                JSONObject section = json != null ? json.optJSONObject(SETTINGS_SECTION) : null;
                JSONArray arr = section != null ? section.optJSONArray(SETTINGS_VALID_ARRAY) : null;

                if (arr == null) {
                    VALID = Collections.emptySet();
                    return VALID;
                }

                Set<String> out = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, null);
                    if (id != null && !id.isBlank()) out.add(id.trim());
                }

                VALID = Collections.unmodifiableSet(out);
                return VALID;

            } catch (Exception ex) {
                VALID = Collections.emptySet();
                return VALID;
            }
        }
    }

    private static boolean hasAnyValidHullmod(ShipAPI ship) {
        if (ship == null || ship.getVariant() == null) return false;
        Set<String> valid = getValidHullmods();
        if (valid.isEmpty()) return false;

        for (String id : valid) {
            if (ship.getVariant().hasHullMod(id)) return true;
        }
        return false;
    }

    private static boolean isPlayerShip(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return false;
        ShipAPI player = engine.getPlayerShip();
        return player != null && ship == player;
    }

    private static boolean isUsingESM(ShipAPI ship) {
        // ESM marks enabled ships with this key in attach()
        return false;
    }

    /**
     * ESM venting recharge bonuses (multiplies flux dissipation while venting):
     * FRIGATE +125% => 2.25x
     * DESTROYER +100% => 2.00x
     * CRUISER +75% => 1.75x
     * CAPITAL_SHIP +50% => 1.50x
     */
    private static float getEsmRechargeMult(ShipAPI.HullSize size) {
        if (size == null) return 1f;
        return switch (size) {
            case FRIGATE -> 2.25f;
            case DESTROYER -> 2.00f;
            case CRUISER -> 1.75f;
            case CAPITAL_SHIP -> 1.50f;
            default -> 1f;
        };
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return hasAnyValidHullmod(ship);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        Set<String> valid = getValidHullmods();
        if (valid.isEmpty()) {
            return "UFPSettings.json missing/invalid: no compatible hullmods defined";
        }
        return "Requires one of: " + String.join(", ", valid);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;
        MutableShipStatsAPI stats = ship.getMutableStats();

        boolean alive = ship.isAlive() && !ship.isHulk();
        boolean venting = alive
                && ship.getFluxTracker() != null
                && ship.getFluxTracker().isVenting();

        boolean applicable = hasAnyValidHullmod(ship);
        boolean player = isPlayerShip(ship);
        boolean esm = isUsingESM(ship);

        // ---------------------------------------------------
        // ESM Recharge Boost: ONLY while venting (player ship only)
        // - This boosts flux dissipation while venting so ESM "energy" recharges faster.
        // - Does NOT run on non-ESM ships.
        // ---------------------------------------------------
        if (venting && applicable && player && esm) {
            float mult = getEsmRechargeMult(ship.getHullSize());
            if (mult > 1f) {
                stats.getFluxDissipation().modifyMult(MOD_ID, mult);
            } else {
                stats.getFluxDissipation().unmodify(MOD_ID);
            }
        } else {
            stats.getFluxDissipation().unmodify(MOD_ID);
        }

        // ---------------------------------------------------
        // ORIGINAL vent calibration logic: NON-ESM ONLY
        // - If ESM is active, we do NOT apply/keep the old vent-rate changes.
        // ---------------------------------------------------
        if (esm) {
            // Ensure old vent calibration is not lingering on ESM ships
            stats.getVentRateMult().unmodify(MOD_ID);
            ship.removeCustomData(KEY_VENT_CALIBRATED);
            return;
        }

        // From here down: original behavior (non-ESM ships)
        if (!venting) {
            stats.getVentRateMult().unmodify(MOD_ID);
            ship.removeCustomData(KEY_VENT_CALIBRATED);
            return;
        }

        if (!applicable) {
            stats.getVentRateMult().unmodify(MOD_ID);
            ship.removeCustomData(KEY_VENT_CALIBRATED);
            return;
        }

        if (ship.getCustomData().get(KEY_VENT_CALIBRATED) == null) {
            float fluxLevel = ship.getFluxTracker().getFluxLevel();
            float targetTime = (fluxLevel <= THRESHOLD) ? TIME_LOW : TIME_HIGH;
            float baseTime = ship.getFluxTracker().getTimeToVent();
            if (baseTime <= 0.01f) baseTime = 0.01f;

            float ventMult = baseTime / targetTime;
            ventMult = clamp(ventMult, 0.05f, 50f);

            stats.getVentRateMult().modifyMult(MOD_ID, ventMult);
            ship.setCustomData(KEY_VENT_CALIBRATED, Boolean.TRUE);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
