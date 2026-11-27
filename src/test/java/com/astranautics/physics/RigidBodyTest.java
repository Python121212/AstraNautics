package com.astranautics.physics;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigidBodyTest {
    private static final double EPSILON = 1.0E-6D;

    private static RigidBody cubeOfMass(double mass) {
        RigidBody body = new RigidBody();
        // Solid unit cube: I = m/6 on the diagonal.
        Matrix3d inertia = new Matrix3d();
        inertia.m00 = mass / 6.0D;
        inertia.m11 = mass / 6.0D;
        inertia.m22 = mass / 6.0D;
        body.setMassProperties(mass, inertia);
        return body;
    }

    @Test
    void freeFallFollowsSemiImplicitEuler() {
        RigidBody body = cubeOfMass(2.0D);
        double gravity = 32.0D;
        double h = 0.005D; // 1/200 s
        int steps = 200;   // 1 second

        for (int i = 0; i < steps; i++) {
            body.applyForce(new Vector3d(0.0D, -gravity * body.getMass(), 0.0D));
            body.integrateVelocities(h, 1.0D, 1.0D);
            body.integratePosition(h);
        }

        // Velocity after 1 s == g (damping disabled).
        assertEquals(-gravity, body.linearVelocity.y, 1.0E-3D);
        // Semi-implicit Euler drops slightly further than 0.5 g t^2.
        double analytic = -0.5D * gravity;
        assertTrue(body.position.y < analytic, "expected semi-implicit Euler to overshoot: " + body.position.y);
        assertTrue(body.position.y > analytic - 0.5D, "position drift too large: " + body.position.y);
    }

    @Test
    void kinematicBodiesIgnoreForces() {
        RigidBody body = cubeOfMass(1.0D);
        body.setKinematic(true);
        body.applyForce(new Vector3d(100.0D, 100.0D, 100.0D));
        body.integrateVelocities(0.05D, 1.0D, 1.0D);
        body.integratePosition(0.05D);

        assertEquals(0.0D, body.linearVelocity.length(), EPSILON);
        assertEquals(0.0D, body.position.length(), EPSILON);
    }

    @Test
    void impulseAtOffsetProducesAngularVelocity() {
        RigidBody body = cubeOfMass(1.0D);
        Vector3d point = new Vector3d(0.5D, 0.0D, 0.0D);
        body.applyImpulseAt(new Vector3d(0.0D, 0.0D, 1.0D), point);

        assertEquals(1.0D, body.linearVelocity.z, 1.0E-6D);
        // torque = r x J = (0.5,0,0) x (0,0,1) = (0,-0.5,0) -> spin about -Y
        assertEquals(-0.5D, body.angularVelocity.y, 1.0E-6D);
        assertEquals(0.0D, body.angularVelocity.x, 1.0E-9D);
    }

    @Test
    void orientationStaysNormalised() {
        RigidBody body = cubeOfMass(1.0D);
        body.angularVelocity.set(1.3D, 2.7D, -0.8D);
        for (int i = 0; i < 500; i++) {
            body.integratePosition(0.005D);
            body.updateRotationMatrix();
        }
        assertEquals(1.0D, Math.sqrt(body.orientation.lengthSquared()), 1.0E-9D);

        // The rotation matrix must stay orthonormal (determinant 1).
        Matrix3d rotation = body.getRotationMatrix();
        Matrix3d product = new Matrix3d(rotation).mul(new Matrix3d(rotation).transpose());
        assertEquals(1.0D, product.determinant(), 1.0E-9D);
        assertEquals(1.0D, rotation.determinant(), 1.0E-9D);
    }

    @Test
    void worldLocalRoundTrip() {
        RigidBody body = cubeOfMass(1.0D);
        body.position.set(3.0D, 7.0D, -2.0D);
        body.orientation.rotationAxis(Math.toRadians(37.0D), 0.0D, 1.0D, 0.0D);
        body.updateRotationMatrix();

        Vector3d local = new Vector3d(0.5D, -1.25D, 2.0D);
        Vector3d world = body.toWorld(local, new Vector3d());
        Vector3d back = body.toLocal(world, new Vector3d());

        assertEquals(local.x, back.x, 1.0E-9D);
        assertEquals(local.y, back.y, 1.0E-9D);
        assertEquals(local.z, back.z, 1.0E-9D);
    }

    @Test
    void quaternionSlerpIsNormalised() {
        Quaterniond start = new Quaterniond();
        Quaterniond end = new Quaterniond().rotationAxis(Math.toRadians(90.0D), 0.0D, 0.0D, 1.0D);
        Quaterniond mid = new Quaterniond(start).slerp(end, 0.5D);
        assertEquals(1.0D, Math.sqrt(mid.lengthSquared()), 1.0E-9D);
        double angle = 2.0D * Math.acos(Math.min(1.0D, Math.abs(mid.w)));
        assertEquals(45.0D, Math.toDegrees(angle), 1.0E-6D);
    }
}
