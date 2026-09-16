package UFP.data.util;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Global configurations manager for UFP.
 * Parses and loads settings exactly once on application startup to enforce optimal performance.
 */
public final class UFPSettings_Manager {

    private static final String CONFIG_FILE_PATH = "data/config/UFPSettings.json";

    // Cached configurations fields
    private static Set<String> validPowerDeliverySystems = new HashSet<>();
    private static Set<String> validPowerReactors = new HashSet<>();
    private static Set<String> validAuxiliaryPowerSystems = new HashSet<>();
    private static Set<String> validImpulseEngines = new HashSet<>();
    private static Set<String> validShieldMods = new HashSet<>();
    private static List<String> validHologramVariants = new ArrayList<>();

    private static boolean epsOverride = false;
    private static boolean baseShieldRecoveryDiag = false;
    private static boolean isLoaded = false;

    // Fallback list if JSON is missing or empty
    private static final String[] FALLBACK_VARIANTS = new String[] {
            "astral_Elite",
            "valkyrie_Elite",
            "aurora_Support",
            "hammerhead_Support",
            "invictus_Support",
            "wolf_hegemony_PD"
    };

    /**
     * Master configuration loader. Call this from your ModPlugin's onApplicationLoad() method.
     */
    public static void loadSettings() {
        if (isLoaded) return;

        try {
            JSONObject root = Global.getSettings().getMergedJSON(CONFIG_FILE_PATH);

            // ---------------------------------------------------------------------
            // PARSE BLOCK: General Settings
            // ---------------------------------------------------------------------
            baseShieldRecoveryDiag = root.optBoolean("BaseShieldHitpointsRecovery", false);

            // ---------------------------------------------------------------------
            // PARSE BLOCK: EPS_SYSTEMS Configuration
            // ---------------------------------------------------------------------
            if (root.has("EPS_SYSTEMS")) {
                JSONObject eps = root.getJSONObject("EPS_SYSTEMS");
                epsOverride = eps.optBoolean("Override", false);

                if (eps.has("power_delivery_system")) {
                    JSONArray arr = eps.getJSONArray("power_delivery_system");
                    Set<String> tempDelivery = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.getString(i).trim();
                        if (!id.isEmpty()) tempDelivery.add(id);
                    }
                    validPowerDeliverySystems = Collections.unmodifiableSet(tempDelivery);
                }

                if (eps.has("reactor_validation")) {
                    JSONArray arr = eps.getJSONArray("reactor_validation");
                    Set<String> tempReactors = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.getString(i).trim();
                        if (!id.isEmpty()) tempReactors.add(id);
                    }
                    validPowerReactors = Collections.unmodifiableSet(tempReactors);
                }

                if (eps.has("auxiliary_power_system")) {
                    JSONArray arr = eps.getJSONArray("auxiliary_power_system");
                    Set<String> tempAux = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.getString(i).trim();
                        if (!id.isEmpty()) tempAux.add(id);
                    }
                    validAuxiliaryPowerSystems = Collections.unmodifiableSet(tempAux);
                }
            }

            // ---------------------------------------------------------------------
            // PARSE BLOCK: ImpulseEngine_Hullmods Configuration
            // ---------------------------------------------------------------------
            if (root.has("ImpulseEngine_Hullmods")) {
                JSONObject impulse = root.getJSONObject("ImpulseEngine_Hullmods");
                if (impulse.has("hullmod_ids")) {
                    JSONArray arr = impulse.getJSONArray("hullmod_ids");
                    Set<String> tempEngines = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.optString(i, null);
                        if (id != null && !id.trim().isEmpty()) tempEngines.add(id.trim());
                    }
                    validImpulseEngines = Collections.unmodifiableSet(tempEngines);
                }
            }

            // ---------------------------------------------------------------------
            // PARSE BLOCK: ShieldMount_Valid_Installation Configuration
            // ---------------------------------------------------------------------
            if (root.has("ShieldMount_Valid_Installation")) {
                JSONObject shieldSection = root.getJSONObject("ShieldMount_Valid_Installation");
                if (shieldSection.has("Shield_Hullmods")) {
                    JSONArray arr = shieldSection.getJSONArray("Shield_Hullmods");
                    Set<String> tempShields = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.optString(i, null);
                        if (id != null && !id.trim().isEmpty()) {
                            tempShields.add(id.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                    validShieldMods = Collections.unmodifiableSet(tempShields);
                }
            }

            // ---------------------------------------------------------------------
            // PARSE BLOCK: HolographicFleet_variants Configuration
            // ---------------------------------------------------------------------
            if (root.has("HolographicFleet_variants")) {
                JSONObject holoBlock = root.getJSONObject("HolographicFleet_variants");
                if (holoBlock.has("variant_ids")) {
                    JSONArray arr = holoBlock.getJSONArray("variant_ids");
                    List<String> tempVariants = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.optString(i, null);
                        if (id != null && !id.trim().isEmpty()) {
                            tempVariants.add(id.trim());
                        }
                    }
                    if (!tempVariants.isEmpty()) {
                        validHologramVariants = Collections.unmodifiableList(tempVariants);
                    }
                }
            }

            if (validHologramVariants.isEmpty()) {
                List<String> fb = new ArrayList<>();
                Collections.addAll(fb, FALLBACK_VARIANTS);
                validHologramVariants = Collections.unmodifiableList(fb);
            }

            Global.getLogger(UFPSettings_Manager.class).info("UFPSettings successfully cached.");
            isLoaded = true;

        } catch (Exception e) {
            Global.getLogger(UFPSettings_Manager.class).error("Critical failure parsing: " + CONFIG_FILE_PATH, e);
        }
    }

    public static Set<String> getValidPowerDeliverySystems() { return validPowerDeliverySystems; }
    public static Set<String> getValidPowerReactors() { return validPowerReactors; }
    public static Set<String> getValidAuxiliaryPowerSystems() { return validAuxiliaryPowerSystems; }
    public static Set<String> getValidImpulseEngines() { return validImpulseEngines; }
    public static Set<String> getValidShieldMods() { return validShieldMods; }
    public static List<String> getValidHologramVariants() { return validHologramVariants; }
    public static boolean isEpsOverrideEnabled() { return epsOverride; }
    public static boolean isBaseShieldRecoveryDiagnosticEnabled() { return baseShieldRecoveryDiag; }
}