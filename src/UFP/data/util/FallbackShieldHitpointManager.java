
package UFP.data.util;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.Global;

/**
 * Fallback strategy: if CSV has no entry for hull_id, use maxFlux as shield HP.
 * Separated for readability and future policy changes.
 */
public class FallbackShieldHitpointManager {

    private FallbackShieldHitpointManager() {
        // Utility class; no instances
    }

    // --- Explanation: Compute HP via fallback policy (HP = maxFlux). ---
    public static float computeFallbackHP(ShipAPI ship) {
        if (ship == null) return 0f;
        float fallback = ship.getMaxFlux();
        Global.getLogger(FallbackShieldHitpointManager.class).info(
                "Fallback shield HP via maxFlux for ship: " + safeShipName(ship) + " -> " + fallback
        );
        return fallback;
    }

    // --- Explanation: Safe ship name for logs. ---
    private static String safeShipName(ShipAPI ship) {
        try {
            return ship.getName();
        } catch (Throwable t) {
            return "(unknown)";
        }
    }
}
