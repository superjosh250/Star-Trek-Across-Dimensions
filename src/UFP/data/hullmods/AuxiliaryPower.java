package UFP.data.hullmods;

import UFP.data.util.EnergySystemManager;
import UFP.data.util.UFPSettings_Manager;
import UFP.data.util.UFP_CSV_Manager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class AuxiliaryPower extends BaseHullMod {

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null || !ship.isAlive()) return;

        // Fetch our active tracker instance
        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(engine, ship);
        if (esm == null) return;

        // WORKLOAD REDUCTION: Replaced the massive local loop with the cached ESM framework check
        if (esm.verifyShipHasReactorHullmod(ship)) {
            return;
        }

        // FRAMEWORK SYNC: Tell the ESM manager that a valid power source is online
        esm.setHasPowerSystem(true);

        // Determine our targeted absolute functional maximum cap
        float functionalMaxCeiling = 0f;

        String hullId = ship.getHullSpec().getHullId();
        UFP_CSV_Manager.ESMDataEntry csvEntry = UFP_CSV_Manager.getEsmData(hullId);
        if (csvEntry == null && ship.getHullSpec().getBaseHullId() != null) {
            csvEntry = UFP_CSV_Manager.getEsmData(ship.getHullSpec().getBaseHullId());
        }

        if (csvEntry != null) {
            if (csvEntry.auxPwr > 0f) {
                // Priority A: Use absolute explicit numeric value provided by the CSV row
                functionalMaxCeiling = csvEntry.auxPwr;
            } else {
                // Priority B: Fallback if matching CSV row entry lacks an explicit auxiliary power value
                functionalMaxCeiling = csvEntry.mainPwr * 0.20f;
            }
        } else {
            // Priority C: Total fallback using vanilla ship attributes
            functionalMaxCeiling = ship.getHullSpec().getFluxCapacity() * 0.20f;
        }

        // Dynamically enforce the functional limitation.
        if (esm.getCurrEnergy() > functionalMaxCeiling) {
            esm.setCurrEnergy(functionalMaxCeiling);
        }
    }
}