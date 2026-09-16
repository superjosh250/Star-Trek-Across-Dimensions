package UFP.data.systems;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

import java.util.*;

public class FederationCriticalPhotonicCharge extends BaseShipSystemScript {

    private static final String CRITICAL_WEAPON_ID = "photonTorpedo_critical";
    private final Set<String> firedShips = new HashSet<>();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        String shipId = ship.getId();

        if (state == State.ACTIVE && !firedShips.contains(shipId)) {
            Global.getLogger(this.getClass()).info("System activated: firing photon torpedoes from valid SYSTEM slots on ship: " + shipId);
            firePhotonTorpedoes(ship);
            firedShips.add(shipId);
        } else if (state == State.COOLDOWN || state == State.IDLE) {
            firedShips.remove(shipId); // Reset so it can fire again next time
        }
    }


    private void firePhotonTorpedoes(ShipAPI ship) {
        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.isSystemSlot() && slot.getId().startsWith("FWD Crit Launcher")) {
                Global.getLogger(this.getClass()).info("Firing from slot: " + slot.getId());

                Global.getCombatEngine().spawnProjectile(
                        ship,
                        null,
                        CRITICAL_WEAPON_ID,
                        slot.computePosition(ship),
                        slot.getAngle() + ship.getFacing(),
                        ship.getVelocity()
                );
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            firedShips.remove(ship.getId());
            Global.getLogger(this.getClass()).info("System unapply: cleanup for ship " + ship.getId());
        }
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && state == State.ACTIVE) {
            return new StatusData("Photon torpedoes launched", false);
        }
        return null;
    }
}
