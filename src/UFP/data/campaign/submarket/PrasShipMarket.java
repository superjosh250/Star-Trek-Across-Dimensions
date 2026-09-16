package UFP.data.campaign.submarket;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;

import java.util.Random;


public class PrasShipMarket extends MilitarySubmarketPlugin {

    // The only ship entries this submarket will ever add:
    private static final String DEFIANT_VARIANT_ID = "fed_defiant";
    private static final String SOVEREIGN_HULL_VARIANT_ID = "fed_sovereign_standard";

    //Price Controls
    private static final float BASE_MARKUP_MULT = 3f;   // +200%
    private static final float SIZE_STEP = 0.08f;       // 8% per size above 3 (tune as desired)
    private static final int SIZE_PIVOT = 3;

    @Override
    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true);
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            sinceSWUpdate = 0f;

            pruneWeapons(0f);

            int weapons = 7 + Math.max(0, market.getSize() - 1) * 2;
            int fighters = 2 + Math.max(0, market.getSize() - 3);

            addWeapons(weapons, weapons + 2, 3, submarket.getFaction().getId());
            addFighters(fighters, fighters + 2, 3, market.getFactionId());

            // We completely replace only the ship listing portion:
            getCargo().getMothballedShips().clear();

            Random r = new Random(
                    market.getId().hashCode()
                            ^ submarket.getSpecId().hashCode()
                            ^ (Global.getSector().getClock().getMonth() * 170000L)
            );

            int size = market.getSize();
            int minShips = Math.max(1, 1 + (size / 3));       // 1..3 depending on size
            int maxShips = Math.max(minShips, 2 + size);      // grows with size
            int count = minShips + r.nextInt(maxShips - minShips + 1);

            // Add ONLY the allowed entries.
            for (int i = 0; i < count; i++) {
                boolean addSovereignHull = r.nextFloat() < 0.65f;
                String variantId = addSovereignHull ? SOVEREIGN_HULL_VARIANT_ID : DEFIANT_VARIANT_ID;
                try {
                    addShip(variantId, false, 1f);
                } catch (Throwable ignored) {
                    // If a variant id is missing, we silently skip so the market doesn't hard-crash.
                }
            }

            addHullMods(4, 2 + itemGenRandom.nextInt(4), submarket.getFaction().getId());
        }

        getCargo().sort();
    }

    @Override
    public String getName() {
        return "Pras Market";
    }

   //Tariff
    @Override
    public float getTariff() {
        float superTariff = super.getTariff();
        float sizeMult = getSizeMult();
        float overallMult = (1f + superTariff) * BASE_MARKUP_MULT * sizeMult;
        return Math.max(0f, overallMult - 1f);
    }

    private float getSizeMult() {
        int size = market != null ? market.getSize() : SIZE_PIVOT;
        float steps = Math.max(0, size - SIZE_PIVOT);
        return 1f + steps * SIZE_STEP;
    }
}