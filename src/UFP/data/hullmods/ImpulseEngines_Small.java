package UFP.data.hullmods;

import UFP.data.util.ImpulseDriveManager;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ImpulseEngines_Small extends BaseHullMod {

    public static final String HULLMOD_ID = "impulseEngines_small";
    private static final String RUNTIME_STATE_KEY = "ufp_impulse_engine_s_runtime";

    private static final String MODIFIER_YELLOW_ALERT = "ufp_impulse_s_yellow_alert";
    private static final String MODIFIER_RED_ALERT = "ufp_impulse_s_red_alert";
    private static final String MODIFIER_MALUS = "ufp_impulse_s_chaotic_malus";

    public static final List<String> VALID_MOUNTS = new ArrayList<>();
    static {
        VALID_MOUNTS.add("engine_mount");
        VALID_MOUNTS.add("impulse_engine_mount");
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Destroyer gets a permanent +5% max speed and +2% acceleration
        if (hullSize == ShipAPI.HullSize.DESTROYER) {
            stats.getMaxSpeed().modifyPercent(id, 5f);
            stats.getAcceleration().modifyPercent(id, 2f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;

        ShipAPI.HullSize hullSize = ship.getHullSize();
        ImpulseDriveManager.DriveProfile profile;

        if (hullSize == ShipAPI.HullSize.FRIGATE) {
            // Frigate: Yellow Alert (<2.5% flux -> +25% Speed, +12% Accel) | Red Alert (<25% flux -> +15% Speed, +7.5% Accel)
            profile = new ImpulseDriveManager.DriveProfile(
                    0.025f, 25f, 12f, 15f, 7.5f,
                    "Impulse drive cruising (+25% Speed, +12% Accel)",
                    "Impulse drive overloaded (+15% Speed, +7.5% Accel)"
            );
        } else if (hullSize == ShipAPI.HullSize.DESTROYER) {
            // Destroyer: No Yellow Alert (-1f threshold) | Red Alert (<25% flux -> +15% Speed, +7.5% Accel)
            profile = new ImpulseDriveManager.DriveProfile(
                    -1f, 0f, 0f, 15f, 7.5f,
                    null,
                    "Impulse drive overloaded (+15% Speed, +7.5% Accel)"
            );
        } else {
            return;
        }

        ImpulseDriveManager.processDriveUpdate(ship, amount, RUNTIME_STATE_KEY,
                MODIFIER_YELLOW_ALERT, MODIFIER_RED_ALERT, MODIFIER_MALUS, VALID_MOUNTS, profile);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;
        Color goodColor = new Color(120, 255, 140);
        Color alertColor = new Color(255, 180, 50);
        Color badColor = new Color(255, 120, 120);

        tooltip.addSectionHeading("Impulse Propulsion Array (Small)", Alignment.MID, 10f);
        tooltip.addPara("An advanced engine modification profile scaling sub-light output dynamics matching light ship displacement specifications.", 6f);

        tooltip.addPara("Frigate class: Optimized for rapid tactical positioning and cruising profiles.", 4f);
        tooltip.addPara("Destroyer class: Gains permanent +5%% max speed and +2%% acceleration.", 3f, goodColor, "+5%", "+2%");

        tooltip.addSectionHeading("Sub-System Integrations", Alignment.MID, 10f);
        tooltip.addPara("Yellow Alert Protocols (Frigates Only): As long as total system flux remains below 2.5%%, max speed increases by +25%% and acceleration ramps up by +12%%.", 4f, alertColor, "2.5%", "+25%", "+12%");
        tooltip.addPara("Red Alert Protocols: When shields are fully raised and total system flux remains safely below 25%%, max speed increases by +15%% and acceleration ramps up by +7.5%%.", 4f, alertColor, "25%", "+15%", "+7.5%");
        tooltip.addPara("Rapid Cycle Resets: Integrated field equalizers ensure that any non-permanent engine flameout or mechanical disablement resets to fully operational within 3.0 seconds.", 3f, goodColor, "3.0 seconds");

        tooltip.addPara("CRITICAL INTEGRITY WARNING:", 10f, badColor);
        tooltip.addPara("This engine must remain safely tuned within a standardized Impulse Engine Mount configuration. Operating this system without a matching framework triggers massive engine backfires.", 3f, badColor, "Impulse Engine Mount");

        if (ship != null) {
            boolean stable = ImpulseDriveManager.hasValidMount(ship, VALID_MOUNTS);
            if (stable) {
                tooltip.addPara("System Status: STABLE AND SECURE - Structural mount harmonization online.", 10f, goodColor, "STABLE AND SECURE");
            } else {
                tooltip.addPara("System Status: DANGEROUS MOUNT SPECIFICATIONS UNMATCHED - Severe system backfire threat active!", 10f, badColor, "DANGEROUS MOUNT SPECIFICATIONS UNMATCHED");
            }
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && (ship.getHullSize() == ShipAPI.HullSize.FRIGATE || ship.getHullSize() == ShipAPI.HullSize.DESTROYER);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Small-scale impulse engine cores can only be housed within frigates and destroyers.";
    }
}