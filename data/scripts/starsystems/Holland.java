package data.scripts.starsystems;

import java.awt.Color;
import java.util.Random;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import org.lwjgl.util.vector.Vector2f;

public class Holland implements SectorGeneratorPlugin {

    private static final float AU = 10000f;

    @Override
    public void generate(SectorAPI sector) {

        StarSystemAPI system = sector.createStarSystem("Holland");
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");

        system.getMemoryWithoutUpdate().set("$musicSetId", "starbase_12");

        PlanetAPI star = system.initStar(
                "holland_star",
                StarTypes.YELLOW,
                750f,
                -47550f,
                12500f,
                350f
        );

        system.setLightColor(new Color(255, 245, 235));

        Random rng = new Random("HOLLAND_SYSTEM_SEED".hashCode());

        float r1 = 0.45f * AU;
        float r2 = 0.75f * AU;
        float r3 = 1.10f * AU;
        float r4 = 1.70f * AU;
        float r5 = 2.35f * AU;
        float r6 = 3.10f * AU;

        // 1) Inner rocky
        PlanetAPI p1 = system.addPlanet(
                "holland_ember",
                star,
                "Holland I",
                "rocky_metallic",
                rngAngle(rng),
                110f,
                r1,
                80f
        );
        addRandomMoons(system, rng, p1, 0, 1);

        // 2) Irradiated large world
        float irrOrbitDays = 140f;
        PlanetAPI irradiated = system.addPlanet(
                "holland_irradiated",
                star,
                "Holland II",
                "irradiated",
                rngAngle(rng),
                240f,
                r2,
                irrOrbitDays
        );

        float mfInner = irradiated.getRadius() + 250f;
        float mfOuter = irradiated.getRadius() + 1300f;
        float mfMiddle = (mfInner + mfOuter) * 0.5f;
        float mfBandWidth = (mfOuter - mfInner);

        MagneticFieldTerrainPlugin.MagneticFieldParams mfParams =
                new MagneticFieldTerrainPlugin.MagneticFieldParams(
                        mfBandWidth,
                        mfMiddle,
                        irradiated,
                        mfInner,
                        mfOuter,
                        new Color(60, 30, 110, 80),
                        1.2f,
                        new Color(140, 100, 235),
                        new Color(90, 200, 170),
                        new Color(20, 220, 70)
                );

        SectorEntityToken magField = system.addTerrain(Terrain.MAGNETIC_FIELD, mfParams);
        magField.setCircularOrbit(irradiated, 0f, 0f, irradiated.getCircularOrbitPeriod());

        system.addRingBand(
                irradiated,
                "misc",
                "rings_asteroids0",
                256f,
                0,
                new Color(180, 180, 180, 255),
                300f,
                irradiated.getRadius() + 550f,
                60f
        );

        system.addAsteroidBelt(
                star,
                280,
                r2,
                650f,
                irrOrbitDays,
                irrOrbitDays,
                Terrain.ASTEROID_BELT,
                irradiated.getName() + " Belt"
        );

        addRandomMoons(system, rng, irradiated, 0, 2);

        // 3) New Holland
        float nhOrbitDays = 220f;
        PlanetAPI newHolland = system.addPlanet(
                "holland_new_holland",
                star,
                "New Holland",
                "terran",
                rngAngle(rng),
                190f,
                r3,
                nhOrbitDays
        );
        data.scripts.starsystems.HollandMarket.addHollandMarket(sector, newHolland);

        system.addPlanet(
                "holland_nh_moon",
                newHolland,
                "Holland Zed",
                "barren",
                rngAngle(rng),
                60f,
                450f,
                30f
        );

        CustomCampaignEntityAPI nhPlatform = system.addCustomEntity(
                "holland_nh_platform",
                "Holland Orbital Platform",
                DimensionsCrossedIDS.STATION_SPACEDOCK,
                DimensionsCrossedIDS.UFP
        );
        nhPlatform.setCircularOrbitPointingDown(newHolland, rngAngle(rng), 750f, 35f);

        CustomCampaignEntityAPI commRelay = system.addCustomEntity(
                "holland_comm_relay",
                "Holland Comm Relay",
                Entities.COMM_RELAY,
                DimensionsCrossedIDS.UFP
        );
        commRelay.setCircularOrbitPointingDown(star,
                (newHolland.getCircularOrbitAngle() + 18f) % 360f,
                r3 + 350f,
                nhOrbitDays);

        CustomCampaignEntityAPI sensorArray = system.addCustomEntity(
                "holland_sensor_array",
                "Holland Sensor Array",
                Entities.SENSOR_ARRAY,
                DimensionsCrossedIDS.UFP
        );
        sensorArray.setCircularOrbitPointingDown(star,
                (newHolland.getCircularOrbitAngle() - 22f + 360f) % 360f,
                r3 - 450f,
                nhOrbitDays);

        JumpPointAPI nhJump = Global.getFactory().createJumpPoint("holland_nh_jump", "New Holland Jump-point");
        nhJump.setStandardWormholeToHyperspaceVisual();
        nhJump.setRelatedPlanet(newHolland);

        float jpRadius = r3 + 0.22f * AU;
        nhJump.setCircularOrbit(star,
                (newHolland.getCircularOrbitAngle() + 55f) % 360f,
                jpRadius,
                nhOrbitDays);
        system.addEntity(nhJump);

        // 4) Gas giant
        PlanetAPI p4 = system.addPlanet(
                "holland_gas",
                star,
                "Holland Alpha",
                "gas_giant",
                rngAngle(rng),
                330f,
                r4,
                360f
        );
        addRandomMoons(system, rng, p4, 1, 3);

        // 5) Cold outer
        PlanetAPI p5 = system.addPlanet(
                "holland_cold",
                star,
                "Holland Beta",
                "cryovolcanic",
                rngAngle(rng),
                160f,
                r5,
                520f
        );
        addRandomMoons(system, rng, p5, 0, 2);

        // 6) Far planet
        PlanetAPI p6 = system.addPlanet(
                "holland_far",
                star,
                "Holland Gamma",
                "barren",
                rngAngle(rng),
                170f,
                r6,
                740f
        );
        addRandomMoons(system, rng, p6, 0, 3);

        // Debris fields
        addDebrisField(system, rng, star, 0.95f * r4, 480f, 0.70f, 999999f, 60f);
        addDebrisField(system, rng, star, 1.05f * r5, 650f, 0.80f, 120f, 30f);
        addDebrisField(system, rng, star, 0.90f * r6, 900f, 0.55f, 240f, 40f);

        // --- Map grid sizing ---
        float maxDist = 0f;
        SectorEntityToken center = system.getCenter();
        Vector2f centerLoc = center.getLocation();

        for (SectorEntityToken entity : system.getAllEntities()) {
            Vector2f loc = entity.getLocation();
            if (loc == null) continue;
            float dist = Misc.getDistance(centerLoc, loc);
            if (dist > maxDist) maxDist = dist;
        }

        float padding = 8500f;
        float gridSize = (maxDist * 2f) + padding;

        system.setMapGridWidthOverride(gridSize);
        system.setMapGridHeightOverride(gridSize);

        system.autogenerateHyperspaceJumpPoints(true, true);

        new data.scripts.starsystems.HollandMarket().generate(sector);
    }

