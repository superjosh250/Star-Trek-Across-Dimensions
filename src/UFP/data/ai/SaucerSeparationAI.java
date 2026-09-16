package UFP.data.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class SaucerSeparationAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private final Random random = new Random();
    private boolean triedEarlyActivation = false;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (system == null || ship == null || !ship.isAlive() || system.isActive()) return;

        // FIX: Ensure the engine context is valid, then stop execution if this is the player
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == engine.getPlayerShip()) return;

        float hullRatio = ship.getHitpoints() / ship.getMaxHitpoints();

        // Early battle random activation (only once)
        if (!triedEarlyActivation && ship.getFullTimeDeployed() < 5f) {
            triedEarlyActivation = true;
            float chance = 0.25f + random.nextFloat() * 0.05f; // 25%–30%
            if (random.nextFloat() < chance) {
                ship.useSystem();
                return;
            }
        }

        // Mid-damage activation
        if (hullRatio <= 0.6f && hullRatio > 0.4f) {
            float chance = 0.25f + random.nextFloat() * 0.15f; // 25%–40%
            if (random.nextFloat() < chance) {
                ship.useSystem();
                return;
            }
        }

        // Heavy damage activation
        if (hullRatio <= 0.4f) {
            float chance = 0.7f;
            if (random.nextFloat() < chance) {
                ship.useSystem();
            }
        }
    }

    public ShipSystemStatsScript.State getState() {
        return ShipSystemStatsScript.State.ACTIVE;
    }
}