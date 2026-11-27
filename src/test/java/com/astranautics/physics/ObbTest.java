package com.astranautics.physics;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObbTest {
    private static Obb box(Vector3d center, double hx, double hy, double hz) {
        Obb obb = new Obb();
        obb.set(center, new Matrix3d(), hx, hy, hz);
        return obb;
    }

    private static Obb rotatedBox(Vector3d center, double degreesAboutY) {
        Matrix3d rotation = new Matrix3d().rotateY(Math.toRadians(degreesAboutY));
        Obb obb = new Obb();
        obb.set(center, rotation, 0.5D, 0.5D, 0.5D);
        return obb;
    }

    @Test
    void separatedBoxesDoNotIntersect() {
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];
        assertFalse(Obb.intersect(box(new Vector3d(0, 0, 0), 0.5, 0.5, 0.5),
                box(new Vector3d(2, 0, 0), 0.5, 0.5, 0.5), normal, penetration));
    }

    @Test
    void touchingBoxesReportPenetrationAndNormal() {
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];

        // Ship voxel sits 0.1 blocks inside a floor block.
        Obb shipVoxel = box(new Vector3d(0.0, 1.4, 0.0), 0.5, 0.5, 0.5);
        Obb floor = box(new Vector3d(0.0, 0.5, 0.0), 0.5, 0.5, 0.5);

        assertTrue(Obb.intersect(shipVoxel, floor, normal, penetration));
        assertEquals(0.1D, penetration[0], 1.0E-6D);
        // Normal must point away from the floor, i.e. +Y.
        assertEquals(1.0D, normal.y, 1.0E-6D);
        assertEquals(0.0D, normal.x, 1.0E-6D);
    }

    @Test
    void rotatedBoxReportsHorizontalNormalWhenHittingAWall() {
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];

        Obb rotated = rotatedBox(new Vector3d(0.9, 0.5, 0.0), 45.0D);
        Obb wall = box(new Vector3d(2.0, 0.5, 0.0), 0.5, 0.5, 0.5);

        assertTrue(Obb.intersect(rotated, wall, normal, penetration));
        assertTrue(penetration[0] > 0.0D);
        // The separation axis has to be mostly horizontal (X).
        assertTrue(Math.abs(normal.x) > Math.abs(normal.y), "normal=" + normal);
    }

    @Test
    void aabbConversionCoversRotatedBox() {
        Obb rotated = rotatedBox(new Vector3d(0, 0, 0), 45.0D);
        AABB bounds = rotated.toAabb();
        // A 1x1 square rotated by 45 degrees spans sqrt(2) on both axes.
        assertEquals(Math.sqrt(2.0D), bounds.getXsize(), 1.0E-6D);
        assertEquals(1.0D, bounds.getYsize(), 1.0E-6D);
    }
}
