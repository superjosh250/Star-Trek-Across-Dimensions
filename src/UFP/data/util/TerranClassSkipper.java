package UFP.data.util;

import com.fs.starfarer.api.Global;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gatekeeper for Terran Empire add-on integration.
 *
 * Purpose:
 * Prevent crashes when "terran_empire" mod is NOT installed/enabled.
 * Only allow loading Terran-specific classes when the mod is present.
 *
 * IMPORTANT:
 * Never directly import Terran classes here. Always use Strings.
 */
public final class TerranClassSkipper {

    public static final String TERRAN_MOD_ID = "terran_empire";
    private static final List<String> TERRAN_ONLY_CLASSES = Collections.unmodifiableList(Arrays.asList(

            // --- Example placeholders (replace with your actual Terran classes)
            "TERRAN.data.hullmods.TerranShields"
    ));

    private static Boolean cachedEnabled = null;

    private TerranClassSkipper() {
        // Prevent instantiation
    }

    /** Check if Terran Empire mod is enabled */
    public static boolean isTerranEnabled() {
        if (cachedEnabled != null) return cachedEnabled;

        boolean enabled = false;
        try {
            enabled = Global.getSettings() != null
                    && Global.getSettings().getModManager() != null
                    && Global.getSettings().getModManager().isModEnabled(TERRAN_MOD_ID);
        } catch (Throwable t) {
            enabled = false;
        }

        cachedEnabled = enabled;
        return enabled;
    }

    /** Clear cached mod state (useful during reload/dev) */
    public static void clearCache() {
        cachedEnabled = null;
    }

    /** Preload Terran classes ONLY if mod is present */
    public static int preloadTerranClassesIfAllowed() {
        if (!isTerranEnabled()) return 0;

        int loaded = 0;
        for (String cls : TERRAN_ONLY_CLASSES) {
            if (tryLoadClass(cls)) {
                loaded++;
            }
        }
        return loaded;
    }

    /** Safely run code ONLY if Terran mod exists */
    public static void runIfTerranEnabled(Runnable action) {
        if (!isTerranEnabled()) return;
        if (action == null) return;

        try {
            action.run();
        } catch (Throwable t) {
            log("Error running Terran action", t);
        }
    }

    /** Invoke static method ONLY if Terran mod exists */
    public static void invokeIfTerranEnabled(String className, String staticNoArgMethod) {
        if (!isTerranEnabled()) return;
        if (className == null || className.trim().isEmpty()) return;
        if (staticNoArgMethod == null || staticNoArgMethod.trim().isEmpty()) return;

        try {
            Class<?> clazz = Class.forName(className);
            clazz.getMethod(staticNoArgMethod).invoke(null);
        } catch (Throwable t) {
            log("Failed Terran invoke: " + className + "." + staticNoArgMethod, t);
        }
    }

    /** Attempt to load a class safely without initializing it */
    public static boolean tryLoadClass(String className) {
        if (className == null || className.trim().isEmpty()) return false;

        try {
            Class.forName(className, false, TerranClassSkipper.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            log("Blocked or missing Terran class: " + className +
                    " (" + t.getClass().getSimpleName() + ")", null);
            return false;
        }
    }

    private static void log(String msg, Throwable t) {
        try {
            if (Global.getLogger(TerranClassSkipper.class) != null) {
                if (t != null) {
                    Global.getLogger(TerranClassSkipper.class).warn(msg, t);
                } else {
                    Global.getLogger(TerranClassSkipper.class).info(msg);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}