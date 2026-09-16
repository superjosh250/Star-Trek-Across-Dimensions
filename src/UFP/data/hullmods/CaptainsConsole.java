package UFP.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CaptainsConsole extends BaseHullMod {

    private static final String SETTINGS_PATH = "data/config/UFPSettings.json";
    private static final String SECTION = "Console_Installation";
    private static final String ARRAY_KEY = "Console_Hullmods";

    private static final String MOD_ID = "CaptainsConsole";

    private static volatile Set<String> VALID_CONSOLES = null;

    /** Load valid console hullmods from JSON */
    private static Set<String> getValidConsoles() {
        Set<String> cached = VALID_CONSOLES;
        if (cached != null) return cached;

        synchronized (CaptainsConsole.class) {
            if (VALID_CONSOLES != null) return VALID_CONSOLES;

            try {
                JSONObject root = Global.getSettings().getMergedJSON(SETTINGS_PATH);
                JSONObject section = root.getJSONObject(SECTION);
                JSONArray arr = section.getJSONArray(ARRAY_KEY);

                Set<String> result = new HashSet<>();

                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, null);

                    if (id == null || id.isBlank()) continue;

                    id = id.trim();

                    // ✅ IGNORE dummy_console WITHOUT CRASHING
                    if ("dummy_console".equals(id)) {
                        // intentional no-op
                        continue;
                    }

                    result.add(id);
                }

                VALID_CONSOLES = Collections.unmodifiableSet(result);

            } catch (Exception ex) {
                Global.getLogger(CaptainsConsole.class)
                        .error("Failed to load Console_Installation settings", ex);

                VALID_CONSOLES = Collections.emptySet();
            }

            return VALID_CONSOLES;
        }
    }

    /** Check if ship has a valid console installed */
    private static boolean hasValidConsole(ShipAPI ship) {
        if (ship == null || ship.getVariant() == null) return false;

        Set<String> valid = getValidConsoles();
        if (valid.isEmpty()) return false;

        for (String id : valid) {
            if (ship.getVariant().hasHullMod(id)) return true;
            if (ship.getVariant().getPermaMods().contains(id)) return true;
            if (ship.getVariant().getSMods().contains(id)) return true;

            if (ship.getHullSpec() != null && ship.getHullSpec().isBuiltIn(id)) {
                return true;
            }
        }

        return false;
    }

    /** Core behavior: This hullmod acts as a "gate" — it ENABLES other console hullmods. */
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        boolean valid = hasValidConsole(ship);

        if (!valid) {
            // 🚫 No valid console installed → system modification blocked
            // You can extend this later to:
            // - revert system
            // - block stat modifiers
            // - disable system triggers
            ship.setCustomData(MOD_ID + "_blocked", true);
        } else {
            // ✅ Valid console installed → system modification allowed
            ship.getCustomData().remove(MOD_ID + "_blocked");
        }
    }

    /** Tooltip feedback in refit */
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip,
                                          ShipAPI.HullSize hullSize,
                                          ShipAPI ship,
                                          float width,
                                          boolean isForModSpec) {
        if (tooltip == null) return;

        float opad = 10f;

        Set<String> valid = getValidConsoles();
        String validList = valid.isEmpty() ? "(none loaded)" : String.join(", ", valid);

        tooltip.addPara(
                "Allows ship system modification ONLY when a valid command console is installed.",
                opad
        );

        if (ship != null) {
            boolean hasConsole = hasValidConsole(ship);

            if (hasConsole) {
                tooltip.addPara(
                        "Console online: %s",
                        opad,
                        Misc.getPositiveHighlightColor(),
                        "System modification enabled"
                );
            } else {
                tooltip.addPara(
                        "Console offline: %s",
                        opad,
                        Misc.getNegativeHighlightColor(),
                        "Install a valid console hullmod"
                );
            }
        }

        tooltip.addPara(
                "Valid console hullmods: %s",
                opad,
                Misc.getHighlightColor(),
                validList
        );
    }

    /** Optional: expose status to UI (like your other mods) */
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return String.join(", ", getValidConsoles());
        }
        return null;
    }
}