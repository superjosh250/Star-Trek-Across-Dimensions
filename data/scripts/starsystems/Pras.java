package data.scripts.starsystems;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Planets;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Misc;
import data.scripts.starsystems.PrasMarket;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.Random;

public class Pras {

    private static final float AU = 10000f;

    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem("Pras");

        system.getLocation().set(-43000f, -7500f);
        system.getMapGridHeightOverride();
        system.getMapGridWidthOverride();// tweak freely

        // ----------------------------
        // Primary: Super-massive neutron star (
        // ----------------------------
        PlanetAPI neutron = system.initStar(
                "pras_neutron",
                StarTypes.NEUTRON_STAR,
                1200f,              // intentionally huge “super massive” visual radius
                system.getLocation().x,
                system.getLocation().y,
                900f                // large corona
        );

        system.setBackgroundTextureFilename("graphics/backgrounds/backgroundUFP_2.jpg");

        // ----------------------------
        // Companion stars orbiting the neutron star
        // ----------------------------
        float redDwarfOrbit = 2.0f * AU;
        float brownDwarfOrbit = 3.2f * AU;

        PlanetAPI redDwarf = system.addPlanet(
                "pras_reddwarf",
                neutron,
                "Pras-B",
                StarTypes.RED_DWARF,
                70f,
                450f,
                redDwarfOrbit,
                520f
        );

        PlanetAPI brownDwarf = system.addPlanet(
                "pras_browndwarf",
                neutron,
                "Pras-C",
                StarTypes.BROWN_DWARF,
                220f,
                520f,
                brownDwarfOrbit,
                880f
        );

        // Asteroid belt between neutron star and the orbiting stars
        system.addAsteroidBelt(neutron, 300, 1.4f * AU, 2500f, 250f, 420f, Terrain.ASTEROID_BELT, "Pras Belt");

        // ----------------------------
        // Planets around the neutron star (inner system)
        // ----------------------------
        PlanetAPI n_rm1 = system.addPlanet("pras_n_rm1", neutron, "Briones I", Planets.ROCKY_METALLIC, 20f, 110f, 0.35f * AU, 70f);
        PlanetAPI n_rm2 = system.addPlanet("pras_n_rm2", neutron, "Briones II",   Planets.ROCKY_METALLIC, 95f, 130f, 0.55f * AU, 105f);
        PlanetAPI n_rm3 = system.addPlanet("pras_n_rm3", neutron, "Briones III",   Planets.ROCKY_METALLIC, 165f, 150f, 0.80f * AU, 165f);

        PlanetAPI n_irr = system.addPlanet("pras_n_irr", neutron, "Ashfell", Planets.IRRADIATED, 250f, 140f, 1.05f * AU, 240f);

        // Gas giant with 4 random satellites
        PlanetAPI n_gas = system.addPlanet("pras_n_gas", neutron, "Brontes", Planets.GAS_GIANT, 310f, 300f, 1.65f * AU, 420f);
        addRandomMoons(system, n_gas, "pras_n_gas_m", 4, 320f, 80f);

        // Two ice giants, each with 3 random satellites
        PlanetAPI n_ice1 = system.addPlanet("pras_n_ice1", neutron, "Waither", Planets.ICE_GIANT, 10f, 260f, 2.15f * AU, 640f);
        addRandomMoons(system, n_ice1, "pras_n_ice1_m", 3, 300f, 70f);

        PlanetAPI n_ice2 = system.addPlanet("pras_n_ice2", neutron, "Nifl", Planets.ICE_GIANT, 140f, 260f, 2.55f * AU, 760f);
        addRandomMoons(system, n_ice2, "pras_n_ice2_m", 3, 320f, 70f);

        // Rocky-ice planet with an irradiated satellite
        PlanetAPI n_rockyIce = system.addPlanet("pras_n_ri", neutron, "Friones", Planets.ROCKY_ICE, 200f, 160f, 2.85f * AU, 920f);
        system.addPlanet("pras_n_ri_m1", n_rockyIce, "Irres", Planets.IRRADIATED, 30f, 55f, 450f, 30f);

