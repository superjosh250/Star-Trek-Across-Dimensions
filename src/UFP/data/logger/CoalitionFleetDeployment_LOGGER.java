package UFP.data.logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import org.apache.log4j.Logger;

import java.lang.reflect.Method;
import java.util.*;

/**
 * CoalitionFleetDeployment logger (FIXED DIAGNOSTIC VERSION):
 * - Identifies cause of 0.00 CR and 0.20 MaxCR.
 * - Uses reflection-safe checks for maintenance and stats.
 * - Compatible with restricted Starfarer script environments.
 */
public class CoalitionFleetDeployment_LOGGER {

    private static final Logger log = Global.getLogger(CoalitionFleetDeployment_LOGGER.class);

    public static boolean ENABLED = true;

    private static final String MEMKEY_FLEET_UUID = "$ufp_cfd_logger_uuid";
    private static final String MEMKEY_MONITOR_ATTACHED = "$ufp_cfd_logger_monitor_attached";
    private static final String MEMKEY_MARKED_SPAWNED = "$ufp_cfd_logger_spawned_by_cfd";

    private static final float LOG_EVERY_DAYS = 1f / 1440f;

    private static final Map<String, Map<String, Float>> baselineHullFrac = new HashMap<>();

    private static boolean ok() {
        return ENABLED && Global.getSector() != null;
    }

    public static void onFleetSpawned(CampaignFleetAPI fleet,
                                      String spawnReason,
                                      MarketAPI market,
                                      RouteManager.RouteData route) {
        if (!ok() || fleet == null) return;

        markSpawnedByCFD(fleet);
        String uuid = getOrCreateFleetUUID(fleet);

        String marketId = (market == null) ? "null" : safeId(market);
        String factionId = (fleet.getFaction() == null) ? "null" : fleet.getFaction().getId();

        log.info("[CFD][SPAWN] fleetUUID=" + uuid
                + " reason=" + spawnReason
                + " faction=" + factionId
                + " market=" + marketId
                + " fp=" + fleet.getFleetPoints());

        logFleetComposition(fleet, "SPAWN-COMPOSITION");
        snapshotBaselineHullFractions(fleet);
        attachMonitorOnce(fleet);
    }

    public static void onNormalizeStart(CampaignFleetAPI fleet, String context) {
        if (!ok() || fleet == null || !isSpawnedByCFD(fleet)) return;
        String uuid = getOrCreateFleetUUID(fleet);
        log.info("[CFD][NORMALIZE-START] fleetUUID=" + uuid + " context=" + context);
        logFleetCRSnapshot(fleet, "NORMALIZE-BEFORE");
    }

    public static void onNormalizeEnd(CampaignFleetAPI fleet, String context) {
        if (!ok() || fleet == null || !isSpawnedByCFD(fleet)) return;
        logFleetCRSnapshot(fleet, "NORMALIZE-AFTER");
    }

    public static void onKnowledgeInjection(MarketAPI market,
                                            String factionId,
                                            int attempted,
                                            int addedOrUnknown,
                                            String contextNote) {
        if (!ok()) return;
        String marketId = (market == null) ? "null" : safeId(market);
        log.info("[CFD][KNOWLEDGE] market=" + marketId
                + " faction=" + factionId
                + " attempted=" + attempted
                + " added=" + addedOrUnknown);
    }

    private static void attachMonitorOnce(final CampaignFleetAPI fleet) {
        if (!ok() || fleet == null) return;
        if (!isSpawnedByCFD(fleet)) return;

        if (fleet.getMemoryWithoutUpdate().getBoolean(MEMKEY_MONITOR_ATTACHED)) return;
        fleet.getMemoryWithoutUpdate().set(MEMKEY_MONITOR_ATTACHED, true);

        fleet.addScript(new EveryFrameScript() {
            private float days = 0f;

            @Override
            public void advance(float amount) {
                if (!ok()) return;
                if (fleet.getContainingLocation() == null) return;

                days += Global.getSector().getClock().convertToDays(amount);
                if (days < LOG_EVERY_DAYS) return;
                days = 0f;

                logFleetCRSnapshot(fleet, "PERIODIC");
                logFleetDiagnostic(fleet);
                detectAndLogHullFractionDrops(fleet);
            }

            @Override
            public boolean isDone() { return false; }
            @Override
            public boolean runWhilePaused() { return false; }
        });
    }

