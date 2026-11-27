package com.astranautics.physics;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ship-vs-ship contacts: the two-body branch of the solver. */
class TwoBodySolverTest {
    private static final PhysicsParams PARAMS = PhysicsParams.defaults();
    private static final double H = 1.0D / 60.0D;

    private static RigidBody cube(double mass) {
        RigidBody body = new RigidBody();
        Matrix3d inertia = new Matrix3d();
        inertia.m00 = mass / 6.0D;
        inertia.m11 = mass / 6.0D;
        inertia.m22 = mass / 6.0D;
        body.setMassProperties(mass, inertia);
        return body;
    }

    @Test
    void restingOnAKinematicBodyCancelsApproachVelocity() {
        RigidBody upper = cube(1.0D);
        upper.position.set(0.0D, 1.0D, 0.0D);
        upper.linearVelocity.set(0.0D, -4.0D, 0.0D);

        RigidBody lower = cube(1.0D);
        lower.position.set(0.0D, 0.0D, 0.0D);
        lower.setKinematic(true);

        Contact contact = new Contact().set(
                new Vector3d(0.0D, 0.5D, 0.0D), new Vector3d(0.0D, 1.0D, 0.0D), 0.02D);

        ShipPhysics.solveContacts(upper, lower, List.of(contact), H, PARAMS);

        assertTrue(upper.linearVelocity.y > -0.2D, "approach not cancelled: " + upper.linearVelocity);
        assertTrue(lower.linearVelocity.length() < 1.0E-9D, "kinematic body moved");
        assertEquals(0.0D, lower.position.length(), 1.0E-9D);
    }

    @Test
    void twoDynamicBodiesExchangeMomentum() {
        RigidBody upper = cube(1.0D);
        upper.position.set(0.0D, 1.0D, 0.0D);
        upper.linearVelocity.set(0.0D, -3.0D, 0.0D);

        RigidBody lower = cube(1.0D);
        lower.position.set(0.0D, 0.0D, 0.0D);
        lower.linearVelocity.set(0.0D, 1.0D, 0.0D);

        Contact contact = new Contact().set(
                new Vector3d(0.0D, 0.5D, 0.0D), new Vector3d(0.0D, 1.0D, 0.0D), 0.01D);

        ShipPhysics.solveContacts(upper, lower, List.of(contact), H, PARAMS);

        double totalMomentum = upper.linearVelocity.y + lower.linearVelocity.y;
        assertEquals(-2.0D, totalMomentum, 0.05D, "momentum not conserved");
        assertTrue(upper.linearVelocity.y > -3.0D, "upper body did not slow down");
        assertTrue(lower.linearVelocity.y < 1.0D, "lower body was not pushed down");
    }

    @Test
    void heavierBodyMovesLess() {
        RigidBody light = cube(1.0D);
        light.position.set(0.0D, 1.0D, 0.0D);
        light.linearVelocity.set(0.0D, -2.0D, 0.0D);

        RigidBody heavy = cube(20.0D);
        heavy.position.set(0.0D, 0.0D, 0.0D);
        heavy.linearVelocity.set(0.0D, 0.0D, 0.0D);

        Contact contact = new Contact().set(
                new Vector3d(0.0D, 0.5D, 0.0D), new Vector3d(0.0D, 1.0D, 0.0D), 0.01D);

        PhysicsParams converged = new PhysicsParams(32.0D, 0.6D, 0.05D, 0.999D, 0.995D, 16);
        ShipPhysics.solveContacts(light, heavy, List.of(contact), H, converged);

        // In a perfectly inelastic contact both bodies end at the same velocity, so the
        // mass ratio shows up in the velocity *change* of each body.
        double deltaLight = Math.abs(light.linearVelocity.y - (-2.0D));
        double deltaHeavy = Math.abs(heavy.linearVelocity.y);
        assertTrue(deltaLight > deltaHeavy * 5.0D,
                "mass ratio not respected: light dV=" + deltaLight + " heavy dV=" + deltaHeavy);
    }

    @Test
    void warmStartingKeepsARestingStackStable() {
        RigidBody lower = cube(2.0D);
        lower.position.set(0.0D, 0.5D, 0.0D);
        lower.setKinematic(true);
        RigidBody upper = cube(1.0D);
        upper.position.set(0.0D, 1.5D, 0.0D);

        ContactCache cache = new ContactCache();
        Vector3d point = new Vector3d(0.0D, 1.0D, 0.0D);
        Vector3d normal = new Vector3d(0.0D, 1.0D, 0.0D);

        for (int step = 0; step < 300; step++) {
            upper.applyForce(new Vector3d(0.0D, -32.0D * upper.getMass(), 0.0D));
            upper.integrateVelocities(H, 0.999D, 0.995D);

            Contact contact = new Contact().set(point, normal, 0.01D);
            ShipPhysics.solveContacts(upper, lower, List.of(contact), H, PARAMS, cache);
            cache.swap();
            upper.integratePosition(H);
            point.set(upper.position.x, lower.position.y + 0.5D, upper.position.z);
        }

        assertTrue(Math.abs(upper.linearVelocity.y) < 1.0D,
                "stack keeps falling: " + upper.linearVelocity);
        assertTrue(Math.abs(upper.position.y - 1.5D) < 0.15D,
                "stack sank or floated: " + upper.position);
    }
}