        // ----------------------------
        // Red dwarf planet set
        // ----------------------------
        PlanetAPI r_irr = system.addPlanet("pras_r_irr", redDwarf, "Irressident", Planets.IRRADIATED, 40f, 120f, 0.25f * AU, 60f);
        PlanetAPI r_rm  = system.addPlanet("pras_r_rm",  redDwarf, "Bronclies", Planets.ROCKY_METALLIC, 160f, 110f, 0.45f * AU, 110f);
        PlanetAPI r_gas = system.addPlanet("pras_r_gas", redDwarf, "Jopiter", Planets.GAS_GIANT, 280f, 240f, 0.75f * AU, 220f);

        // ----------------------------
        // Brown dwarf planet set
        // ----------------------------
        PlanetAPI b_rm1 = system.addPlanet("pras_b_rm1", brownDwarf, "Corrand I", Planets.ROCKY_METALLIC, 20f, 115f, 0.30f * AU, 90f);
        PlanetAPI b_rm2 = system.addPlanet("pras_b_rm2", brownDwarf, "Corrand II", Planets.ROCKY_METALLIC, 110f, 120f, 0.52f * AU, 150f);

        PlanetAPI b_venus = system.addPlanet("pras_b_venus", brownDwarf, "Valhalen", Planets.BARREN_VENUSLIKE, 200f, 135f, 0.78f * AU, 260f);

        PlanetAPI b_irr = system.addPlanet("pras_b_irr", brownDwarf, "Edgenhiem", Planets.IRRADIATED, 310f, 140f, 1.05f * AU, 360f);

        // Rings on the brown-dwarf irradiated world (visual ring band).
        system.addRingBand(
                b_irr,
                "misc",
                "rings_asteroids0",
                256f,
                0,
                new Color(220, 210, 190, 180),
                256f,
                480f,
                60f
        );

        PlanetAPI b_rockyIce = system.addPlanet("pras_b_ri", brownDwarf, "Frostspine", Planets.ROCKY_ICE, 30f, 150f, 1.35f * AU, 520f);

        // Asteroid field/belt around the brown-dwarf rocky ice planet.
        system.addAsteroidBelt(b_rockyIce, 120, 650f, 220f, 40f, 70f, Terrain.ASTEROID_BELT, "Ice Shards");

        // Rogue planet (outer system, orbiting system center rather than a star)
        PlanetAPI rogue = system.addPlanet(
                "pras_rogue",
                system.getCenter(),
                "Nomad",
                Planets.ROCKY_ICE,
                75f,
                140f,
                4.6f * AU,
                1600f
        );

        // 3 comm relays + 4 sensor arrays (7 total), placed as orbiting entities.
        addRelay(system, "pras_comm_1", "Comm Relay", Entities.COMM_RELAY, 0.95f * AU, 90f, 200f);
        addRelay(system, "pras_comm_2", "Comm Relay", Entities.COMM_RELAY, 2.05f * AU, 210f, 480f);
        addRelay(system, "pras_comm_3", "Comm Relay", Entities.COMM_RELAY, 3.05f * AU, 330f, 720f);

        addRelay(system, "pras_sensor_1", "Sensor Array", Entities.SENSOR_ARRAY, 1.15f * AU, 30f, 260f);
        addRelay(system, "pras_sensor_2", "Sensor Array", Entities.SENSOR_ARRAY, 2.35f * AU, 150f, 520f);
        addRelay(system, "pras_sensor_3", "Sensor Array", Entities.SENSOR_ARRAY, 3.55f * AU, 270f, 900f);
        addRelay(system, "pras_sensor_4", "Sensor Array", Entities.SENSOR_ARRAY, 4.25f * AU, 10f, 1200f);

