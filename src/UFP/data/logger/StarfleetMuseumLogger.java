package UFP.data.logger;

import UFP.data.util.UFP_CSV_Manager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;

import java.util.*;

public class StarfleetMuseumLogger extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(StarfleetMuseumLogger.class);

    // CSV Logger Key ID
    public static final String LOGGER_ID = "StarfleetMuseumLogger";

    // Target Variant and Station Indicators
    public static final String TARGET_VARIANT_PREFIX = "fed_starfleetMuseum";
    public static final String DETACHED_FLAG = "UFP_IsDetachedModule";

    private boolean stationFound = false;
    private ShipAPI mainStation = null;
    private final Set<String> trackedModuleIds = new HashSet<>();
    private final Map<String, Boolean> moduleDetachedState = new HashMap<>();

    @Override
    public void init(CombatEngineAPI engine) {
        super.init(engine);
        if (!UFP_CSV_Manager.isLoggingEnabled(LOGGER_ID)) return;

        log.info("StarfleetMuseumLogger initialized for combat session.");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!UFP_CSV_Manager.isLoggingEnabled(LOGGER_ID)) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // 1. Locate the museum station if not yet bound
        if (!stationFound || mainStation == null || !engine.isEntityInPlay(mainStation)) {
            findTargetStation(engine);
            if (mainStation == null) return;
        }

        // 2. Monitor station status & detachment transitions
        monitorStationAndModules(engine);
    }

    private void findTargetStation(CombatEngineAPI engine) {
        for (ShipAPI ship : engine.getShips()) {
            if (ship == null || !ship.isAlive()) continue;

            String variantId = (ship.getVariant() != null) ? ship.getVariant().getHullVariantId() : "";
            String hullId = (ship.getHullSpec() != null) ? ship.getHullSpec().getHullId() : "";

            if (variantId.toLowerCase(Locale.ROOT).startsWith(TARGET_VARIANT_PREFIX.toLowerCase(Locale.ROOT)) ||
                    hullId.toLowerCase(Locale.ROOT).startsWith(TARGET_VARIANT_PREFIX.toLowerCase(Locale.ROOT))) {

                this.mainStation = ship;
                this.stationFound = true;

                log.info("==================================================================");
                log.info("STARFLEET MUSEUM TARGET FOUND IN COMBAT");
                log.info("Station Name: " + safeGetName(ship));
                log.info("Variant ID: " + variantId);
                log.info("Hull ID: " + hullId);
                log.info("==================================================================");

                logShipSnapshot("MAIN STATION", ship);
                logSubModulesSnapshot(ship);
                break;
            }
        }
    }

    private void monitorStationAndModules(CombatEngineAPI engine) {
        if (mainStation == null) return;

        List<ShipAPI> childModules = mainStation.getChildModulesCopy();
        if (childModules == null) return;

        for (ShipAPI module : childModules) {
            if (module == null) continue;

            String moduleId = getModuleIdentifier(module);
            boolean currentlyDetached = isModuleDetached(module);

            // Track initial state
            if (!trackedModuleIds.contains(moduleId)) {
                trackedModuleIds.add(moduleId);
                moduleDetachedState.put(moduleId, currentlyDetached);
                continue;
            }

            // Detect state change (Detachment Event)
            boolean previousDetached = moduleDetachedState.getOrDefault(moduleId, false);
            if (currentlyDetached && !previousDetached) {
                moduleDetachedState.put(moduleId, true);
                logDetachmentEvent(module);
            }
        }
    }

    private void logShipSnapshot(String label, ShipAPI ship) {
        log.info("--- [" + label + "] SNAPSHOT ---");
        log.info("  Name: " + safeGetName(ship));
        log.info("  Hull ID: " + (ship.getHullSpec() != null ? ship.getHullSpec().getHullId() : "null"));
        log.info("  Variant ID: " + (ship.getVariant() != null ? ship.getVariant().getHullVariantId() : "null"));
        log.info("  Is Station Flag: " + ship.isStation());
        log.info("  Parent Station: " + (ship.getParentStation() != null ? safeGetName(ship.getParentStation()) : "None"));

        // Safe AI Class Logging
        String aiClassName = "No AI (Player or Station Part)";
        if (ship.getShipAI() != null) {
            aiClassName = ship.getShipAI().getClass().getName();
        }
        log.info("  AI Controller Class: " + aiClassName);

        // Safe System Logging
        if (ship.getSystem() != null) {
            ShipSystemAPI sys = ship.getSystem();
            log.info("  Ship System: " + sys.getId() + " | State: " + sys.getState().name() + " | Script Class: " + sys.getClass().getName());
        } else {
            log.info("  Ship System: None");
        }

        // Log Active Hullmods
        if (ship.getVariant() != null && ship.getVariant().getHullMods() != null) {
            log.info("  Installed Hullmods: " + ship.getVariant().getHullMods().toString());
        }

        // Log Fitted Weapons
        List<WeaponAPI> weapons = ship.getAllWeapons();
        if (weapons != null && !weapons.isEmpty()) {
            StringBuilder sb = new StringBuilder("  Weapons (" + weapons.size() + "): ");
            for (WeaponAPI w : weapons) {
                sb.append("[").append(w.getSlot().getId()).append(" -> ").append(w.getId()).append("] ");
            }
            log.info(sb.toString());
        } else {
            log.info("  Weapons: None");
        }
    }

    private void logSubModulesSnapshot(ShipAPI parentStation) {
        List<ShipAPI> childModules = parentStation.getChildModulesCopy();
        if (childModules == null || childModules.isEmpty()) {
            log.info("No sub-modules detected attached to " + safeGetName(parentStation));
            return;
        }

        log.info(">>> Logging " + childModules.size() + " Child Sub-Modules <<<");
        for (int i = 0; i < childModules.size(); i++) {
            ShipAPI module = childModules.get(i);
            logShipSnapshot("SUB-MODULE #" + (i + 1), module);
        }
    }

    private void logDetachmentEvent(ShipAPI module) {
        log.warn("==================================================================");
        log.warn("DETACHMENT EVENT DETECTED: " + safeGetName(module));
        log.warn("==================================================================");
        log.warn("  Module ID Key: " + getModuleIdentifier(module));
        log.warn("  Current Collision Class: " + module.getCollisionClass().name());
        log.warn("  Current Velocity: " + module.getVelocity());
        log.warn("  Parent Station Link: " + (module.getParentStation() != null ? safeGetName(module.getParentStation()) : "SEVERED (null)"));
        log.warn("  Station Slot Link: " + (module.getStationSlot() != null ? module.getStationSlot().getId() : "SEVERED (null)"));

        String newAIClass = (module.getShipAI() != null) ? module.getShipAI().getClass().getName() : "NULL_AI";
        log.warn("  New AI Controller: " + newAIClass);

        logShipSnapshot("POST-DETACHMENT STATE", module);
    }

    private boolean isModuleDetached(ShipAPI module) {
        if (module == null) return false;
        if (Boolean.TRUE.equals(module.getCustomData().get(DETACHED_FLAG))) return true;
        return module.getParentStation() == null && !module.isStation();
    }

    private String getModuleIdentifier(ShipAPI module) {
        if (module == null) return "null";
        return module.getId() + "_" + (module.getHullSpec() != null ? module.getHullSpec().getHullId() : "unknown");
    }

    private String safeGetName(ShipAPI ship) {
        if (ship == null) return "null";
        if (ship.getName() != null && !ship.getName().isEmpty()) {
            return ship.getName();
        }
        if (ship.getHullSpec() != null) {
            return ship.getHullSpec().getHullId();
        }
        return "Unknown Station Entity";
    }
}