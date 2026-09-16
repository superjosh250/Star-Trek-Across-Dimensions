package data.scripts.starsystems;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

import java.awt.Color;

public class Athan implements SectorGeneratorPlugin {

    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem("Athan");
        system.getLocation().set(-36600f, -4120f); // Adjust sector coordinates as needed

        // 1. Star Definition
        SectorEntityToken star = system.initStar("athan_star", "star_yellow", 600f, 400f);
        system.setLightColor(new Color(255, 245, 210));

        // 2. Terran World (Athan Prime) & Moon
        PlanetAPI athanPrime = system.addPlanet(
                "athan_prime",
                star,
                "Athan Prime",
                "terran",
                45f,
                170f,
                4200f,
                365f
        );

        PlanetAPI athanMoon = system.addPlanet(
                "athan_prime_moon",
                athanPrime,
                "Athan Major",
                "barren-desert",
                120f,
                50f,
                1200f,
                30f
        );

        // 3. Dummy Entity #1 (250f from Athan Prime)
        SectorEntityToken dummy1 = system.addCustomEntity(
                "starfleet_museum",
                "Spacedock One - Starfleet Museum",
                DimensionsCrossedIDS.STARFLEET_MUSEUM, // Replace with your custom entity ID from custom_entities.json
                DimensionsCrossedIDS.UFP
        );
        dummy1.setCircularOrbitPointingDown(athanPrime, 0f, 250f, 15f);

        // 4. Jump Point #1 (700f from Athan Prime)
        JumpPointAPI jpAthan = Global.getFactory().createJumpPoint("athan_prime_jp", "Athan Prime Jump-Point");
        jpAthan.setCircularOrbit(athanPrime, 210f, 700f, 35f);
        jpAthan.setRelatedPlanet(athanPrime);
        system.addEntity(jpAthan);

        // 5. Jump Point #2 (1500f from Star)
        JumpPointAPI jpInner = Global.getFactory().createJumpPoint("athan_inner_jp", "Inner System Jump-Point");
        jpInner.setCircularOrbit(star, 130f, 1500f, 90f);
        system.addEntity(jpInner);

        // 6. Jump Point #3 (Randomized Position)
        float randomAngle = (float) (Math.random() * 360.0);
        float randomDist = 5500f + (float) (Math.random() * 2500.0);
        JumpPointAPI jpRandom = Global.getFactory().createJumpPoint("athan_fringe_jp", "Fringe Jump-Point");
        jpRandom.setCircularOrbit(star, randomAngle, randomDist, 240f);
        system.addEntity(jpRandom);

        // 7. Dummy Entity #2 (Outer Orbit)
        SectorEntityToken dummy2 = system.addCustomEntity(
                "athan_orbital_complex",
                "Orbital Station 275",
                "orbital_office_complex", // Replace with your custom entity ID
                DimensionsCrossedIDS.UFP
        );
        dummy2.setCircularOrbitPointingDown(star, 300f, 3000f, 180f);

        // 8. Relays (2x Comm Relays, 2x Sensor Arrays)
        SectorEntityToken comm1 = system.addCustomEntity("athan_comm_1", "Athan Comm Relay Alpha", "comm_relay", "neutral");
        comm1.setCircularOrbitPointingDown(star, 60f, 2200f, 120f);

        SectorEntityToken comm2 = system.addCustomEntity("athan_comm_2", "Athan Comm Relay Beta", "comm_relay", "neutral");
        comm2.setCircularOrbitPointingDown(star, 240f, 6500f, 320f);

        SectorEntityToken sensor1 = system.addCustomEntity("athan_sensor_1", "Athan Sensor Array Alpha", "sensor_array", "neutral");
        sensor1.setCircularOrbitPointingDown(star, 150f, 2600f, 160f);

        SectorEntityToken sensor2 = system.addCustomEntity("athan_sensor_2", "Athan Sensor Array Beta", "sensor_array", "neutral");
        sensor2.setCircularOrbitPointingDown(star, 330f, 7000f, 380f);

        // Hyperspace generate
        system.autogenerateHyperspaceJumpPoints(true, true);
    }
}