    /**
     * Reflection-safe diagnostic to find out why CR is capped.
     */
    private static void logFleetDiagnostic(CampaignFleetAPI fleet) {
        if (fleet == null || !isSpawnedByCFD(fleet)) return;
        String uuid = getOrCreateFleetUUID(fleet);
        FactionAPI faction = fleet.getFaction();

        // Log memory keys to check for maintenance/logistics flags
        log.info("[CFD][DIAGNOSTIC][MEM] fleetUUID=" + uuid + " keys=" + fleet.getMemoryWithoutUpdate().getKeys().toString());

        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (m == null) continue;
            RepairTrackerAPI rt = m.getRepairTracker();

            // Check hull knowledge
            boolean knows = (faction != null) && faction.knowsShip(m.getHullId());

            // Check maintenance suspension via reflection-safe boolean helper
            boolean suspended = safeBool(rt, "isMaintenanceSuspended");

            // Log core state
            log.info(String.format("[CFD][DIAGNOSTIC][SHIP] fleetUUID=%s ship=%s knows=%b maintSuspended=%b maxCR=%s",
                    uuid, m.getHullId(), knows, suspended, format2(safeMaxCR(rt))));
        }
    }

    private static void logFleetCRSnapshot(CampaignFleetAPI fleet, String tag) {
        if (fleet == null || fleet.getFleetData() == null) return;
        String uuid = getOrCreateFleetUUID(fleet);
        StringBuilder sb = new StringBuilder();
        sb.append("[CFD][").append(tag).append("][CR] fleetUUID=").append(uuid);

        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (m == null) continue;
            RepairTrackerAPI rt = m.getRepairTracker();
            if (rt == null) continue;
            sb.append("\n  - shipKey=").append(memberKey(m))
                    .append(" cr=").append(format2(safeCR(rt)))
                    .append(" maxCR=").append(format2(safeMaxCR(rt)));
        }
        log.info(sb.toString());
    }

    private static void markSpawnedByCFD(CampaignFleetAPI fleet) {
        fleet.getMemoryWithoutUpdate().set(MEMKEY_MARKED_SPAWNED, true);
    }

    private static boolean isSpawnedByCFD(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(MEMKEY_MARKED_SPAWNED);
    }

    private static String getOrCreateFleetUUID(CampaignFleetAPI fleet) {
        if (fleet == null) return "null";
        Object v = fleet.getMemoryWithoutUpdate().get(MEMKEY_FLEET_UUID);
        if (v instanceof String && ((String) v).length() > 0) return (String) v;
        String uuid = UUID.randomUUID().toString();
        fleet.getMemoryWithoutUpdate().set(MEMKEY_FLEET_UUID, uuid);
        return uuid;
    }

    private static String memberKey(FleetMemberAPI m) {
        String id = reflectString(m, "getId");
        if (id != null && id.length() > 0) return id;
        return safeVariantId(m) + "|" + safeHullId(m) + "|" + System.identityHashCode(m);
    }

    private static String safeHullId(FleetMemberAPI m) {
        try { return m.getHullSpec() != null ? m.getHullSpec().getHullId() : "null"; }
        catch (Throwable t) { return "error"; }
    }

    private static String safeVariantId(FleetMemberAPI m) {
        try { return m.getVariant() != null ? m.getVariant().getHullVariantId() : "null"; }
        catch (Throwable t) { return "error"; }
    }

    private static float safeCR(RepairTrackerAPI rt) {
        try { return rt.getCR(); } catch (Throwable t) { return -1f; }
    }

    private static float safeMaxCR(RepairTrackerAPI rt) {
        try { return rt.getMaxCR(); } catch (Throwable t) { return -1f; }
    }

    private static boolean safeBool(Object o, String method) {
        if (o == null) return false;
        try {
            Method m = o.getClass().getMethod(method);
            Object out = m.invoke(o);
            return (out instanceof Boolean) && (Boolean) out;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String safeId(Object o) {
        if (o == null) return "null";
        String sid = reflectString(o, "getId");
        return sid != null ? sid : o.toString();
    }

    private static String reflectString(Object o, String method) {
        try {
            Method m = o.getClass().getMethod(method);
            Object out = m.invoke(o);
            return out == null ? null : String.valueOf(out);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float safeHullFraction(FleetMemberAPI member) {
        try {
            Method getStatus = member.getClass().getMethod("getStatus");
            Object status = getStatus.invoke(member);
            if (status != null) {
                Method getHullFraction = status.getClass().getMethod("getHullFraction");
                Object out = getHullFraction.invoke(status);
                if (out instanceof Float) return (Float) out;
            }
        } catch (Throwable ignored) {}
        return 1f;
    }

    private static void snapshotBaselineHullFractions(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getFleetData() == null) return;
        String uuid = getOrCreateFleetUUID(fleet);
        Map<String, Float> map = new HashMap<>();
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (m == null) continue;
            map.put(memberKey(m), safeHullFraction(m));
        }
        baselineHullFrac.put(uuid, map);
    }

    private static void detectAndLogHullFractionDrops(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getFleetData() == null) return;
        String uuid = getOrCreateFleetUUID(fleet);
        Map<String, Float> base = baselineHullFrac.get(uuid);
        if (base == null) return;
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (m == null) continue;
            String key = memberKey(m);
            float cur = safeHullFraction(m);
            Float prev = base.get(key);
            if (prev != null && cur + 0.001f < prev) {
                log.warn("[CFD][DAMAGE-DETECTED] fleetUUID=" + uuid + " ship=" + key + " cur=" + cur);
                base.put(key, cur);
            }
        }
    }

    private static void logFleetComposition(CampaignFleetAPI fleet, String tag) {
        if (fleet == null || fleet.getFleetData() == null) return;
        String uuid = getOrCreateFleetUUID(fleet);
        StringBuilder sb = new StringBuilder();
        sb.append("[CFD][").append(tag).append("] fleetUUID=").append(uuid).append(" {");
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (m == null) continue;
            sb.append("\n  - ").append(memberKey(m)).append(" hull=").append(safeHullId(m));
        }
        sb.append("\n}");
        log.info(sb.toString());
    }

    private static String format2(float v) {
        return String.format(Locale.US, "%.2f", v);
    }
}