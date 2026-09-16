package data.scripts.starsystems;

import java.awt.Color;
import java.util.Random;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class Virellia {

    // Pushed-out AU scaling (less cramped)
    private static float AU(float au) {
        return 2500f + au * 1200f;
    }

    // Aesthetic orbit days from radius
    private static float ORBIT_DAYS(float orbitRadius) {
        return Math.max(80f, orbitRadius / 18f);
    }

    private static final float SENSOR_PROFILE = 12000f;

    // For vanilla salvage entities, it's safe to assign specials because their IDs exist in salvage_entity_gen_data.csv
    private static void makeVanillaSalvageable(SectorEntityToken entity) {
        entity.setInteractionImage("illustrations", "space_wreckage");
        SalvageSpecialAssigner.assignSpecials(entity, true);
    }

    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem("Virellia");
        system.getLocation().set(-16750f, -17200f);
        system.getMapGridHeightOverride();
        system.getMapGridWidthOverride();

        LocationAPI hyper = Global.getSector().getHyperspace();
        system.setBackgroundTextureFilename("graphics/backgrounds/backgroundUFP_1.jpg");

        // Star
        PlanetAPI star = system.initStar("virellia_star", "star_yellow", 850f, 220f);
        system.setLightColor(new Color(255, 245, 225));

        // ============================================================
        // 6 INNER PLANETS (spread out)
        // ============================================================
        system.addPlanet("vir_p1", star, "Kestrel", Planets.BARREN, 10f, 55f, AU(0.35f), ORBIT_DAYS(AU(0.35f)));
        system.addPlanet("vir_p2", star, "Sable", Planets.BARREN_DESERT, 55f, 70f, AU(0.55f), ORBIT_DAYS(AU(0.55f)));
        system.addPlanet("vir_p3", star, "Hearth", Planets.TUNDRA, 120f, 90f, AU(0.85f), ORBIT_DAYS(AU(0.85f)));
        system.addPlanet("vir_p4", star, "Tarn", Planets.ROCKY_METALLIC, 200f, 65f, AU(1.20f), ORBIT_DAYS(AU(1.20f)));
        system.addPlanet("vir_p5", star, "Cinder", Planets.IRRADIATED, 260f, 85f, AU(1.75f), ORBIT_DAYS(AU(1.75f)));
        system.addPlanet("vir_p6", star, "Mirth", Planets.DESERT, 315f, 95f, AU(2.60f), ORBIT_DAYS(AU(2.60f)));

        system.addRingBand(star, "misc", "rings_dust0",
                256f, 0, Color.white, 256f,
                AU(3.2f), ORBIT_DAYS(AU(3.2f)),
                null, null);

        // ============================================================
        // GIANTS
        // ============================================================
        PlanetAPI ig1 = system.addPlanet("vir_ig1", star, "Orun", Planets.ICE_GIANT, 20f, 210f, AU(5.2f), ORBIT_DAYS(AU(5.2f)));
        PlanetAPI ig2 = system.addPlanet("vir_ig2", star, "Vaska", Planets.ICE_GIANT, 80f, 230f, AU(7.8f), ORBIT_DAYS(AU(7.8f)));
        PlanetAPI ig3 = system.addPlanet("vir_ig3", star, "Nhar", Planets.ICE_GIANT, 150f, 240f, AU(10.5f), ORBIT_DAYS(AU(10.5f)));
        PlanetAPI ig4 = system.addPlanet("vir_ig4", star, "Eidolon", Planets.ICE_GIANT, 240f, 250f, AU(13.2f), ORBIT_DAYS(AU(13.2f)));
        PlanetAPI gg1 = system.addPlanet("vir_gg1", star, "Brontes", Planets.GAS_GIANT, 310f, 320f, AU(15.5f), ORBIT_DAYS(AU(15.5f)));
        PlanetAPI gg2 = system.addPlanet("vir_gg2", star, "Kron", Planets.GAS_GIANT, 350f, 340f, AU(18.0f), ORBIT_DAYS(AU(18.0f)));

        // Rings on 2 giants
        system.addRingBand(gg1, "misc", "rings_ice0", 256f, 1, Color.white, 256f, gg1.getRadius() + 280f, 50f, null, null);
        system.addRingBand(ig3, "misc", "rings_asteroids0", 256f, 0, Color.white, 256f, ig3.getRadius() + 240f, 60f, null, null);

        // Giant moons (wider than before)
        system.addPlanet("vir_ig1_m1", ig1, "Orun-I", Planets.ROCKY_ICE, 0f, 45f, 900f, 55f);
        system.addPlanet("vir_ig1_m2", ig1, "Orun-II", Planets.BARREN, 120f, 55f, 1350f, 80f);
        system.addPlanet("vir_ig2_m1", ig2, "Vaska-I", Planets.FROZEN, 30f, 50f, 980f, 60f);
        system.addPlanet("vir_gg2_m1", gg2, "Kron-I", Planets.PLANET_LAVA, 70f, 70f, 1500f, 110f);
        system.addPlanet("vir_gg2_m2", gg2, "Kron-II", Planets.FROZEN, 150f, 55f, 1850f, 135f);

        // ============================================================
        // SPECIAL CHAIN: ice giant -> gasMoon -> microStar + tundra opposite
        // ============================================================
        PlanetAPI gasMoon = system.addPlanet("vir_ig2_gasm", ig2, "Vaska-Than", Planets.GAS_GIANT,
                220f, 180f,
                2200f, 160f);

        // microStar further out
        system.addPlanet("vir_microstar", gasMoon, "Candlepin", StarTypes.NEUTRON_STAR,
                0f, 70f,
                1500f, 120f);

        // tundra opposite microStar (same radius, 180 degrees apart)
        system.addPlanet("vir_microstar_tundra", gasMoon, "Fenn", Planets.TUNDRA,
                180f, 55f,
                1500f, 120f);

        // ============================================================
        // OUTER BELT: visual + REAL asteroids + cluster
        // ============================================================
        float beltRadius = AU(22.0f);

        // Visual ring
        system.addRingBand(star, "misc", "rings_asteroids0",
                256f, 2, Color.white, 256f,
                beltRadius, ORBIT_DAYS(beltRadius),
                null, null);

        // REAL asteroid belt (interactable)
        system.addAsteroidBelt(star, 350, beltRadius, 700f, 220f, 320f, Terrain.ASTEROID_BELT, "Virellia Outer Belt");

        // Asteroid field cluster
        SectorEntityToken outerField = system.addTerrain(Terrain.ASTEROID_FIELD,
                new AsteroidFieldParams(900f, 1300f, 45, 70, 6f, 18f, "Virellia Outer Drift"));
        outerField.setCircularOrbit(star, 110f, beltRadius + 900f, ORBIT_DAYS(beltRadius + 900f));

        // Two planets in the belt region
        system.addPlanet("vir_belt_p1", star, "Shale", Planets.BARREN, 160f, 70f, beltRadius - 800f, ORBIT_DAYS(beltRadius - 800f));
        system.addPlanet("vir_belt_p2", star, "Glint", Planets.ROCKY_ICE, 210f, 85f, beltRadius + 700f, ORBIT_DAYS(beltRadius + 700f));

        // Magnetic fields (2)
        SectorEntityToken mag1 = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldParams(800f, 400f, outerField, 200f, 1000f,
                        new Color(75, 105, 165, 75), 1.0f,
                        new Color(55, 60, 140), new Color(65, 85, 155), new Color(175, 105, 165),
                        new Color(90, 130, 180), new Color(105, 150, 190), new Color(120, 175, 205), new Color(135, 200, 220)));
        mag1.setCircularOrbit(star, 95f, beltRadius - 1400f, ORBIT_DAYS(beltRadius - 1400f));

        SectorEntityToken mag2 = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldParams(700f, 350f, outerField, 250f, 950f,
                        new Color(120, 90, 180, 70), 1.0f,
                        new Color(50, 70, 140), new Color(70, 90, 165), new Color(140, 110, 200),
                        new Color(90, 120, 190), new Color(110, 150, 210), new Color(130, 180, 220), new Color(150, 210, 235)));
        mag2.setCircularOrbit(star, 250f, beltRadius + 1600f, ORBIT_DAYS(beltRadius + 1600f));

        // ============================================================
        // Debris fields (random-ish)
        // ============================================================
        Random rng = StarSystemGenerator.random;
        for (int i = 0; i < 6; i++) {
            DebrisFieldParams dp = new DebrisFieldParams(220f + rng.nextFloat() * 260f, -1f, 10000000f, 0f);
            dp.source = DebrisFieldSource.MIXED;
            dp.baseSalvageXP = 250;
            SectorEntityToken debris = Misc.addDebrisField(system, dp, rng);
            SalvageSpecialAssigner.assignSpecialForDebrisField(debris);
            debris.setSensorProfile(SENSOR_PROFILE);
            debris.setDiscoverable(true);

            float angle = rng.nextFloat() * 360f;
            float radius = AU(3.5f) + rng.nextFloat() * (beltRadius - AU(3.5f));
            debris.setCircularOrbit(star, angle, radius, ORBIT_DAYS(radius));
        }

        // ============================================================
        // 3 custom entities
        // NOTE: Two of them are now VANILLA SALVAGE ENTITIES so they use vanilla salvage specs.
        // ============================================================

        // (1) Listening Post -> vanilla salvage station (research station)
        SectorEntityToken customInner = system.addCustomEntity(
                "vir_custom_1",
                "Virellia Listening Post",
                Entities.STATION_RESEARCH, // <-- vanilla salvage ID
                "neutral"
        );
        customInner.setCircularOrbitPointingDown(star, 40f, AU(2.9f), ORBIT_DAYS(AU(2.9f)));
        customInner.setSensorProfile(SENSOR_PROFILE);
        makeVanillaSalvageable(customInner);

        // (2) Market station stays as your special station (not salvage-based)
        SectorEntityToken customMid = system.addCustomEntity(
                "vir_custom_2",
                "Coalition Research Station",
                "coalition_research_station",
                Factions.INDEPENDENT
        );

        // Keep station orbit (this is what we sync the asteroid field to)
        float coalOrbitAngle = 210f;
        float coalOrbitRadius = AU(9.2f);
        float coalOrbitDays = ORBIT_DAYS(coalOrbitRadius);

        customMid.setCircularOrbitPointingDown(star, coalOrbitAngle, coalOrbitRadius, coalOrbitDays);
        customMid.setSensorProfile(1500000f);
        customMid.setCustomDescriptionId(DimensionsCrossedIDS.COALITION_RESEARCH);
        customMid.setTransponderOn(true);
        customMid.getBaseSensorRangeToDetect(100f);
        customMid.setDiscoverable(true);

        // Comm relay, sensor relay, nav buoy - orbit the station
        SectorEntityToken coalComm = system.addCustomEntity(
                "vir_coal_comm",
                "Comm Relay",
                Entities.COMM_RELAY,
                Factions.INDEPENDENT
        );
        coalComm.setCircularOrbitPointingDown(customMid, 20f, 900f, 60f);

        SectorEntityToken coalSensor = system.addCustomEntity(
                "vir_coal_sensor",
                "Sensor Array",
                Entities.SENSOR_ARRAY,
                Factions.INDEPENDENT
        );
        coalSensor.setCircularOrbitPointingDown(customMid, 140f, 1100f, 75f);

        SectorEntityToken coalNav = system.addCustomEntity(
                "vir_coal_nav",
                "Nav Buoy",
                Entities.NAV_BUOY,
                Factions.INDEPENDENT
        );
        coalNav.setCircularOrbitPointingDown(customMid, 260f, 1300f, 90f);

        // Add a local asteroid field that stays PERFECTLY IN SYNC with customMid
        // and therefore customMid always sits inside of it.
        SectorEntityToken coalAstField = system.addTerrain(
                Terrain.ASTEROID_FIELD,
                new AsteroidFieldParams(
                        450f, // min radius
                        750f, // max radius
                        25,   // min asteroid count
                        40,   // max asteroid count
                        6f,   // min asteroid radius
                        18f,  // max asteroid radius
                        "Coalition Debris Ring"
                )
        );

        // IMPORTANT CHANGE:
        // Instead of orbiting customMid, the asteroid field now orbits the STAR with the exact same
        // angle/radius/period as customMid. This keeps them perfectly synced, co-located,
        // and guarantees customMid remains inside the field while orbiting.
        coalAstField.setCircularOrbit(star, coalOrbitAngle, coalOrbitRadius, coalOrbitDays);

        // (3) Drift Anchorage -> vanilla salvage mining station
        SectorEntityToken customAst = system.addCustomEntity(
                "vir_custom_3",
                "Virellia Drift Anchorage",
                Entities.STATION_MINING, // <-- vanilla salvage ID
                "neutral"
        );
        customAst.setCircularOrbitPointingDown(star, 120f, beltRadius + 900f, ORBIT_DAYS(beltRadius + 900f));
        customAst.setSensorProfile(SENSOR_PROFILE);
        makeVanillaSalvageable(customAst);

        // ============================================================
        // Jump points
        // ============================================================
        JumpPointAPI jpAst = Global.getFactory().createJumpPoint("vir_jp_ast", "Drift Anchorage Jump-point");
        jpAst.setCircularOrbit(customAst, 0f, 950f, 80f);
        jpAst.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jpAst);

        for (int i = 1; i <= 3; i++) {
            JumpPointAPI jp = Global.getFactory().createJumpPoint("vir_jp_" + i, "Virellia Jump-point " + i);
            float r = AU(5.0f) + rng.nextFloat() * (beltRadius - AU(5.0f));
            float a = rng.nextFloat() * 360f;
            jp.setCircularOrbit(star, a, r, ORBIT_DAYS(r));
            jp.setStandardWormholeToHyperspaceVisual();
            system.addEntity(jp);
        }

        float maxDist = 0f;
        SectorEntityToken center = system.getCenter();
        Vector2f centerLoc = center.getLocation();
        for (SectorEntityToken entity : system.getAllEntities()) {
            Vector2f loc = entity.getLocation();
            if (loc == null) continue;
            float dist = Misc.getDistance(centerLoc, loc);
            if (dist > maxDist) {
                maxDist = dist;
            }
        }

        // Padding so rings, labels, fringe jump points aren’t clipped
        float padding = 10300f;
        // Grid size = diameter + padding
        float gridSize = (maxDist * 2f) + padding;
        system.setMapGridWidthOverride(gridSize);
        system.setMapGridHeightOverride(gridSize);

        system.autogenerateHyperspaceJumpPoints(true, true);
    }
}