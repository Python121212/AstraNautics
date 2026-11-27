package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end simulation tests: a single-block ship dropped into a synthetic voxel
 * world, stepped through {@link ShipPhysics#step} exactly like the game does.
 */
class SolverSimulationTest {
    private static final double H = 1.0D / 60.0D;
    private static final double GRAVITY = 32.0D;
    private static final double FRICTION = 0.6D;
    private static final double RESTITUTION = 0.05D;
    private static final double LINEAR_DAMPING = 0.999D;
    private static final double ANGULAR_DAMPING = 0.995D;

    /** A synthetic world made of full blocks. */
    static final class FakeWorld implements BlockCollisionSource {
        private final Set<BlockPos> solid = new HashSet<>();

        FakeWorld floor(int y, int radius) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    solid.add(new BlockPos(x, y, z));
                }
            }
            return this;
        }

        FakeWorld wall(int x, int yFrom, int yTo, int zRadius) {
            for (int y = yFrom; y <= yTo; y++) {
                for (int z = -zRadius; z <= zRadius; z++) {
                    solid.add(new BlockPos(x, y, z));
                }
            }
            return this;
        }

        @Override
        public List<AABB> collisionBoxesAt(BlockPos pos) {
            return solid.contains(pos) ? List.of(new AABB(pos)) : List.of();
        }
    }

    /** A single 1x1x1 voxel whose centre of mass is its own centre. */
    static final class SingleCube implements VoxelBody {
        @Override
        public Set<BlockPos> surfaceVoxels() {
            return Set.of(BlockPos.ZERO);
        }

        @Override
        public Vector3d localCenterRelativeToCom(BlockPos local, Vector3d dest) {
            return dest.set(0.0D, 0.0D, 0.0D);
        }
    }

    private static RigidBody cube(double mass) {
        RigidBody body = new RigidBody();
        Matrix3d inertia = new Matrix3d();
        inertia.m00 = mass / 6.0D;
        inertia.m11 = mass / 6.0D;
        inertia.m22 = mass / 6.0D;
        body.setMassProperties(mass, inertia);
        return body;
    }

    private static void simulate(BlockCollisionSource world, RigidBody body, VoxelBody voxels, int steps) {
        for (int i = 0; i < steps; i++) {
            ShipPhysics.step(world, body, voxels, H, GRAVITY, FRICTION, RESTITUTION,
                    LINEAR_DAMPING, ANGULAR_DAMPING);
        }
    }

    @Test
    void cubeFallsAndSettlesOnTheFloor() {
        FakeWorld world = new FakeWorld().floor(0, 4);
        RigidBody body = cube(1.0D);
        body.position.set(0.5D, 6.0D, 0.5D);

        simulate(world, body, new SingleCube(), 600); // 10 s

        // Floor occupies y in [0,1], so the cube centre rests at y = 1.5.
        assertEquals(1.5D, body.position.y, 0.08D, "resting height: " + body.position);
        assertTrue(Math.abs(body.linearVelocity.y) < 0.2D, "still falling: " + body.linearVelocity);
        assertTrue(Math.abs(body.position.x - 0.5D) < 0.1D, "drifted on X: " + body.position);
        assertTrue(Math.abs(body.position.z - 0.5D) < 0.1D, "drifted on Z: " + body.position);
    }

    @Test
    void cubeStaysOnTheFloorAfterLongSimulation() {
        FakeWorld world = new FakeWorld().floor(0, 4);
        RigidBody body = cube(1.0D);
        body.position.set(0.5D, 1.5D, 0.5D);

        simulate(world, body, new SingleCube(), 1200); // 20 s

        assertEquals(1.5D, body.position.y, 0.08D, "sank or floated: " + body.position);
        assertTrue(Math.abs(body.linearVelocity.y) < 0.2D, "jittering: " + body.linearVelocity);
    }

    @Test
    void fastCubeDoesNotTunnelThroughTheFloor() {
        FakeWorld world = new FakeWorld().floor(0, 4);
        RigidBody body = cube(1.0D);
        body.position.set(0.5D, 8.0D, 0.5D);
        body.linearVelocity.set(0.0D, -45.0D, 0.0D);

        // 1/60 s substeps with adaptive substepping in game; here we simply use a
        // fine step so the test stays deterministic.
        for (int i = 0; i < 400; i++) {
            ShipPhysics.step(world, body, new SingleCube(), 1.0D / 240.0D, GRAVITY, FRICTION,
                    RESTITUTION, LINEAR_DAMPING, ANGULAR_DAMPING);
        }

        assertTrue(body.position.y > 1.0D, "tunnelled through the floor: " + body.position);
    }

    @Test
    void cubeSlidingIntoAWallIsStopped() {
        FakeWorld world = new FakeWorld().floor(0, 6).wall(4, 1, 3, 6);
        RigidBody body = cube(1.0D);
        body.position.set(0.5D, 1.5D, 0.5D);
        body.linearVelocity.set(6.0D, 0.0D, 0.0D);

        simulate(world, body, new SingleCube(), 900); // 15 s

        // Wall occupies x in [4,5]; the cube must not pass through it.
        assertTrue(body.position.x < 4.0D, "passed through the wall: " + body.position);
        assertTrue(Math.abs(body.linearVelocity.x) < 0.5D, "still sliding: " + body.linearVelocity);
    }

    @Test
    void cubeDroppedOnALedgeTipsOver() {
        // Floor only to the left of x = 0, so a cube dropped half over the edge falls.
        FakeWorld world = new FakeWorld();
        for (int x = -4; x < 0; x++) {
            for (int z = -4; z <= 4; z++) {
                world.solid.add(new BlockPos(x, 0, z));
            }
        }

        RigidBody body = cube(1.0D);
        body.position.set(0.1D, 3.0D, 0.5D);

        simulate(world, body, new SingleCube(), 400);

        assertTrue(body.position.y < 1.0D, "cube should have fallen off the ledge: " + body.position);
    }

    @Test
    void kinematicBodyIsNotSimulated() {
        FakeWorld world = new FakeWorld().floor(0, 4);
        RigidBody body = cube(1.0D);
        body.position.set(0.5D, 5.0D, 0.5D);
        body.setKinematic(true);

        simulate(world, body, new SingleCube(), 200);

        assertEquals(5.0D, body.position.y, 1.0E-9D);
    }
}
