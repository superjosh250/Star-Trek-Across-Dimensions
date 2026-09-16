package UFP.data.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import UFP.data.util.EnergySystemManager;
import UFP.data.util.ShieldHitpointManager;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class DetachModuleAI implements ShipSystemAIScript {

    private static final Random RNG = new Random();

    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;

    // Zero-allocation interval timer to check logic every 0.5 seconds
    private final IntervalUtil evaluationInterval = new IntervalUtil(0.4f, 0.6f);

    // Dynamic initial delay window setup (e.g. shortly after start or after ~45s)
    private float requiredCombatTime = -1f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.engine = engine;

        // Determine if this AI instance waits a short period (5-15s) or extended period (40-50s)
        if (RNG.nextBoolean()) {
            this.requiredCombatTime = 5f + RNG.nextFloat() * 10f;
        } else {
            this.requiredCombatTime = 40f + RNG.nextFloat() * 10f;
        }
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (ship == null || system == null || engine == null || !ship.isAlive()) return;

        if (system.isActive() || system.isCoolingDown() || system.getAmmo() == 0) {
            return;
        }

        // Wait for randomized initial combat engagement window
        if (engine.getTotalElapsedTime(false) < requiredCombatTime) {
            return;
        }

        evaluationInterval.advance(amount);
        if (evaluationInterval.intervalElapsed()) {
            if (shouldTriggerUndock()) {
                ship.useSystem();
            }
        }
    }

    private boolean shouldTriggerUndock() {
        // ---------------------------------------------------------------------
        // 1. Enemy Proximity Check
        // ---------------------------------------------------------------------
        // Dynamic detection radius between 1200 - 2200 units
        float searchRadiusSq = (1200f + RNG.nextFloat() * 1000f);
        searchRadiusSq *= searchRadiusSq;

        boolean enemyNearby = false;
        for (ShipAPI other : engine.getShips()) {
            if (other != null && other.isAlive() && !other.isFighter() && other.getOwner() != ship.getOwner()) {
                if (Misc.getDistanceSq(ship.getLocation(), other.getLocation()) <= searchRadiusSq) {
                    enemyNearby = true;
                    break;
                }
            }
        }

        // Proximity alone gives a randomized chance to trigger (e.g. 15% chance per tick)
        if (enemyNearby && RNG.nextFloat() < 0.15f) {
            return true;
        }

        // ---------------------------------------------------------------------
        // 2. Custom Shield HP & Depletion Check
        // ---------------------------------------------------------------------
        if (ShieldHitpointManager.isShieldDisabled(ship)) {
            // High chance if shield is completely broken/depleted
            if (RNG.nextFloat() < 0.65f) return true;
        } else {
            float maxShieldHp = ShieldHitpointManager.getMaxShieldHP(ship);
            if (maxShieldHp > 0f) {
                float currShieldHp = ShieldHitpointManager.getShieldHP(ship);
                float shieldRatio = currShieldHp / maxShieldHp;

                // Random threshold up to 50% max HP
                float randomShieldThreshold = 0.10f + RNG.nextFloat() * 0.40f;
                if (shieldRatio < randomShieldThreshold && RNG.nextFloat() < 0.40f) {
                    return true;
                }
            }
        }

        // ---------------------------------------------------------------------
        // 3. ESM Energy Check (0% - 15% power)
        // ---------------------------------------------------------------------
        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(engine, ship);
        if (esm != null) {
            float pwrLevel = esm.getEnergyLevel(); // Returns 0.0 to 1.0
            // Random threshold up to 15% power
            float randomPwrThreshold = RNG.nextFloat() * 0.15f;
            if (pwrLevel <= randomPwrThreshold && RNG.nextFloat() < 0.50f) {
                return true;
            }
        }

        // ---------------------------------------------------------------------
        // 4. Low Armor Check (< 50% threshold limit)
        // ---------------------------------------------------------------------
        // Random threshold capped at 50% max
        float randomArmorThreshold = 0.10f + RNG.nextFloat() * 0.40f;
        float currentArmorRatio = getAverageArmorRatio(ship);
        if (currentArmorRatio < randomArmorThreshold && RNG.nextFloat() < 0.45f) {
            return true;
        }

        // ---------------------------------------------------------------------
        // 5. Low Hull Hitpoints Check (< 75% threshold limit)
        // ---------------------------------------------------------------------
        // Random threshold capped at 75% max
        float randomHullThreshold = 0.20f + RNG.nextFloat() * 0.55f;
        if (ship.getHullLevel() < randomHullThreshold && RNG.nextFloat() < 0.35f) {
            return true;
        }

        return false;
    }

    private float getAverageArmorRatio(ShipAPI ship) {
        if (ship.getArmorGrid() == null) return 1f;
        float[][] grid = ship.getArmorGrid().getGrid();
        if (grid == null) return 1f;

        float maxArmorCell = ship.getArmorGrid().getMaxArmorInCell();
        if (maxArmorCell <= 0f) return 1f;

        float totalArmor = 0f;
        int count = 0;

        for (int x = 0; x < grid.length; x++) {
            for (int y = 0; y < grid[x].length; y++) {
                totalArmor += grid[x][y];
                count++;
            }
        }

        if (count == 0) return 1f;
        return (totalArmor / (count * maxArmorCell));
    }
}