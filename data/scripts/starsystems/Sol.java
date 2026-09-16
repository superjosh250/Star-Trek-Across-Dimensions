package data.scripts.starsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;


public class Sol {

        // =========================================================
        // Real-world AU values
        // =========================================================
        private static final double AU_MERCURY = 0.387;
        private static final double AU_VENUS   = 0.723;
        private static final double AU_EARTH   = 1.000;
        private static final double AU_MARS    = 1.524;
        private static final double AU_BELT    = 2.80;
        private static final double AU_JUPITER = 5.203;
        private static final double AU_SATURN  = 9.537;
        private static final double AU_URANUS  = 19.191;
        private static final double AU_NEPTUNE = 30.069;

        // =========================================================
        // Star + safety
        // =========================================================
        private static final float STAR_RADIUS = 800f;
        private static final float CORONA_EXTRA = 1000f;
        private static final float MIN_ORBIT = STAR_RADIUS + CORONA_EXTRA + 700f;

        // =========================================================
        // FINAL tightened spacing (Neptune guaranteed on-grid)
        // =========================================================
        private static final float ORBIT_SCALE = 3400f;
        private static final float ORBIT_EXP   = 0.55f;

        // =========================================================
        // Orbital period scaling (compressed to match distance)
        // =========================================================
        private static final float EARTH_PERIOD = 365f;
        private static final float PERIOD_EXP   = 0.78f;

        // Planet sizes
        private static final float R_MERCURY = 55f;
        private static final float R_VENUS   = 85f;
        private static final float R_EARTH   = 90f;
        private static final float R_MARS    = 70f;
        private static final float R_JUPITER = 210f;
        private static final float R_SATURN  = 190f;
        private static final float R_URANUS  = 140f;
        private static final float R_NEPTUNE = 140f;

