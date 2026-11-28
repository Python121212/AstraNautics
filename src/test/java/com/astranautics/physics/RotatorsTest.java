package com.astranautics.physics;

import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dial maths: what "turn to 90 degrees" means, and that the short way round is the way
 * taken.
 */
class RotatorsTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void anUnrotatedHullReadsZeroOnBothDials() {
        Quaterniond orientation = new Quaterniond();
        assertEquals(0.0D, Rotators.yawDegrees(orientation), EPSILON);
        assertEquals(0.0D, Rotators.pitchDegrees(orientation), EPSILON);
    }

    @Test
    void yawFollowsRotationAboutTheWorldVertical() {
        Quaterniond orientation = new Quaterniond();
        Rotators.rotateAboutWorldAxis(orientation, Direction.Axis.Y, 90.0D);
        assertEquals(90.0D, Rotators.yawDegrees(orientation), EPSILON);
        assertEquals(0.0D, Rotators.pitchDegrees(orientation), EPSILON);

        Rotators.rotateAboutWorldAxis(orientation, Direction.Axis.Y, 45.0D);
        assertEquals(135.0D, Rotators.yawDegrees(orientation), EPSILON);
    }

    @Test
    void pitchFollowsRotationAboutTheWorldEastWestAxis() {
        Quaterniond orientation = new Quaterniond();
        Rotators.rotateAboutWorldAxis(orientation, Direction.Axis.X, 90.0D);
        assertEquals(90.0D, Rotators.pitchDegrees(orientation), EPSILON);
        assertEquals(0.0D, Rotators.yawDegrees(orientation), EPSILON);

        Rotators.rotateAboutWorldAxis(orientation, Direction.Axis.X, 90.0D);
        assertEquals(180.0D, Rotators.pitchDegrees(orientation), EPSILON);
    }

    @Test
    void theShortWayRoundIsChosen() {
        assertEquals(20.0D, Rotators.shortestDelta(350.0D, 10.0D), EPSILON);
        assertEquals(-20.0D, Rotators.shortestDelta(10.0D, 350.0D), EPSILON);
        assertEquals(-170.0D, Rotators.shortestDelta(0.0D, 190.0D), EPSILON);
        assertEquals(0.0D, Rotators.shortestDelta(90.0D, 90.0D), EPSILON);
    }

    @Test
    void steppingArrivesExactlyOnTheTarget() {
        Quaterniond orientation = new Quaterniond();
        double target = 270.0D;
        int guard = 0;
        double remaining = 1.0D;
        while (Math.abs(remaining) > EPSILON && guard++ < 1000) {
            remaining = Rotators.stepTowards(orientation, Rotators.Mode.YAW, target, 4.5D);
        }
        assertEquals(0.0D, remaining, EPSILON);
        assertEquals(target, Rotators.yawDegrees(orientation), 1.0E-4D);
        // 270 degrees at 4.5 degrees per step: 60 steps, not the 20 a wrong-way turn would need.
        assertTrue(guard <= 66, "took " + guard + " steps");
    }

    @Test
    void steppingNeverOvershoots() {
        Quaterniond orientation = new Quaterniond();
        Rotators.stepTowards(orientation, Rotators.Mode.YAW, 350.0D, 100.0D);
        assertEquals(260.0D, Rotators.yawDegrees(orientation), 1.0E-4D);
    }

    @Test
    void aFullTurnComesBackToTheStart() {
        Quaterniond orientation = new Quaterniond();
        for (int i = 0; i < 8; i++) {
            Rotators.stepTowards(orientation, Rotators.Mode.PITCH, 0.0D + 45.0D * (i + 1), 45.0D);
        }
        // A full 360 about X is the identity again.
        assertEquals(0.0D, Rotators.pitchDegrees(orientation), 1.0E-4D);
        assertEquals(0.0D, Rotators.yawDegrees(orientation), 1.0E-4D);
    }
}
