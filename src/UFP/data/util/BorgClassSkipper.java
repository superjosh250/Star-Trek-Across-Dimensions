package UFP.data.util;

import com.fs.starfarer.api.Global;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gatekeeper for Borg add-on integration.
 *
 * Purpose:
 * - Prevent the main UFP jar from crashing when borg add-on is not installed/enabled.
 * - Only attempt to load/initialize Borg-only classes when mod id "borg" is present.
 *
 * How it works:
 * - Uses ModManager API to detect if mod id "borg" is enabled (i.e., mod_info.json loaded).
 * - Uses reflection with class names (Strings) to avoid class resolution at JVM link time.
 *
 * NOTE:
 * - Do NOT import or directly reference any Borg add-on classes from the main jar.
 * - Keep Borg class names as Strings in the list below.
 */
public final class BorgClassSkipper {

    /** The mod id that must be enabled for Borg content to be allowed. */
    public static final String BORG_MOD_ID = "borg";

    /**
     * Classes that belong to the Borg add-on (or rely on assets/libs only present with borg).
     * These will NOT be loaded unless borg is enabled.
     *
     * Put fully qualified class names here.
     */
    private static final List<String> BORG_ONLY_CLASSES = Collections.unmodifiableList(Arrays.asList(
            // Examples — replace with your real Borg-side classes:
            // "BORG.data.hullmods.BorgShields",
            // "BORG.data.scripts.BorgCampaignPlugin",
            // "BORG.data.everyframe.BorgEveryFramePlugin"
            "BORG.data.campaign.commodity.AssimilatedDrone",
            "BORG.data.campaign.ids.BorgIDS",
            "BORG.data.campaign.industry.AssimilatedSociety",
            "BORG.data.campaign.industry.AssimilatedSpaceport",
            "BORG.data.campaign.industry.AssimilationDispatch",
            "BORG.data.campaign.industry.AssimilationMatrix",
            "BORG.data.campaign.industry.ResourceAllocator",
            "BORG.data.campaign.industry.ResourceAssembler",
            "BORG.data.campaign.industry.Shipyard",
            "BORG.data.campaign.industry.TechnologyAssembler",
            "BORG.data.campaign.industry.TerminusAlpha",
            "BORG.data.campaign.people.AdvancedBorgUnits",
            "BORG.data.hullmods.AdaptationMatrix",
            "BORG.data.hullmods.BorgShields",
            "BORG.data.hullmods.HiveMind",
            "BORG.data.hullmods.RegenerationMatrix",
            "BORG.data.logger.BorgAdaptLogger",
            "BORG.data.plugins.CuttingBeamEffect",
            "BORG.data.plugins.PlasmaBeamEffect",
            "BORG.data.shipsystem.BorgTractorBeam",
            "BORG.data.util.BorgAdaptationMatrix",
            "BORG.data.util.BorgRegenerationMatrix",
            "BORG.data.util.CuttingBeamUtility",
            "BORG.data.util.PlasmaBurnEffect",
            "BORG.data.scripts.AssimilationProtocol",
            "BORG.data.scripts.AssimilationTaskforce",
            "BORG.data.scripts.CubeDefenseForce"
    ));

    private static Boolean cachedEnabled = null;

    private BorgClassSkipper() {
        // no instances
    }

    /**
     * Returns true if the borg add-on mod is enabled (its mod_info.json has been loaded by Starsector).
     */
    public static boolean isBorgEnabled() {
        if (cachedEnabled != null) return cachedEnabled;

        boolean enabled = false;
        try {
            // ModManager is available via Global.getSettings()
            enabled = Global.getSettings() != null
                    && Global.getSettings().getModManager() != null
                    && Global.getSettings().getModManager().isModEnabled(BORG_MOD_ID);
        } catch (Throwable t) {
            // If anything goes wrong, fail closed (treat as not enabled)
            enabled = false;
        }

        cachedEnabled = enabled;
        return enabled;
    }


    public static void clearCache() {
        cachedEnabled = null;
    }


    public static int preloadBorgClassesIfAllowed() {
        if (!isBorgEnabled()) return 0;

        int loaded = 0;
        for (String className : BORG_ONLY_CLASSES) {
            if (className == null || className.trim().isEmpty()) continue;
            if (tryLoadClass(className)) loaded++;
        }
        return loaded;
    }

    public static void runIfBorgEnabled(Runnable action) {
        if (!isBorgEnabled()) return;
        if (action == null) return;

        try {
            action.run();
        } catch (Throwable t) {
            // Swallow to prevent hard crashes, but log for visibility
            log("Borg action failed: " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }


    public static void invokeIfBorgEnabled(String className, String staticNoArgMethod) {
        if (!isBorgEnabled()) return;
        if (className == null || className.trim().isEmpty()) return;
        if (staticNoArgMethod == null || staticNoArgMethod.trim().isEmpty()) return;

        try {
            Class<?> clazz = Class.forName(className, false, BorgClassSkipper.class.getClassLoader());
            clazz.getMethod(staticNoArgMethod).invoke(null);
        } catch (Throwable t) {
            log("Failed to invoke " + className + "." + staticNoArgMethod + "(): " +
                    t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }

    /**
     * Attempts to load a class by name without initializing it (initialize=false).
     * Returns true if present and loadable.
     */
    public static boolean tryLoadClass(String className) {
        if (className == null || className.trim().isEmpty()) return false;

        try {
            Class.forName(className, false, BorgClassSkipper.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            // ClassNotFoundException / NoClassDefFoundError are expected when borg isn't present or deps missing
            log("Blocked or missing Borg class: " + className + " (" +
                    t.getClass().getSimpleName() + ")", null);
            return false;
        }
    }

    private static void log(String msg, Throwable t) {
        try {
            if (Global.getLogger(BorgClassSkipper.class) != null) {
                if (t != null) Global.getLogger(BorgClassSkipper.class).warn(msg, t);
                else Global.getLogger(BorgClassSkipper.class).info(msg);
            }
        } catch (Throwable ignored) {
        }
    }
}