    private static float rngAngle(Random rng) {
        return rng.nextFloat() * 360f;
    }

    private static String pickType(Random rng, String... options) {
        return options[rng.nextInt(options.length)];
    }

    private static void addRandomMoons(StarSystemAPI system, Random rng, PlanetAPI parent, int min, int max) {
        int count = min + rng.nextInt((max - min) + 1);
        float base = parent.getRadius() + 250f;

        for (int i = 0; i < count; i++) {
            float orbitRadius = base + i * (160f + rng.nextFloat() * 120f);
            float orbitDays = 18f + rng.nextFloat() * 40f;
            float radius = 35f + rng.nextFloat() * 35f;

            system.addPlanet(
                    parent.getId() + "_m" + (i + 1),
                    parent,
                    "Moon " + (i + 1),
                    pickType(rng, "barren", "rocky_metallic", "frozen", "airless"),
                    rngAngle(rng),
                    radius,
                    orbitRadius,
                    orbitDays
            );
        }
    }

    private static void addDebrisField(StarSystemAPI system, Random rng, SectorEntityToken focus,
                                       float orbitRadius, float bandWidth, float density,
                                       float lastsDays, float glowsDays) {

        DebrisFieldTerrainPlugin.DebrisFieldParams params =
                new DebrisFieldTerrainPlugin.DebrisFieldParams(bandWidth, density, lastsDays, glowsDays);

        params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.MIXED;
        params.baseSalvageXP = 450;
        params.defenderProb = 0.10f;
        params.maxDefenderSize = 1;

        SectorEntityToken field = system.addTerrain(Terrain.DEBRIS_FIELD, params);
        float orbitDays = 220f + rng.nextFloat() * 500f;
        field.setCircularOrbit(focus, rngAngle(rng), orbitRadius, orbitDays);
    }
}