        // Custom entities
        addCustom(system, "pras_nav_1", "Nav Buoy", Entities.NAV_BUOY, 1.85f * AU, 60f, 410f);
        addCustom(system, "pras_nav_2", "Nav Buoy", Entities.NAV_BUOY, 3.85f * AU, 240f, 980f);
        addCustom(system, "pras_stable_1", "Stable Location", Entities.STABLE_LOCATION, 2.95f * AU, 110f, 860f);
        addCustom(system, "pras_station_1", "Derelict Research Station", Entities.STATION_RESEARCH, 1.55f * AU, 300f, 420f);
        addCustom(system, "pras_warning", "Warning Beacon", Entities.WARNING_BEACON, 4.05f * AU, 190f, 1200f);

        //Mr. Pras Mansion
        CustomCampaignEntityAPI prasStation2 = system.addCustomEntity(
                "pras_station_2",
                "Mr.Pras' Mansion",
                DimensionsCrossedIDS.STATION_PRAS,
                DimensionsCrossedIDS.UFP
        );

        // orbit the irradiated world (example: Ashfall)
        prasStation2.setCircularOrbitPointingDown(
                n_irr,      // <-- focus: the irradiated planet
                300f,       // angle
                450f,       // orbit radius around the planet (in pixels)
                60f         // orbit period (in days)
        );
        PrasMarket.addMarketToPrasStation(prasStation2);

        // ----------------------------
        // Random salvageable debris fields (simple placement as debris entities)
        // ----------------------------
        addDebris(system, "pras_debris_1", 1.45f * AU, 15f, 240f);
        addDebris(system, "pras_debris_2", 2.75f * AU, 190f, 700f);
        addDebris(system, "pras_debris_3", 3.95f * AU, 310f, 1050f);


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
        float padding = 8500f;

        // Grid size = diameter + padding
        float gridSize = (maxDist * 2f) + padding;

        system.setMapGridWidthOverride(gridSize);
        system.setMapGridHeightOverride(gridSize);


        system.autogenerateHyperspaceJumpPoints(true, true);
    }


    private static void addRelay(StarSystemAPI system, String id, String name, String entityType,
                                 float orbitRadius, float angle, float orbitDays) {
        CustomCampaignEntityAPI e = system.addCustomEntity(id, name, entityType, DimensionsCrossedIDS.UFP);
        e.setCircularOrbitPointingDown(system.getStar(), angle, orbitRadius, orbitDays);
    }

    private static void addCustom(StarSystemAPI system, String id, String name, String entityType,
                                  float orbitRadius, float angle, float orbitDays) {
        CustomCampaignEntityAPI e = system.addCustomEntity(id, name, entityType, Factions.NEUTRAL);
        e.setCircularOrbitPointingDown(system.getStar(), angle, orbitRadius, orbitDays);
    }

    private static void addDebris(StarSystemAPI system, String id, float orbitRadius, float angle, float orbitDays) {
        CustomCampaignEntityAPI debris = system.addCustomEntity(id, "Debris Field", Entities.DEBRIS_FIELD_SHARED, Factions.NEUTRAL);
        debris.setCircularOrbitPointingDown(system.getStar(), angle, orbitRadius, orbitDays);
    }

    private static void addRandomMoons(StarSystemAPI system, PlanetAPI parent, String idPrefix,
                                       int count, float startRadius, float step) {
        Random rng = new Random((system.getName() + "_" + parent.getId()).hashCode());

        // A small curated list of “reasonable” moon types.
        // You can expand this easily.
        String[] types = new String[]{
                Planets.BARREN,
                Planets.BARREN_VENUSLIKE,
                Planets.ROCKY_METALLIC,
                Planets.ROCKY_ICE,
                Planets.FROZEN,
                Planets.ROCKY_UNSTABLE,
                Planets.IRRADIATED
        };

        for (int i = 0; i < count; i++) {
            String type = types[rng.nextInt(types.length)];
            float radius = 40f + rng.nextFloat() * 25f;
            float orbitRadius = startRadius + (i * step);
            float orbitDays = 25f + i * 15f + rng.nextFloat() * 10f;
            float angle = rng.nextFloat() * 360f;

            system.addPlanet(
                    idPrefix + (i + 1),
                    parent,
                    parent.getName() + "-" + (i + 1),
                    type,
                    angle,
                    radius,
                    orbitRadius,
                    orbitDays
            );
        }
    }
}