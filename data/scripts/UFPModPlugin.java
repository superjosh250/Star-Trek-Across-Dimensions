package data.scripts;

import UFP.data.scripts.UFP_ColonizationProtocol;
import UFP.data.ui.ESM_LibControl;
import UFP.data.util.*;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import UFP.data.scripts.CoalitionFactionInjector;

import data.scripts.starsystems.Sol;
import data.scripts.starsystems.Virellia;
import data.scripts.starsystems.Pras;
import data.scripts.starsystems.PrasMarket;
import data.scripts.starsystems.VirelliaMarket;
import data.scripts.starsystems.Holland;
import data.scripts.starsystems.HollandMarket;
import data.scripts.starsystems.Athan;
import data.scripts.starsystems.AthanMarket;


public class UFPModPlugin extends BaseModPlugin {

    private static final String PRAS_MARKET_ID = "pras_station_2_market";
    private static final String VIRELLIA_MARKET_ID = "vir_custom_2_market";

    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();

        // Load all system data blocks globally
        UFP_CSV_Manager.loadAllCSVData();
        UFPSettings_Manager.loadSettings();

        UFPTextureManager.preloadTextures();
        Global.getLogger(UFPModPlugin.class).info("UFP textures preloaded on application load.");

        // ADD THIS LINE:
        ESM_LibControl.init();
        Global.getLogger(UFPModPlugin.class).info("ESM LibControl registered with LunaLib.");
    }

    @Override
    public void onNewGame() {
        SectorAPI sector = Global.getSector();

        // Generate systems/entities first
        new Sol().generate(sector);
        new Pras().generate(sector);
        new Virellia().generate(sector);
        new Holland().generate(sector);
        new Athan().generate(sector);

        // Faction relations
        FactionAPI UFP = sector.getFaction("ufp");
        FactionAPI player = sector.getFaction(Factions.PLAYER);
        FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
        FactionAPI tritachyon = sector.getFaction(Factions.TRITACHYON);
        FactionAPI pirates = sector.getFaction(Factions.PIRATES);
        FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);
        FactionAPI church = sector.getFaction(Factions.LUDDIC_CHURCH);
        FactionAPI path = sector.getFaction(Factions.LUDDIC_PATH);
        FactionAPI kol = sector.getFaction(Factions.KOL);
        FactionAPI diktat = sector.getFaction(Factions.DIKTAT);
        FactionAPI persean = sector.getFaction(Factions.PERSEAN);
        FactionAPI guard = sector.getFaction(Factions.LIONS_GUARD);

        UFP.setRelationship(player.getId(), 0.5f);
        UFP.setRelationship(hegemony.getId(), -0.4f);
        UFP.setRelationship(tritachyon.getId(), 0.3f);
        UFP.setRelationship(pirates.getId(), -1f);
        UFP.setRelationship(independent.getId(), 0.1f);
        UFP.setRelationship(persean.getId(), 0.3f);
        UFP.setRelationship(church.getId(), 0f);
        UFP.setRelationship(path.getId(), -0.8f);
        UFP.setRelationship(kol.getId(), 0.15f);
        UFP.setRelationship(diktat.getId(), 0f);
        UFP.setRelationship(guard.getId(), 0f);
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        // Ensure Pras admin logic is applied
        MarketAPI prasMarket = Global.getSector().getEconomy().getMarket(PRAS_MARKET_ID);
        if (prasMarket != null) {
            PrasMarket.forcePrasAdminVanillaStyle(prasMarket);
        }
        ensureVirelliaMarketExists();
        new HollandMarket().generate(Global.getSector());
        new AthanMarket().generate(Global.getSector());
    }

    @Override
    public void onGameLoad(boolean newGame) {
        // Inject ColTech hulls into factions & register fleet cap listener
        CoalitionFactionInjector.onGameLoad();

        Global.getSector().addTransientScript(WarpDriveManager.getInstance());

        // Instantiate our dual-purpose power tracking listener
        UFP.data.listeners.UFP_PowerCampaignListener powerListener = new UFP.data.listeners.UFP_PowerCampaignListener();

        // 1. Registers the modern RefitScreenListener engine (Closed/Saved events)
        Global.getSector().getListenerManager().addListener(powerListener);

        // 2. Registers the legacy CampaignEventListener system (Battle finished events)
        Global.getSector().addTransientListener(powerListener);

        // Always re-apply Pras admin logic on load
        MarketAPI m = Global.getSector().getEconomy().getMarket(PRAS_MARKET_ID);
        if (m != null) {
            PrasMarket.forcePrasAdminVanillaStyle(m);
        }
        ensureVirelliaMarketExists();
        new HollandMarket().generate(Global.getSector());
        new AthanMarket().generate(Global.getSector());

        // Essential safety check: prevent the script from running or crashing if loaded during a tactical battle
        if (Global.getSector() == null) return;

        // Check if the script is already active in the sector engine to prevent stacking duplicates on multiple loads
        if (!Global.getSector().hasScript(UFP_ColonizationProtocol.class)) {
            Global.getSector().addTransientScript(new UFP_ColonizationProtocol());
        }

        // 2. FIXED DIAGNOSTIC REGISTER: Load as an active engine script instead of a listener
        if (!Global.getSector().hasScript(UFP.data.logger.UFP_CargoDebugger.class)) {
            Global.getSector().addTransientScript(new UFP.data.logger.UFP_CargoDebugger());
        }

        // Double-check sync when loading a save
        ESM_LibControl.syncSettings();
    }

    private void ensureVirelliaMarketExists() {
        try {
            if (Global.getSector() == null || Global.getSector().getEconomy() == null) return;

            MarketAPI existing = Global.getSector().getEconomy().getMarket(VIRELLIA_MARKET_ID);
            if (existing != null) return;

            VirelliaMarket.addMarkets();

            Global.getLogger(UFPModPlugin.class).info("Virellia market created: " + VIRELLIA_MARKET_ID);
        } catch (Throwable t) {
            Global.getLogger(UFPModPlugin.class).error("Failed to ensure Virellia market exists", t);
        }
    }
}
