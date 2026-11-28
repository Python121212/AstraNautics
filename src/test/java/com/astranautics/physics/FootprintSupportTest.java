package com.astranautics.physics;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The support surface under an entity: two adjacent deck blocks must leave no gap at the
 * seam, because that gap is where players used to fall through.
 */
class FootprintSupportTest {
    private static final double EPSILON = 1.0E-6D;
    private static final double HALF = 0.5D;

    private static Matrix3d identity() {
        Matrix3d matrix = new Matrix3d();
        matrix.identity();
        return matrix;
    }

    @Test
    void flatDeckTopIsFoundUnderThePoint() {
        Matrix3d rotation = identity();
        Vector3d center = new Vector3d(0.5D, 0.5D, 0.5D);

        assertEquals(1.0D, FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.5D, 0.5D), EPSILON);
        assertEquals(1.0D, FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.02D, 0.98D), EPSILON);
    }

    @Test
    void pointOutsideTheVoxelFindsNoSurface() {
        Matrix3d rotation = identity();
        Vector3d center = new Vector3d(0.5D, 0.5D, 0.5D);

        assertTrue(Double.isNaN(FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 1.05D, 0.5D)));
        assertTrue(Double.isNaN(FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.5D, -0.2D)));
    }

    @Test
    void seamBetweenTwoDeckBlocksHasNoHole() {
        Matrix3d rotation = identity();
        Vector3d left = new Vector3d(0.5D, 0.5D, 0.5D);
        Vector3d right = new Vector3d(1.5D, 0.5D, 0.5D);

        // Just either side of x = 1.0: exactly one of the two voxels must answer, and both
        // answers must be the same height, otherwise the surface has a step or a hole.
        double before = FootprintSupport.topHeightAt(rotation, left, HALF, HALF, HALF, 0.999D, 0.5D);
        double after = FootprintSupport.topHeightAt(rotation, right, HALF, HALF, HALF, 1.001D, 0.5D);
        assertEquals(1.0D, before, EPSILON);
        assertEquals(1.0D, after, EPSILON);
    }

    @Test
    void slabTopSitsWhereTheBlockDoes() {
        Matrix3d rotation = identity();
        // A bottom slab: collision box 0..0.5 in Y, so centre y = 0.25 and hy = 0.25.
        Vector3d center = new Vector3d(0.5D, 0.25D, 0.5D);

        assertEquals(0.5D, FootprintSupport.topHeightAt(rotation, center, 0.5D, 0.25D, 0.5D,
                0.5D, 0.5D), EPSILON);
    }

    @Test
    void rotatedDeckKeepsItsTopFaceHorizontal() {
        Matrix3d rotation = new Matrix3d().rotateY(Math.toRadians(45.0D));
        Vector3d center = new Vector3d(0.5D, 0.5D, 0.5D);

        assertEquals(1.0D, FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.5D, 0.5D), EPSILON);
        assertEquals(1.0D, FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.98D, 0.5D), EPSILON);
        assertTrue(Double.isNaN(FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 1.25D, 0.5D)),
                "a 45 degree diamond only reaches 0.707 blocks along X");
    }

    @Test
    void tiltedDeckRaisesTheLowerEdge() {
        Matrix3d rotation = new Matrix3d().rotateZ(Math.toRadians(30.0D));
        Vector3d center = new Vector3d(0.5D, 0.5D, 0.5D);

        double top = FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.5D, 0.5D);
        assertEquals(1.077350269D, top, 1.0E-5D);

        // Rotating about Z tips the block, so one side of the top face is higher: a hull
        // resting at an angle has a sloped deck that the flat "voxel top" cannot describe.
        double low = FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.2D, 0.5D);
        double high = FootprintSupport.topHeightAt(rotation, center, HALF, HALF, HALF, 0.6D, 0.5D);
        assertEquals(0.904145203D, low, 1.0E-5D);
        assertEquals(1.135085203D, high, 1.0E-5D);
    }
}
