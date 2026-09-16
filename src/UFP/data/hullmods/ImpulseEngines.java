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

public class ImpulseEngines extends BaseHullMod {

    public static final String HULLMOD_ID = "ufp_impulse_engines";
    private static final String RUNTIME_STATE_KEY = "ufp_impulse_engine_runtime";

    private static final String MODIFIER_RED_ALERT = "ufp_impulse_red_alert";
    private static final String MODIFIER_MALUS = "ufp_impulse_chaotic_malus";

    public static final List<String> VALID_MOUNTS = new ArrayList<>();
    static {
        VALID_MOUNTS.add("engine_mount");
        VALID_MOUNTS.add("impulse_engine_mount");
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (hullSize == ShipAPI.HullSize.DESTROYER) {
            stats.getMaxSpeed().modifyPercent(id, 10f);
            stats.getAcceleration().modifyPercent(id, 5f);
        } else if (hullSize == ShipAPI.HullSize.CAPITAL_SHIP) {
            stats.getMaxSpeed().modifyPercent(id, -10f);
            stats.getAcceleration().modifyPercent(id, -5f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;

        // Bypassing Yellow alert entirely by feeding an impossible -1f threshold
        ImpulseDriveManager.DriveProfile profile = new ImpulseDriveManager.DriveProfile(
                -1f, 0f, 0f, 10f, 15f,
                null, "Impulse drive overloaded (+10% Speed, +15% Accel)"
        );

        ImpulseDriveManager.processDriveUpdate(ship, amount, RUNTIME_STATE_KEY,
                "ufp_impulse_engines_dummy_yellow", MODIFIER_RED_ALERT, MODIFIER_MALUS, VALID_MOUNTS, profile);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;
        Color goodColor = new Color(120, 255, 140);
        Color alertColor = new Color(255, 180, 50);
        Color badColor = new Color(255, 120, 120);

        tooltip.addSectionHeading("Impulse Propulsion Array", Alignment.MID, 10f);
        tooltip.addPara("An advanced engine modification profile scaling sub-light output dynamics matching ship displacement specifications.", 6f);

        tooltip.addPara("Destroyer class: Gains +10%% max speed and +5%% acceleration.", 4f, goodColor, "+10%", "+5%");
        tooltip.addPara("Cruiser class: Operational performance curves remain balanced.", 3f);
        tooltip.addPara("Capital class: Experiences heavy inertia degradation: -10%% max speed and -5%% acceleration.", 3f, badColor, "-10%", "-5%");

        tooltip.addSectionHeading("Sub-System Integrations", Alignment.MID, 10f);
        tooltip.addPara("Red Alert Protocols: When shields are fully raised and total system flux remains safely below 25%%, speed increases by +10%% and acceleration ramps up by +15%%.", 4f, alertColor, "25%", "+10%", "+15%");
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
        return ship != null && ship.getHullSize() != ShipAPI.HullSize.FRIGATE && ship.getHullSize() != ShipAPI.HullSize.FIGHTER;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Frigates and strike fighters cannot house large-scale impulse engine cores.";
    }
}