package UFP.data.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;
import UFP.data.util.UFPSettings_Manager;
import java.util.Set;

public class UFP_PowerCampaignListener extends BaseCampaignEventListener implements RefitScreenListener {

    public UFP_PowerCampaignListener() {
        super(false);
    }

    /**
     * Shared logic to safely clean up reactor grid metrics if a valid core configuration is matched.
     */
    private void checkAndCleanReactorState(FleetMemberAPI member) {
        if (member == null || member.getVariant() == null) return;

        var variant = member.getVariant();
        Set<String> validReactors = UFPSettings_Manager.getValidPowerReactors();
        if (validReactors == null || validReactors.isEmpty()) return;

        boolean hasReactorInstalled = false;
        for (String reactorId : validReactors) {
            if (variant.hasHullMod(reactorId) ||
                    variant.getHullMods().contains(reactorId) ||
                    (member.getHullSpec() != null && member.getHullSpec().isBuiltIn(reactorId))) {
                hasReactorInstalled = true;
                break;
            }
        }

        // Event Correction: If a new core is fitted, strip out the broken grid penalty cleanly
        if (hasReactorInstalled && variant.hasHullMod("main_power_failure")) {
            // If it's a stock hull variant, make it editable first to avoid breaking database defaults
            if (variant.isStockVariant()) {
                variant = variant.clone();
                variant.setSource(VariantSource.REFIT);
                member.setVariant(variant, false, true);
            }
            variant.removeMod("main_power_failure");

            // Wipe persistent cross-combat engine markers
            Object o = Global.getSector().getMemoryWithoutUpdate().get("$UFP_ejectReactor_used_members");
            if (o instanceof Set) {
                ((Set<?>) o).remove(member.getId());
            }
        }
    }

    public void reportRefitScreenClosed(FleetMemberAPI member) {
        checkAndCleanReactorState(member);
    }

    @Override
    public void reportFleetMemberVariantSaved(FleetMemberAPI member, MarketAPI market) {
        checkAndCleanReactorState(member);
    }

    /**
     * Guarantees that if a reactor core blew up or ejected during tactical combat,
     * the persistent 'main_power_failure' mod maps directly to the fleet member on the overworld.
     */
    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        Object o = Global.getSector().getMemoryWithoutUpdate().get("$UFP_ejectReactor_used_members");
        if (!(o instanceof Set)) return;

        @SuppressWarnings("unchecked")
        Set<String> ejectedIds = (Set<String>) o;
        if (ejectedIds.isEmpty()) return;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null || playerFleet.getFleetData() == null) return;

        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (ejectedIds.contains(member.getId())) {
                var variant = member.getVariant();
                if (variant != null && !variant.hasHullMod("main_power_failure")) {
                    if (variant.isStockVariant()) {
                        variant = variant.clone();
                        variant.setSource(VariantSource.REFIT);
                        member.setVariant(variant, false, true);
                    }
                    variant.addMod("main_power_failure");
                }
            }
        }
    }
}