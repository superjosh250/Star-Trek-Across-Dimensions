package UFP.data.scripts;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class ColonizationProtocol_IntelBar extends BaseIntelPlugin {

    public static final String UPDATE_CREATED = "created";
    public static final String UPDATE_FLEET_LAUNCHED = "fleet_launched";
    public static final String UPDATE_COLONIZATION_STARTED = "colonization_started";
    public static final String UPDATE_ESTABLISHED = "established";
    public static final String UPDATE_FAILED = "failed";

    private final ColonizationFleetManager.ColonyOperation operation;
    private final String factionId;

    public ColonizationProtocol_IntelBar(ColonizationFleetManager.ColonyOperation operation, String factionId) {
        this.operation = operation;
        this.factionId = factionId;

        setImportant(true);
        Global.getSector().getIntelManager().addIntel(this, true);
        sendUpdateIfPlayerHasIntel(UPDATE_CREATED, false, false);
    }

    public void notifyFleetLaunched() {
        sendUpdateIfPlayerHasIntel(UPDATE_FLEET_LAUNCHED, false, false);
    }

    public void notifyColonizationStarted() {
        sendUpdateIfPlayerHasIntel(UPDATE_COLONIZATION_STARTED, false, false);
    }

    public void notifyEstablished() {
        sendUpdateIfPlayerHasIntel(UPDATE_ESTABLISHED, false, false);
    }

    public void notifyFailed() {
        sendUpdateIfPlayerHasIntel(UPDATE_FAILED, false, false);
    }

    @Override
    public String getName() {
        String targetName = operation.target != null ? operation.target.getName() : "Unknown world";
        return switch (operation.state) {
            case PLANNED -> "UFP colonization planned: " + targetName;
            case EN_ROUTE -> "UFP colony fleet en route: " + targetName;
            case SETTLING -> "UFP colonization underway: " + targetName;
            case ESTABLISHED -> "UFP colony established: " + targetName;
            case FAILED -> "UFP colonization failed: " + targetName;
        };
    }

    @Override
    public String getIcon() {
        if (Global.getSector().getFaction(factionId) != null) {
            return Global.getSector().getFaction(factionId).getCrest();
        }
        return null;
    }

    @Override
    public Color getTitleColor(ListInfoMode mode) {
        if (Global.getSector().getFaction(factionId) != null) {
            return Global.getSector().getFaction(factionId).getBaseUIColor();
        }
        return super.getTitleColor(mode);
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        addBulletPoints(info, mode);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        String target = operation.target != null ? operation.target.getName() : "Unknown";
        String system = operation.target != null && operation.target.getStarSystem() != null
                ? operation.target.getStarSystem().getNameWithLowercaseTypeShort()
                : "Unknown system";

        bullet(info);
        info.addPara("Target world: %s (%s)", initPad, tc, Misc.getHighlightColor(), target, system);

        bullet(info);
        info.addPara("Status: %s", 0f, tc, Misc.getHighlightColor(), getStatusText());

        if (operation.state == ColonizationFleetManager.OperationState.PLANNED) {
            bullet(info);
            info.addPara("Days until departure: %s", 0f, tc, Misc.getHighlightColor(),
                    formatDays(operation.daysUntilDeparture));

            bullet(info);
            info.addPara("Estimated travel time: %s", 0f, tc, Misc.getHighlightColor(),
                    formatDays(operation.daysUntilArrival));
        } else if (operation.state == ColonizationFleetManager.OperationState.EN_ROUTE) {
            bullet(info);
            info.addPara("Colony fleet: %s", 0f, tc, Misc.getHighlightColor(),
                    operation.colonyFleet != null ? operation.colonyFleet.getName() : "Unknown");

            bullet(info);
            info.addPara("Days until arrival: %s", 0f, tc, Misc.getHighlightColor(),
                    formatDays(operation.daysUntilArrival));
        } else if (operation.state == ColonizationFleetManager.OperationState.SETTLING) {
            bullet(info);
            info.addPara("Days until colonization: %s", 0f, tc, Misc.getHighlightColor(),
                    formatDays(operation.daysUntilColonization));
        }

        if (operation.sourceMarket != null) {
            bullet(info);
            info.addPara("Departure point: %s", 0f, tc, Misc.getHighlightColor(),
                    operation.sourceMarket.getName());
        }

        unindent(info);
    }

    @Override
    public boolean hasSmallDescription() {
        return true;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        String target = operation.target != null ? operation.target.getName() : "Unknown";
        String status = getStatusText();

        info.addPara("The United Federation of Planets has selected %s for colonization.", opad,
                Misc.getHighlightColor(), target);

        info.addPara("Current phase: %s", opad, Misc.getHighlightColor(), status);

        if (operation.state == ColonizationFleetManager.OperationState.PLANNED) {
            info.addPara("A colony fleet is mobilizing. Days until departure: %s", opad,
                    Misc.getHighlightColor(), formatDays(operation.daysUntilDeparture));
            info.addPara("Estimated days until arrival: %s", opad,
                    Misc.getHighlightColor(), formatDays(operation.daysUntilArrival));
        } else if (operation.state == ColonizationFleetManager.OperationState.EN_ROUTE) {
            info.addPara("The colony fleet is traveling toward %s. Days until arrival: %s", opad,
                    Misc.getHighlightColor(),
                    target,
                    formatDays(operation.daysUntilArrival));
        } else if (operation.state == ColonizationFleetManager.OperationState.SETTLING) {
            info.addPara("The fleet has arrived and is beginning settlement operations. Days until colony establishment: %s",
                    opad, Misc.getHighlightColor(), formatDays(operation.daysUntilColonization));
        } else if (operation.state == ColonizationFleetManager.OperationState.ESTABLISHED) {
            info.addPara("The colony has been successfully established on %s.", opad,
                    Misc.getHighlightColor(), target);
        } else if (operation.state == ColonizationFleetManager.OperationState.FAILED) {
            info.addPara("The colonization attempt for %s has failed.", opad,
                    Misc.getNegativeHighlightColor(), target);
        }

        addDeleteButton(info, width, "Dismiss report");
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return operation.target;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<>(super.getIntelTags(map));
        tags.add(Tags.INTEL_FLEET_LOG);
        tags.add(factionId);
        return tags;
    }

    @Override
    public String getSortString() {
        return "UFP Colonization";
    }

    @Override
    public com.fs.starfarer.api.campaign.FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(factionId);
    }

    private String getStatusText() {
        return switch (operation.state) {
            case PLANNED -> "Colony fleet mobilizing";
            case EN_ROUTE -> "Fleet en route";
            case SETTLING -> "Colonization in progress";
            case ESTABLISHED -> "Colony established";
            case FAILED -> "Colonization failed";
        };
    }

    private String formatDays(float days) {
        return String.format("%.1f", Math.max(0f, days));
    }
}