package com.astranautics.physics;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link ShipPhysics#solveContacts} with synthetic contacts to make sure a
 * box resting on the ground neither sinks nor bounces.
 */
class ContactSolverTest {
    private static final double H = 0.025D; // one 1/40 s substep

    private static RigidBody cube(double mass) {
        RigidBody body = new RigidBody();
        Matrix3d inertia = new Matrix3d();
        inertia.m00 = mass / 6.0D;
        inertia.m11 = mass / 6.0D;
        inertia.m22 = mass / 6.0D;
        body.setMassProperties(mass, inertia);
        return body;
    }

    /** Four contacts at the bottom corners of a unit cube resting on y = 0. */
    private static List<Contact> groundContacts(RigidBody body) {
        List<Contact> contacts = new ArrayList<>();
        double[][] corners = {{-0.5, -0.5}, {0.5, -0.5}, {-0.5, 0.5}, {0.5, 0.5}};
        for (double[] corner : corners) {
            Vector3d point = new Vector3d(body.position.x + corner[0], 0.0D, body.position.z + corner[1]);
            contacts.add(new Contact().set(point, new Vector3d(0, 1, 0), 0.0D));
        }
        return contacts;
    }

    @Test
    void restingBoxDoesNotSinkOrFly() {
        RigidBody body = cube(1.0D);
        body.position.set(0.0D, 0.5D, 0.0D);

        double gravity = 32.0D;
        for (int step = 0; step < 400; step++) { // 10 simulated seconds
            body.applyForce(new Vector3d(0, -gravity * body.getMass(), 0));
            body.integrateVelocities(H, 0.999D, 0.995D);
            ShipPhysics.solveContacts(body, groundContacts(body), H, 0.6D, 0.05D);
            body.integratePosition(H);
        }

        // The contact solver must hold the box at the ground plane.
        assertTrue(Math.abs(body.position.y - 0.5D) < 0.1D,
                "box drifted vertically to y=" + body.position.y);
        assertTrue(Math.abs(body.linearVelocity.y) < 0.5D,
                "residual vertical velocity " + body.linearVelocity.y);
        assertTrue(Math.abs(body.position.x) < 0.01D && Math.abs(body.position.z) < 0.01D,
                "friction should not move a resting box: " + body.position);
    }

    @Test
    void fallingBoxIsStoppedByTheSolver() {
        RigidBody body = cube(1.0D);
        body.position.set(0.0D, 0.5D, 0.0D);
        body.linearVelocity.set(0.0D, -8.0D, 0.0D);

        for (int step = 0; step < 20; step++) {
            body.applyForce(new Vector3d(0, -32.0D * body.getMass(), 0));
            body.integrateVelocities(H, 0.999D, 0.995D);
            ShipPhysics.solveContacts(body, groundContacts(body), H, 0.6D, 0.05D);
            body.integratePosition(H);
        }

        assertTrue(body.linearVelocity.y > -1.0D,
                "impact was not absorbed, vy=" + body.linearVelocity.y);
    }

    @Test
    void offCentreContactCreatesSpin() {
        RigidBody body = cube(1.0D);
        body.position.set(0.0D, 0.5D, 0.0D);

        List<Contact> contacts = new ArrayList<>();
        contacts.add(new Contact().set(new Vector3d(0.5D, 0.0D, 0.0D), new Vector3d(0, 1, 0), 0.0D));

        for (int step = 0; step < 10; step++) {
            body.applyForce(new Vector3d(0, -32.0D * body.getMass(), 0));
            body.integrateVelocities(H, 0.999D, 0.995D);
            ShipPhysics.solveContacts(body, contacts, H, 0.6D, 0.05D);
            body.integratePosition(H);
            contacts.get(0).point.set(body.position.x + 0.5D, body.position.y - 0.5D, body.position.z);
        }

        assertTrue(body.angularVelocity.length() > 1.0E-3D,
                "an off-centre contact should induce rotation, got " + body.angularVelocity);
    }
}
