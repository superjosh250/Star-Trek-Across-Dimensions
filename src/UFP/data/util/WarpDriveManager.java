package UFP.data.util;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight, high-performance campaign utility manager for UFP Warp Core systems.
 * Enforces architectural rules regarding ESM dependencies and jump point navigation compatibility.
 */
public final class WarpDriveManager implements EveryFrameScript {

    // Thread-safe, cached registry of hullmod IDs designated as functional Warp Drives
    private static final Set<String> registeredWarpDrives = Collections.synchronizedSet(new HashSet<String>());

    // Track state to prevent spamming warnings every frame while sitting near a jump point
    private static boolean jumpBlockedWarningShown = false;

    // Singleton / Script Handler Instance
    private static WarpDriveManager instance;

    public static WarpDriveManager getInstance() {
        if (instance == null) {
            instance = new WarpDriveManager();
        }
        return instance;
    }

    public WarpDriveManager() {
        // Public constructor allowed for EveryFrameScript registration
    }

    // =========================================================================
    // --- EVERY FRAME CAMPAIGN ENFORCEMENT ---
    // =========================================================================

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return true; } // Must run during dialogs!

    @Override
    public void advance(float amount) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null || !playerFleet.isAlive()) return;

        // Check compliance across the player fleet
        if (!canFleetUseJumpPoints(playerFleet)) {
            enforceJumpPointLockout(playerFleet);
        } else {
            jumpBlockedWarningShown = false; // Reset warning once fleet becomes compliant
        }
    }

    /**
     * Intercepts fleet proximity/interaction with jump points if non-compliant ESM ships exist.
     */
    private void enforceJumpPointLockout(CampaignFleetAPI playerFleet) {
        // Check if player is near or attempting to enter a jump point entity
        SectorEntityToken target = playerFleet.getInteractionTarget();

        if (target instanceof JumpPointAPI || (target != null && target.getCustomEntityType() != null && target.getCustomEntityType().contains("jump_point"))) {

            // If an interaction dialog is opening, close/intercept it
            if (Global.getSector().getCampaignUI().isShowingDialog()) {
                // Get non-compliant ships to format actionable feedback
                List<FleetMemberAPI> brokenShips = getNonCompliantShips(playerFleet);

                StringBuilder shipList = new StringBuilder();
                for (int i = 0; i < Math.min(3, brokenShips.size()); i++) {
                    if (i > 0) shipList.append(", ");
                    shipList.append(brokenShips.get(i).getShipName());
                }
                if (brokenShips.size() > 3) shipList.append(" (+").append(brokenShips.size() - 3).append(" more)");

                // Alert the player
                if (!jumpBlockedWarningShown) {
                    Global.getSector().getCampaignUI().addMessage(
                            "WARP CORE FAILURE: Jump point navigation locked! ESM vessels lacking active Warp Cores: " + shipList,
                            Color.RED
                    );
                    jumpBlockedWarningShown = true;
                }

                // Push fleet slightly back so it doesn't get stuck infinitely looping dialogs
                playerFleet.getMemoryWithoutUpdate().unset("$isTransiting");
            }
        }
    }

    // =========================================================================
    // --- CORE REGISTRY INTERFACE ---
    // =========================================================================

    public static void registerWarpDriveHullMod(String hullmodId) {
        if (hullmodId != null && !hullmodId.trim().isEmpty()) {
            registeredWarpDrives.add(hullmodId.trim());
        }
    }

    public static void unregisterWarpDriveHullMod(String hullmodId) {
        if (hullmodId != null) {
            registeredWarpDrives.remove(hullmodId.trim());
        }
    }

    public static void clearRegistry() {
        registeredWarpDrives.clear();
    }

    public static Set<String> getRegisteredWarpDrives() {
        return Collections.unmodifiableSet(registeredWarpDrives);
    }

    // =========================================================================
    // --- STATE EVALUATION API HOOKS ---
    // =========================================================================

    public static boolean hasValidWarpDrive(FleetMemberAPI member) {
        if (member == null || member.getVariant() == null || registeredWarpDrives.isEmpty()) {
            return false;
        }

        var variant = member.getVariant();

        Set<String> suppressed = null;
        try {
            suppressed = variant.getSuppressedMods();
        } catch (Throwable ignored) {}

        for (String warpId : registeredWarpDrives) {
            if (suppressed != null && suppressed.contains(warpId)) continue;

            if (variant.hasHullMod(warpId)) return true;
            if (variant.getHullMods().contains(warpId)) return true;
            if (variant.getPermaMods().contains(warpId)) return true;
            if (variant.getSMods().contains(warpId)) return true;
            if (member.getHullSpec() != null && member.getHullSpec().isBuiltIn(warpId)) return true;
        }

        return false;
    }

    public static boolean isEsmShip(FleetMemberAPI member) {
        if (member == null) return false;

        String hullId = member.getHullId();
        if (UFP_CSV_Manager.getEsmData(hullId) != null) return true;

        if (member.getHullSpec() != null && member.getHullSpec().getBaseHullId() != null) {
            if (UFP_CSV_Manager.getEsmData(member.getHullSpec().getBaseHullId()) != null) return true;
        }

        if (member.getVariant() != null) {
            return member.getVariant().hasHullMod("UFP_EPSConduits") ||
                    member.getVariant().getHullMods().contains("UFP_EPSConduits");
        }

        return false;
    }

    // =========================================================================
    // --- CAMPAIGN COMPLIANCE ENGINE ---
    // =========================================================================

    /**
     * Master Campaign Rule Enforcement System.
     */
    public static boolean canFleetUseJumpPoints(CampaignFleetAPI fleet) {
        if (fleet == null) return true;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing()) continue;

            if (isEsmShip(member) && !hasValidWarpDrive(member)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a list of all ships in the fleet failing ESM/Warp Core compliance.
     */
    public static List<FleetMemberAPI> getNonCompliantShips(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> nonCompliant = new ArrayList<>();
        if (fleet == null) return nonCompliant;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing()) continue;

            if (isEsmShip(member) && !hasValidWarpDrive(member)) {
                nonCompliant.add(member);
            }
        }
        return nonCompliant;
    }
}