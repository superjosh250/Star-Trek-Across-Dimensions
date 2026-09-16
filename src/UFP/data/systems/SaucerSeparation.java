package UFP.data.systems;

import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class SaucerSeparation extends BaseShipSystemScript {

    private static final String FULL_SHIP_ID = "fed_galaxy";
    private static final String SAUCER_VARIANT_ID = "fed_galaxy_saucer";
    private static final String STARDRIVE_VARIANT_ID = "fed_galaxy_stardrive";

    private static final String PENALTY_APPLIED_KEY = "UFP_SaucerSeparation_IncompatiblePenaltyApplied";
    private static final String SHIELD_LOCK_ID = "UFP_SaucerSeparation_ShieldLock";

    private static final Logger log = Global.getLogger(SaucerSeparation.class);
    private static boolean isDiagnosticMode = false;

    public SaucerSeparation() {
        loadSettings();
    }

    private void loadSettings() {
        try (BufferedReader reader = new BufferedReader(new FileReader("data/config/UFPSettings.json"))) {
            StringBuilder jsonText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonText.append(line);
            }
            JSONObject settings = new JSONObject(jsonText.toString());
            isDiagnosticMode = settings.optBoolean("SaucerSeparation_LOGGING", false);
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("Failed to load UFPSettings.json for SaucerSeparation.");
            isDiagnosticMode = false;
        }
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        // ANCHOR GUARD: System state cannot advance while docked to a station module slot
        if (ship.getParentStation() != null || ship.getStationSlot() != null) return;

        // Keep shields locked if the incompatible-use penalty was already applied.
        if (Boolean.TRUE.equals(ship.getCustomData().get(PENALTY_APPLIED_KEY))) {
            enforceShieldLock(ship);
        }

        if (state != State.ACTIVE || effectLevel < 1f) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        String hullId = ship.getHullSpec().getHullId();

        // HARD GATE: do NOT separate unless the hull is exactly fed_galaxy.
        if (!FULL_SHIP_ID.equals(hullId)) {
            punishIncompatibleActivation(ship, engine, hullId);
            return;
        }

        CombatFleetManagerAPI fleetManager = engine.getFleetManager(ship.getOwner());

        // Original ship state
        Vector2f loc = new Vector2f(ship.getLocation());
        Vector2f vel = new Vector2f(ship.getVelocity());
        float facing = ship.getFacing();

        // Calculate health and armor percentages
        float hullPercent = ship.getHitpoints() / ship.getMaxHitpoints();
        float currentArmor = getTotalArmor(ship);
        ArmorGridAPI grid = ship.getArmorGrid();
        float maxArmor = grid.getMaxArmorInCell() * grid.getGrid().length * grid.getGrid()[0].length;
        float armorPercent = maxArmor > 0f ? currentArmor / maxArmor : 0f;

        if (isDiagnosticMode) {
            log.info("Original Ship Stats: HullPercent=" + hullPercent + ", ArmorPercent=" + armorPercent);
        }

        // Spawn positions
        Vector2f saucerLoc = rotateOffset(loc, new Vector2f(120.5f, 0f), facing);
        Vector2f stardriveLoc = rotateOffset(loc, new Vector2f(-340f, 0f), facing);

        // Spawn separated ships
        ShipAPI saucer = fleetManager.spawnShipOrWing(SAUCER_VARIANT_ID, saucerLoc, facing, 0f);
        ShipAPI stardrive = fleetManager.spawnShipOrWing(STARDRIVE_VARIANT_ID, stardriveLoc, facing, 0f);

        if (saucer != null) {
            applySplitStats(saucer, vel, hullPercent, armorPercent);
            if (isDiagnosticMode) logShipStats("Saucer Spawned", saucer);
        }
        if (stardrive != null) {
            applySplitStats(stardrive, vel, hullPercent, armorPercent);
            if (isDiagnosticMode) logShipStats("Stardrive Spawned", stardrive);
            if (ship == engine.getPlayerShip()) engine.setPlayerShipExternal(stardrive);
        }

        // Remove original ship only for the valid fed_galaxy split path
        fleetManager.removeDeployed(ship, false);
        engine.removeEntity(ship);

        if (isDiagnosticMode) {
            log.info("SaucerSeparation: Galaxy split into saucer and stardrive.");
        }
    }

    private void punishIncompatibleActivation(ShipAPI ship, CombatEngineAPI engine, String hullId) {
        if (Boolean.TRUE.equals(ship.getCustomData().get(PENALTY_APPLIED_KEY))) {
            enforceShieldLock(ship);
            return;
        }

        ship.setCustomData(PENALTY_APPLIED_KEY, Boolean.TRUE);

        stripAllArmor(ship);
        ship.setHitpoints(Math.max(1f, ship.getMaxHitpoints() * 0.25f));
        enforceShieldLock(ship);

        if (engine != null) {
            Color bad = Misc.getNegativeHighlightColor();
            engine.addFloatingText(
                    ship.getLocation(),
                    "SAUCER FAILURE",
                    30f,
                    bad,
                    ship,
                    1f,
                    2f
            );

            if (engine.getPlayerShip() == ship && engine.getCombatUI() != null) {
                engine.getCombatUI().addMessage(
                        1,
                        ship,
                        bad,
                        "Incompatible hull: catastrophic separation failure"
                );
            }
        }

        if (isDiagnosticMode) {
            log.warn("SaucerSeparation blocked on incompatible hull: " + hullId +
                    " | armor stripped, hull reduced to 25%, shields permanently disabled.");
        }
    }

    private void enforceShieldLock(ShipAPI ship) {
        if (ship == null) return;

        ship.getMutableStats().getShieldUnfoldRateMult().modifyMult(SHIELD_LOCK_ID, 0f);

        if (ship.getShield() != null && ship.getShield().isOn()) {
            ship.getShield().toggleOff();
        }
    }

    private void stripAllArmor(ShipAPI ship) {
        ArmorGridAPI grid = ship.getArmorGrid();
        if (grid == null) return;

        float[][] armor = grid.getGrid();
        for (int x = 0; x < armor.length; x++) {
            for (int y = 0; y < armor[0].length; y++) {
                grid.setArmorValue(x, y, 0f);
            }
        }
    }

    private void applySplitStats(ShipAPI part, Vector2f velocity, float hullPercent, float armorPercent) {
        part.getVelocity().set(velocity);
        part.setHitpoints(part.getMaxHitpoints() * hullPercent);
        applyArmorDistribution(part, armorPercent);
    }

    private void applyArmorDistribution(ShipAPI ship, float armorPercent) {
        ArmorGridAPI grid = ship.getArmorGrid();
        float[][] armor = grid.getGrid();
        int width = armor.length;
        int height = armor[0].length;
        float maxArmorPerCell = grid.getMaxArmorInCell();
        float totalArmorPool = (width * height * maxArmorPerCell) * armorPercent;
        float centerX = width / 2f;
        float centerY = height / 2f;

        List<Cell> cells = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float dx = x - centerX;
                float dy = y - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                cells.add(new Cell(x, y, distance));
            }
        }

        cells.sort((a, b) -> Float.compare(b.distance, a.distance));

        for (Cell cell : cells) {
            if (totalArmorPool <= 0) {
                grid.setArmorValue(cell.x, cell.y, 0f);
                continue;
            }
            float value = Math.min(maxArmorPerCell, totalArmorPool);
            grid.setArmorValue(cell.x, cell.y, value);
            totalArmorPool -= value;
        }
    }

    private void logShipStats(String prefix, ShipAPI ship) {
        float hull = ship.getHitpoints();
        float maxHull = ship.getMaxHitpoints();
        float avgArmor = getAverageArmor(ship);
        float maxArmorCell = ship.getArmorGrid().getMaxArmorInCell();
        log.info(prefix + " [" + ship.getHullSpec().getHullId() + "] - Hull: " + hull + "/" + maxHull +
                ", Avg Armor: " + avgArmor + " (Max per cell: " + maxArmorCell + ")");
    }

    private static class Cell {
        int x, y;
        float distance;

        Cell(int x, int y, float distance) {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }

    private Vector2f rotateOffset(Vector2f origin, Vector2f offset, float angle) {
        double rad = Math.toRadians(angle);
        float x = (float) (origin.x + offset.x * Math.cos(rad) - offset.y * Math.sin(rad));
        float y = (float) (origin.y + offset.x * Math.sin(rad) + offset.y * Math.cos(rad));
        return new Vector2f(x, y);
    }

    private float getAverageArmor(ShipAPI ship) {
        ArmorGridAPI grid = ship.getArmorGrid();
        float[][] armor = grid.getGrid();
        float total = 0f;
        int cells = 0;
        for (int x = 0; x < armor.length; x++) {
            for (int y = 0; y < armor[0].length; y++) {
                total += armor[x][y];
                cells++;
            }
        }
        return cells > 0 ? total / cells : 0f;
    }

    private float getTotalArmor(ShipAPI ship) {
        ArmorGridAPI grid = ship.getArmorGrid();
        float[][] armor = grid.getGrid();
        float total = 0f;
        for (int x = 0; x < armor.length; x++) {
            for (int y = 0; y < armor[0].length; y++) {
                total += armor[x][y];
            }
        }
        return total;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Intentionally do NOT remove SHIELD_LOCK_ID here.
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null || !ship.isAlive()) return false;

        // Disable system activation for both AI and player while attached to a station slot
        if (ship.getParentStation() != null || ship.getStationSlot() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int getUsesOverride(ShipAPI ship) {
        return 1;
    }

    @Override
    public float getActiveOverride(ShipAPI ship) {
        return 1f;
    }

    @Override
    public float getInOverride(ShipAPI ship) {
        return 0.5f;
    }

    @Override
    public float getOutOverride(ShipAPI ship) {
        return 0.5f;
    }

    @Override
    public float getRegenOverride(ShipAPI ship) {
        return 0f;
    }

    @Override
    public String getDisplayNameOverride(State state, float effectLevel) {
        return "Saucer Separation";
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null || !ship.isAlive()) return null;

        if (ship.getParentStation() != null || ship.getStationSlot() != null) {
            return "Moored to Station";
        }

        return FULL_SHIP_ID.equals(ship.getHullSpec().getHullId()) ? "Ready to Separate" : "Incompatible Hull";
    }

    @Override
    public ShipSystemStatsScript.StatusData getStatusData(int index, State state, float effectLevel) {
        return index == 0 ? new StatusData("Ready to Separate...", false) : null;
    }
}