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

public class ImpulseEngines_Capital extends BaseHullMod {

    public static final String HULLMOD_ID = "ufp_impulse_engines_capital";
    public static final String RUNTIME_STATE_KEY = "ufp_impulse_capital_runtime";

    private static final String MODIFIER_YELLOW_ALERT = "ufp_impulse_yellow_alert";
    private static final String MODIFIER_RED_ALERT = "ufp_impulse_red_alert";
    private static final String MODIFIER_MALUS = "ufp_impulse_chaotic_malus";

    public static final List<String> VALID_MOUNTS = new ArrayList<>();
    static {
        VALID_MOUNTS.add("engine_mount");
        VALID_MOUNTS.add("impulse_engine_mount");
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (hullSize == ShipAPI.HullSize.CAPITAL_SHIP) {
            stats.getMaxSpeed().modifyPercent(id, 0f);
            stats.getAcceleration().modifyPercent(id, 10f);
        } else if (hullSize == ShipAPI.HullSize.CRUISER) {
            stats.getMaxSpeed().modifyPercent(id, 15f);
            stats.getAcceleration().modifyPercent(id, 5f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;

        ImpulseDriveManager.DriveProfile profile;
        if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
            profile = new ImpulseDriveManager.DriveProfile(0.025f, 15f, 10f, 10f, 5f,
                    "Impulse drive optimized (+15% Speed, +10% Accel)", "Impulse drive overloaded (+10% Speed, +5% Accel)");
        } else {
            profile = new ImpulseDriveManager.DriveProfile(0.025f, 20f, 8f, 20f, 8f,
                    "Impulse drive maximized (+20% Speed, +8% Accel)", "Impulse drive overloaded (+20% Speed, +8% Accel)");
        }

        ImpulseDriveManager.processDriveUpdate(ship, amount, RUNTIME_STATE_KEY,
                MODIFIER_YELLOW_ALERT, MODIFIER_RED_ALERT, MODIFIER_MALUS, VALID_MOUNTS, profile);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (tooltip == null) return;
        Color goodColor = new Color(120, 255, 140);
        Color alertYellow = new Color(255, 230, 70);
        Color alertRed = new Color(255, 100, 100);
        Color badColor = new Color(255, 120, 120);

        tooltip.addSectionHeading("Capital-Grade Impulse Propulsion Array", Alignment.MID, 10f);
        tooltip.addPara("Heavy localized sub-light propulsion framework calibrated specifically for large displacement hull designs.", 6f);
        tooltip.addPara("Cruiser class: Gains +15%% permanent max speed and +5%% permanent acceleration.", 4f, goodColor, "+15%", "+5%");
        tooltip.addPara("Capital class: Structural mass limits speed adjustments (+0%%), but gains +10%% permanent acceleration.", 3f, goodColor, "+0%", "+10%");

        tooltip.addSectionHeading("Tactical Alert Condition Protocols", Alignment.MID, 10f);
        tooltip.addPara("Yellow Alert (Priority Alpha): Triggers when shields are raised and total framework flux rests below 2.5%%.", 4f, alertYellow, "2.5%");
        tooltip.addPara("  - Cruisers: Maintains maximized alert velocity output (+20%% Speed, +8%% Accel).", 2f, goodColor, "+20%", "+8%");
        tooltip.addPara("  - Capitals: Ramps up core structural efficiency (+15%% Speed, +10%% Accel).", 2f, goodColor, "+15%", "+10%");

        tooltip.addPara("Red Alert (Priority Beta): Triggers when shields are raised and total framework flux rests between 2.5%% and 25%%.", 4f, alertRed, "2.5%", "25%");
        tooltip.addPara("  - Cruisers: Receives substantial combat vector adjustments (+20%% Speed, +8%% Accel).", 2f, goodColor, "+20%", "+8%");
        tooltip.addPara("  - Capitals: Gains basic overload thrust compensation (+10%% Speed, +5%% Accel).", 2f, goodColor, "+10%", "+5%");

        tooltip.addPara("Rapid Cycle Resets: Field equalizers safely repair standard engine flameouts within 3.0 seconds.", 4f, goodColor, "3.0 seconds");

        tooltip.addPara("CRITICAL INTEGRITY WARNING:", 10f, badColor);
        tooltip.addPara("Operating this capital array without an active Impulse Engine Mount configuration will trigger massive backfires.", 3f, badColor, "Impulse Engine Mount");

        if (ship != null) {
            boolean stable = ImpulseDriveManager.hasValidMount(ship, VALID_MOUNTS);
            if (stable) {
                tooltip.addPara("System Status: CORES SECURE - Field array synchronized with mount geometry.", 10f, goodColor, "CORES SECURE");
            } else {
                tooltip.addPara("System Status: UNSTABLE ARRAY - Mount mismatched. Catastrophic systems failure imminent!", 10f, badColor, "UNSTABLE ARRAY");
            }
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && (ship.getHullSize() == ShipAPI.HullSize.CRUISER || ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Heavy capital-grade impulse propulsion arrays can only be mounted on Cruisers or Capital Ships.";
    }
}