        public void generate(SectorAPI sector) {

                StarSystemAPI system = sector.createStarSystem("Sol");
                system.getLocation().set(-32750f, 0f);
                system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
                system.setLightColor(new Color(255, 245, 225));

                PlanetAPI sun = system.initStar("sol", "star_yellow", STAR_RADIUS, CORONA_EXTRA, 10f, 0.6f, 3f);
                sun.setName("Sol");
                system.generateAnchorIfNeeded();

                float rMercury = orbitRadiusFromAU(AU_MERCURY);
                float rVenus   = orbitRadiusFromAU(AU_VENUS);
                float rEarth   = orbitRadiusFromAU(AU_EARTH);
                float rMars    = orbitRadiusFromAU(AU_MARS);
                float rBelt    = orbitRadiusFromAU(AU_BELT);
                float rJupiter = orbitRadiusFromAU(AU_JUPITER);
                float rSaturn  = orbitRadiusFromAU(AU_SATURN);
                float rUranus  = orbitRadiusFromAU(AU_URANUS);
                float rNeptune = orbitRadiusFromAU(AU_NEPTUNE);

                PlanetAPI mercury = system.addPlanet("mercury", sun, "Mercury", "barren", 20f, R_MERCURY, rMercury,
                        orbitDaysFromAU(AU_MERCURY));
                applyTilt(mercury, 2f);

                PlanetAPI venus = system.addPlanet("venus", sun, "Venus", "toxic", 80f, R_VENUS, rVenus,
                        orbitDaysFromAU(AU_VENUS));
                applyTilt(venus, 177.36f);

                PlanetAPI earth = system.addPlanet("earth", sun, "Earth", "earth", 140f, R_EARTH, rEarth,
                        orbitDaysFromAU(AU_EARTH));
                applyTilt(earth, 23.44f);

                // Add jump point near Earth
                addCustomJumpPoint(system, earth, "jp_earth_custom", "Earth Jump Point");

                // ✅ Earth market moved to SolMarket
                data.scripts.starsystems.SolMarket.addEarthMarket(sector, earth);

                PlanetAPI moon = system.addPlanet("luna", earth, "Luna", "moon_terraformed", 35f, 25f, 320f, 30f);
                applyTilt(moon, 6.68f);
                data.scripts.starsystems.SolMarket.addLunaMarket(sector, moon);

                // Earth relay
                SectorEntityToken earthRelay = system.addCustomEntity("relay_earth", "Earth Comm Relay", "comm_relay", "ufp");
                earthRelay.setOrbit(Global.getFactory().createCircularOrbit(earth, 60f, 600f, 45f));

                // ========================
                // MARS
                // ========================
                PlanetAPI mars = system.addPlanet("mars", sun, "Mars", "mars_terraforming", 210f, R_MARS, rMars,
                        orbitDaysFromAU(AU_MARS));
                applyTilt(mars, 25.19f);
                data.scripts.starsystems.SolMarket.addMarsMarket(sector, mars);

                // Stations around Mars
                SectorEntityToken utopiaPlanitiaStation = system.addCustomEntity(
                        "utopia_planitia_station",
                        "Utopia Planitia",
                        "utopia_planitiaStation",
                        "ufp");
                utopiaPlanitiaStation.setOrbit(Global.getFactory().createCircularOrbit(mars, 45f, 350f, 30f));

                SectorEntityToken mcKinleyStation = system.addCustomEntity(
                        "mcKinleyStation",
                        "McKinley Station",
                        "mcKinley_station",
                        "ufp");
                mcKinleyStation.setOrbit(Global.getFactory().createCircularOrbit(utopiaPlanitiaStation, 0f, 125, 50f));

                SectorEntityToken advDrydock1 = system.addCustomEntity(
                        "advDrydock1",
                        "Type 4 Drydock",
                        "advanced_drydock",
                        "ufp");
                advDrydock1.setOrbit(Global.getFactory().createCircularOrbit(utopiaPlanitiaStation, 90f, 125, 50f));

                SectorEntityToken advDrydock2 = system.addCustomEntity(
                        "advDrydock2",
                        "Type 4 Drydock 2",
                        "advanced_drydock",
                        "ufp");
                advDrydock2.setOrbit(Global.getFactory().createCircularOrbit(utopiaPlanitiaStation, 270f, 125, 50f));

                SectorEntityToken drydock_station = system.addCustomEntity(
                        "drydock_station",
                        "Type 1 Drydock",
                        "drydock",
                        "ufp");
                drydock_station.setOrbit(Global.getFactory().createCircularOrbit(utopiaPlanitiaStation, 180f, 125, 50f));

                // Mars relay
                SectorEntityToken marsRelay = system.addCustomEntity("relay_mars", "Mars Comm Relay", "comm_relay", "ufp");
                marsRelay.setOrbit(Global.getFactory().createCircularOrbit(mars, 120f, 520f, 50f));

                // =========================================================
                // JUPITER
                // =========================================================
                PlanetAPI jupiter = system.addPlanet("jupiter", sun, "Jupiter", "gas_giant", 300f, R_JUPITER, rJupiter,
                        orbitDaysFromAU(AU_JUPITER));
                applyTilt(jupiter, 3.13f);

                // Add jump point near Jupiter
                addCustomJumpPoint(system, jupiter, "jp_jupiter_custom", "Jupiter Jump Point");

                SectorEntityToken jupiterRelay = system.addCustomEntity("relay_jupiter", "Jupiter Comm Relay", "comm_relay", "ufp");
                jupiterRelay.setOrbit(Global.getFactory().createCircularOrbit(jupiter, 200f, 1200f, 80f));

                PlanetAPI io       = system.addPlanet("io", jupiter, "Io", "lava_minor", 30f, 45f, 360f, 1.769f);
                PlanetAPI europa   = system.addPlanet("europa", jupiter, "Europa", "frozen", 90f, 42f, 520f, 3.551f);
                PlanetAPI ganymede = system.addPlanet("ganymede", jupiter, "Ganymede", "frozen", 150f, 55f, 760f, 7.155f);
                PlanetAPI callisto = system.addPlanet("callisto", jupiter, "Callisto", "barren", 210f, 52f, 1100f, 16.689f);

                SectorEntityToken jupiterStation = system.addCustomEntity(
                        "jupiterStation",
                        "Jupiter Station",
                        "jupiterStation",
                        "ufp");
                jupiterStation.setOrbit(Global.getFactory().createCircularOrbit(jupiter, 150f, 500f, 90f));

                SectorEntityToken orbitalComplex = system.addCustomEntity(
                        "orbitalComplex",
                        "Jupiter Orbital Complex",
                        "orbital_office_complex",
                        "ufp");
                orbitalComplex.setOrbit(Global.getFactory().createCircularOrbit(jupiter, 90f, 500f, 180f));

                // =========================================================
                // SATURN
                // =========================================================
                PlanetAPI saturn = system.addPlanet("saturn", sun, "Saturn", "saturn", 20f, R_SATURN, rSaturn,
                        orbitDaysFromAU(AU_SATURN));
                applyTilt(saturn, 26.73f);

                float saturnRingMid = saturn.getRadius() + 520f;
                system.addRingBand(saturn, "misc", "rings_dust0", 256f, 0, new Color(220, 220, 220, 200),
                        420f, saturnRingMid, 40f, Terrain.RING, "Saturn's Rings");
                system.addRingBand(saturn, "misc", "rings_ice0", 256f, 1, new Color(255, 255, 255, 160),
                        260f, saturn.getRadius() + 760f, 55f);

                PlanetAPI enceladus = system.addPlanet("enceladus", saturn, "Enceladus", "frozen", 20f, 25f, 320f, 1.370f);
                PlanetAPI rhea      = system.addPlanet("rhea", saturn, "Rhea", "frozen", 80f, 35f, 520f, 4.518f);
                PlanetAPI titan     = system.addPlanet("titan", saturn, "Titan", "frozen", 140f, 60f, 850f, 15.945f);
                PlanetAPI iapetus   = system.addPlanet("iapetus", saturn, "Iapetus", "frozen", 220f, 30f, 1500f, 79.322f);

                // =========================================================
                // URANUS + NEPTUNE
                // =========================================================
                PlanetAPI uranus = system.addPlanet("uranus", sun, "Uranus", "uranus", 70f, R_URANUS, rUranus,
                        orbitDaysFromAU(AU_URANUS));
                applyTilt(uranus, 97.77f);

                PlanetAPI neptune = system.addPlanet("neptune", sun, "Neptune", "neptune", 120f, R_NEPTUNE, rNeptune,
                        orbitDaysFromAU(AU_NEPTUNE));
                applyTilt(neptune, 28.32f);

                // Add jump point near Neptune
                addCustomJumpPoint(system, neptune, "jp_neptune_custom", "Neptune Jump Point");

                // Asteroid belt
                system.addAsteroidBelt(sun, 1200, rBelt, 1600f, 180f, 420f, Terrain.ASTEROID_BELT, "Main Belt");

                // Fringe jump point
                JumpPointAPI fringe = Global.getFactory().createJumpPoint("sol_jump_fringe", "Fringe Jump-point");
                fringe.setOrbit(Global.getFactory().createCircularOrbit(sun, 120f, rNeptune * 1.08f,
                        orbitDaysFromAU(AU_NEPTUNE) + 180f));
                fringe.setStandardWormholeToHyperspaceVisual();
                fringe.getMemoryWithoutUpdate().set(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY, true);
                system.addEntity(fringe);

                system.autogenerateHyperspaceJumpPoints(true, true);
        }

        private float orbitRadiusFromAU(double au) {
                return MIN_ORBIT + ORBIT_SCALE * (float) Math.pow(au, ORBIT_EXP);
        }

        private float orbitDaysFromAU(double au) {
                return EARTH_PERIOD * (float) Math.pow(au, PERIOD_EXP);
        }

        private void applyTilt(PlanetAPI planet, float tiltDeg) {
                planet.getSpec().setTilt(tiltDeg);
                planet.applySpecChanges();
        }

        // Helper method added to class
        private void addCustomJumpPoint(StarSystemAPI system, PlanetAPI target, String id, String name) {
                JumpPointAPI jp = Global.getFactory().createJumpPoint(id, name);
                jp.setOrbit(Global.getFactory().createCircularOrbit(target, 0f, 600f, 100f));
                jp.setStandardWormholeToHyperspaceVisual();
                system.addEntity(jp);
        }
}