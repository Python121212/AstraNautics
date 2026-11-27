package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: hitting a structure must push it - and spinning it, so an off-centre punch
 * makes it roll instead of sliding.
 */
class PunchImpulseTest {
    private static final double H = 1.0D / 60.0D;
    private static final PhysicsParams PARAMS = PhysicsParams.defaults();

    private static RigidBody cube(double mass) {
        RigidBody body = new RigidBody();
        Matrix3d inertia = new Matrix3d();
        inertia.m00 = mass / 6.0D;
        inertia.m11 = mass / 6.0D;
        inertia.m22 = mass / 6.0D;
        body.setMassProperties(mass, inertia);
        return body;
    }

    /** A single 1x1x1 voxel whose centre of mass is its own centre. */
    private static final class SingleCube implements VoxelBody {
        @Override
        public java.util.Set<BlockPos> surfaceVoxels() {
            return java.util.Set.of(BlockPos.ZERO);
        }

        @Override
        public Vector3d localCenterRelativeToCom(BlockPos local, Vector3d dest) {
            return dest.set(0.0D, 0.0D, 0.0D);
        }
    }

    @Test
    void offCentreImpulseProducesBothLinearAndAngularMotion() {
        RigidBody body = cube(2.0D);
        body.position.set(0.5D, 10.5D, 0.5D);
        body.updateRotationMatrix();

        // Punch the +X face, above the centre of mass: the lever arm creates torque
        // around Z, which is what makes a structure roll forwards.
        Vector3d contact = new Vector3d(1.5D, 11.0D, 0.5D);
        Vector3d impulse = new Vector3d(6.0D, 0.0D, 0.0D);

        body.applyImpulseAt(impulse, contact);

        assertTrue(body.linearVelocity.x > 1.0D, "no linear motion: " + body.linearVelocity);
        assertNotEquals(0.0D, body.angularVelocity.z, 1.0E-6D, "no rotation: " + body.angularVelocity);
        // Rolling forward around Z means a negative angular velocity for a +X push.
        assertTrue(body.angularVelocity.z < 0.0D, "rotation has the wrong sign: " + body.angularVelocity);
    }

    @Test
    void centrePunchDoesNotSpinTheBody() {
        RigidBody body = cube(2.0D);
        body.position.set(0.5D, 10.5D, 0.5D);
        body.updateRotationMatrix();

        body.applyImpulseAt(new Vector3d(6.0D, 0.0D, 0.0D), new Vector3d(0.5D, 10.5D, 0.5D));

        assertTrue(body.linearVelocity.x > 1.0D, "no linear motion: " + body.linearVelocity);
        assertTrue(body.angularVelocity.length() < 1.0E-9D, "centre hit spun: " + body.angularVelocity);
    }

    @Test
    void punchedCubeOnTheFloorKeepsMovingAndRotating() {
        SolverSimulationTest.FakeWorld world = new SolverSimulationTest.FakeWorld().floor(0, 6);
        RigidBody body = cube(2.0D);
        body.position.set(0.5D, 1.5D, 0.5D);
        body.updateRotationMatrix();

        ContactCache cache = new ContactCache();
        // Settle it on the floor first.
        for (int i = 0; i < 60; i++) {
            ShipPhysics.step(world, body, new SingleCube(), H, PARAMS, cache);
        }
        double startX = body.position.x;

        body.applyImpulseAt(new Vector3d(7.0D, 0.0D, 0.0D), new Vector3d(1.0D, 2.0D, 0.5D));
        boolean rotated = false;
        for (int i = 0; i < 240; i++) { // 4 s
            ShipPhysics.step(world, body, new SingleCube(), H, PARAMS, cache);
            if (body.angularVelocity.length() > 0.05D) {
                rotated = true;
            }
        }

        assertTrue(body.position.x - startX > 0.4D, "punch did not move it: " + body.position);
        assertTrue(rotated, "punch never rotated the body");
        assertTrue(body.position.y > 0.9D, "sank into the floor: " + body.position);
    }
}
