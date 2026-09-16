
package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lwjgl.util.vector.Vector2f;
import UFP.data.plugins.SelfDestructVisual;

import java.awt.Color;

public class SelfDestruct extends BaseShipSystemScript {

    private static final float EXPLOSION_RADIUS = 6000f;
    private static final float EXPLOSION_DAMAGE = 15000f;
    private static final float EMP_DAMAGE = 2000f;
    private static final Color EXPLOSION_COLOR = new Color(206, 227, 232, 230);
    private static final Color JITTER_COLOR = new Color(154, 236, 239, 150);

    private static final float COUNTDOWN_TIME = 3f;
    private float timeElapsed = 0f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        ship.setJitter(this, JITTER_COLOR, effectLevel, 5, 0f, 10f);
        ship.setJitterUnder(this, JITTER_COLOR, effectLevel, 10, 0f, 15f);

        if (state == State.ACTIVE) {
            timeElapsed += Global.getCombatEngine().getElapsedInLastFrame();

            // ✅ Draw danger rings during countdown
            SelfDestructVisual.drawDangerRings(ship, timeElapsed);

            if (timeElapsed >= COUNTDOWN_TIME) {
                triggerExplosion(ship);
                destroyShipForever(ship);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        timeElapsed = 0f;
        SelfDestructVisual.resetLogging(); // ✅ Reset logging state
    }


    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && state == State.ACTIVE) {
            float remaining = Math.max(0f, COUNTDOWN_TIME - timeElapsed);
            return new StatusData("Self-destruct in: " + String.format("%.1f", remaining) + "s", false);
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return ship != null && ship.isAlive() && !ship.getFluxTracker().isOverloaded();
    }

    private void triggerExplosion(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        Vector2f loc = ship.getLocation();

        engine.spawnExplosion(loc, ship.getVelocity(), EXPLOSION_COLOR, EXPLOSION_RADIUS, 2f);
        Global.getSoundPlayer().playSound("devastator_explosion", 1f, 1f, loc, ship.getVelocity());

        for (ShipAPI target : engine.getShips()) {
            if (target.isAlive() && target != ship) {
                float dist = Math.max(1f, Vector2f.sub(target.getLocation(), loc, null).length());
                if (dist <= EXPLOSION_RADIUS) {
                    float distanceFactor = dist / 1000f;
                    float reduction = 0.10f + (float) Math.random() * 0.05f;
                    float damageMultiplier = Math.max(0.1f, 1f - (distanceFactor * reduction));

                    float damage = EXPLOSION_DAMAGE * damageMultiplier;
                    engine.applyDamage(target, target.getLocation(), damage, DamageType.HIGH_EXPLOSIVE, EMP_DAMAGE, false, false, ship);
                }
            }
        }

        engine.applyDamage(ship, loc, ship.getHitpoints() * 10f, DamageType.FRAGMENTATION, 0f, true, false, ship);
    }


    private void destroyShipForever(ShipAPI ship) {
        FleetMemberAPI member = ship.getFleetMember();
        if (member != null) {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null && fleet.getFleetData().getMembersListCopy().contains(member)) {
                fleet.getFleetData().removeFleetMember(member);
                Global.getSector().getCampaignUI().addMessage("Ship " + member.getShipName() + " was destroyed and cannot be recovered.");
            }
        }
    }
}
