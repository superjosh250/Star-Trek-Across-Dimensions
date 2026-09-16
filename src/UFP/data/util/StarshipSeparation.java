package UFP.data.util;

import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StarshipSeparation {

    private static final float SAFETY_CLEARANCE_MARGIN = 10f; // Clearance distance buffer in units

    /**
     * Data holder defining a single ship segment to spawn during separation.
     */
    public static class SeparationSpec {
        public final String variantId;
        public final Vector2f localOffset; // x = forward/aft, y = port/starboard
        public final boolean isPlayerTarget; // Transfers player control to this segment

        public SeparationSpec(String variantId, Vector2f localOffset, boolean isPlayerTarget) {
            this.variantId = variantId;
            this.localOffset = localOffset;
            this.isPlayerTarget = isPlayerTarget;
        }
    }

    /**
     * Executes separation logic for any number of child ship segments.
     *
     * @param engine Engine instance
     * @param parent Original parent ship being separated
     * @param specs  List of N specifications defining child ship variants and offsets
     * @return List of spawned ShipAPI instances
     */
    public static List<ShipAPI> separate(CombatEngineAPI engine, ShipAPI parent, List<SeparationSpec> specs) {
        if (engine == null || parent == null || !parent.isAlive() || specs == null || specs.isEmpty()) {
            return new ArrayList<>();
        }

        CombatFleetManagerAPI fleetManager = engine.getFleetManager(parent.getOwner());
        if (fleetManager == null) return new ArrayList<>();

        float hullRatio = Math.max(0.01f, parent.getHitpoints() / parent.getMaxHitpoints());
        float armorRatio = Math.max(0f, getArmorRatio(parent));

        Vector2f parentLoc = parent.getLocation();
        Vector2f parentVel = parent.getVelocity();
        float facing = parent.getFacing();
        boolean isPlayer = (engine.getPlayerShip() == parent);

        List<ShipAPI> spawnedShips = new ArrayList<>(specs.size());

        // 1. Spawn all requested segments with collision temporarily disabled
        for (SeparationSpec spec : specs) {
            if (spec.variantId == null) continue;

            Vector2f spawnLoc = calculateWorldLocation(parentLoc, spec.localOffset, facing);
            ShipAPI spawned = fleetManager.spawnShipOrWing(spec.variantId, spawnLoc, facing, 0f);

            if (spawned != null) {
                // Temporarily disable collision to prevent physics overlaps and shield damage
                spawned.setCollisionClass(CollisionClass.NONE);

                spawned.getVelocity().set(parentVel);
                spawned.setHitpoints(spawned.getMaxHitpoints() * hullRatio);
                applyArmorRatio(spawned, armorRatio);

                if (isPlayer && spec.isPlayerTarget) {
                    engine.setPlayerShipExternal(spawned);
                }

                spawnedShips.add(spawned);
            }
        }

        // 2. Remove parent ship
        fleetManager.removeDeployed(parent, false);
        engine.removeEntity(parent);

        // 3. Attach multi-ship clearance tracker plugin
        if (spawnedShips.size() > 1) {
            engine.addPlugin(new SeparationCollisionSafetyPlugin(engine, spawnedShips, SAFETY_CLEARANCE_MARGIN));
        } else if (spawnedShips.size() == 1) {
            spawnedShips.get(0).setCollisionClass(CollisionClass.SHIP);
        }

        return spawnedShips;
    }

    /**
     * High-performance, zero-allocation frame listener that checks distance between all sibling pieces.
     */
    private static class SeparationCollisionSafetyPlugin extends BaseEveryFrameCombatPlugin {
        private final CombatEngineAPI engine;
        private final List<ShipAPI> activeGroup;
        private final List<ShipAPI> pendingClearance;
        private final float margin;

        public SeparationCollisionSafetyPlugin(CombatEngineAPI engine, List<ShipAPI> ships, float margin) {
            this.engine = engine;
            this.activeGroup = new ArrayList<>(ships);
            this.pendingClearance = new ArrayList<>(ships);
            this.margin = margin;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (engine == null || engine.isPaused() || pendingClearance.isEmpty()) {
                if (engine != null && pendingClearance.isEmpty()) {
                    engine.removePlugin(this);
                }
                return;
            }

            Iterator<ShipAPI> iterator = pendingClearance.iterator();
            while (iterator.hasNext()) {
                ShipAPI ship = iterator.next();

                if (ship == null || !ship.isAlive() || !engine.isEntityInPlay(ship)) {
                    iterator.remove();
                    activeGroup.remove(ship);
                    continue;
                }

                boolean isClearOfAllSiblings = true;
                Vector2f shipLoc = ship.getLocation();
                float shipRadius = ship.getCollisionRadius();

                for (ShipAPI sibling : activeGroup) {
                    if (sibling == ship || sibling == null || !sibling.isAlive()) continue;

                    Vector2f siblingLoc = sibling.getLocation();
                    float dx = shipLoc.x - siblingLoc.x;
                    float dy = shipLoc.y - siblingLoc.y;
                    float distSq = dx * dx + dy * dy;

                    float requiredDist = shipRadius + sibling.getCollisionRadius() + margin;
                    if (distSq < (requiredDist * requiredDist)) {
                        isClearOfAllSiblings = false;
                        break;
                    }
                }

                // Restore standard ship collision once completely clear
                if (isClearOfAllSiblings) {
                    ship.setCollisionClass(CollisionClass.SHIP);
                    iterator.remove();
                }
            }

            // Self-cleanup once all pieces are safely separated
            if (pendingClearance.isEmpty()) {
                engine.removePlugin(this);
            }
        }
    }

    public static float getArmorRatio(ShipAPI ship) {
        ArmorGridAPI grid = ship.getArmorGrid();
        if (grid == null) return 1f;

        float[][] armor = grid.getGrid();
        if (armor == null || armor.length == 0 || armor[0].length == 0) return 1f;

        float totalArmor = 0f;
        int width = armor.length;
        int height = armor[0].length;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                totalArmor += armor[x][y];
            }
        }

        float maxTotalArmor = grid.getMaxArmorInCell() * width * height;
        return maxTotalArmor > 0f ? totalArmor / maxTotalArmor : 0f;
    }

    public static void applyArmorRatio(ShipAPI ship, float armorRatio) {
        ArmorGridAPI grid = ship.getArmorGrid();
        if (grid == null) return;

        float[][] armor = grid.getGrid();
        if (armor == null) return;

        int width = armor.length;
        int height = armor[0].length;
        float maxCellArmor = grid.getMaxArmorInCell();
        float targetCellArmor = maxCellArmor * armorRatio;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid.setArmorValue(x, y, targetCellArmor);
            }
        }
    }

    public static Vector2f calculateWorldLocation(Vector2f origin, Vector2f offset, float angleDegrees) {
        double rad = Math.toRadians(angleDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float x = origin.x + offset.x * cos - offset.y * sin;
        float y = origin.y + offset.x * sin + offset.y * cos;
        return new Vector2f(x, y);
    }
}