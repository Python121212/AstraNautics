package com.astranautics.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two curves that decide whether an airship flies: air pressure against altitude, and
 * propeller thrust against forward speed. Both are taken from Create: Aeronautics.
 */
class AerodynamicsTest {
    private static final double FALLOFF = 0.004D;

    @Test
    void pressureIsOneAtSeaLevel() {
        assertEquals(1.0D, Aerodynamics.airPressure(63.0D, FALLOFF), 1.0E-9D);
    }

    @Test
    void pressureHalvesByTheBuildLimit() {
        // Aeronautics' figure: about 0.46 at y = 256, i.e. half the lift of sea level.
        assertEquals(0.462D, Aerodynamics.airPressure(256.0D, FALLOFF), 1.0E-3D);
        // Far up there is effectively no air left, which is what caps how high a ship can go.
        assertTrue(Aerodynamics.airPressure(1000.0D, FALLOFF) < 0.05D);
    }

    @Test
    void pressureOnlyFallsWithAltitude() {
        double previous = Double.MAX_VALUE;
        for (double y = -64.0D; y <= 320.0D; y += 8.0D) {
            double pressure = Aerodynamics.airPressure(y, FALLOFF);
            assertTrue(pressure <= previous + 1.0E-9D, "pressure rose at y=" + y);
            previous = pressure;
        }
    }

    @Test
    void noFalloffKeepsFullLift() {
        assertEquals(1.0D, Aerodynamics.airPressure(300.0D, 0.0D), 1.0E-9D);
    }

    @Test
    void thrustFallsToZeroAtTheAirflowSpeed() {
        assertEquals(1.0D, Aerodynamics.thrustFactor(0.0D, 24.0D), 1.0E-9D);
        assertEquals(0.5D, Aerodynamics.thrustFactor(12.0D, 24.0D), 1.0E-9D);
        assertEquals(0.0D, Aerodynamics.thrustFactor(24.0D, 24.0D), 1.0E-9D);
        assertEquals(0.0D, Aerodynamics.thrustFactor(40.0D, 24.0D), 1.0E-9D);
        // Moving backwards along the thrust axis can never give more than full thrust.
        assertEquals(1.0D, Aerodynamics.thrustFactor(-5.0D, 24.0D), 1.0E-9D);
    }
}
