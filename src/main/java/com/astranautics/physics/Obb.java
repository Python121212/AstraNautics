package com.astranautics.physics;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Oriented bounding box plus a small separating-axis test used by the
 * block-vs-world collision resolver.
 */
public final class Obb {
    public final Vector3d center = new Vector3d();
    public final Vector3d[] axes = {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)};
    public final double[] halfExtents = {0.5D, 0.5D, 0.5D};

    public Obb set(Vector3d centerIn, Matrix3d rotation, double hx, double hy, double hz) {
        center.set(centerIn);
        rotation.getColumn(0, axes[0]);
        rotation.getColumn(1, axes[1]);
        rotation.getColumn(2, axes[2]);
        halfExtents[0] = hx;
        halfExtents[1] = hy;
        halfExtents[2] = hz;
        return this;
    }

    public Obb setAabb(AABB box) {
        center.set((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
        axes[0].set(1, 0, 0);
        axes[1].set(0, 1, 0);
        axes[2].set(0, 0, 1);
        halfExtents[0] = (box.maxX - box.minX) * 0.5D;
        halfExtents[1] = (box.maxY - box.minY) * 0.5D;
        halfExtents[2] = (box.maxZ - box.minZ) * 0.5D;
        return this;
    }

    public double radiusOn(Vector3d axis) {
        return Math.abs(axes[0].dot(axis)) * halfExtents[0]
                + Math.abs(axes[1].dot(axis)) * halfExtents[1]
                + Math.abs(axes[2].dot(axis)) * halfExtents[2];
    }

    /**
     * Separating axis test between two OBBs.
     *
     * @param normalOut      receives the (unit) minimum translation direction, pointing from {@code b} to {@code a}
     * @param penetrationOut receives the overlap depth along that direction
     * @return true when the boxes overlap
     */
    public static boolean intersect(Obb a, Obb b, Vector3d normalOut, double[] penetrationOut) {
        Vector3d delta = new Vector3d(a.center).sub(b.center);
        Vector3d axis = new Vector3d();

        double bestOverlap = Double.MAX_VALUE;
        Vector3d bestAxis = new Vector3d();

        for (int i = 0; i < 3; i++) {
            if (!testAxis(a.axes[i], a, b, delta, axis)) {
                return false;
            }
            double overlap = overlapAmount(axis, a, b, delta);
            if (overlap < bestOverlap) {
                bestOverlap = overlap;
                bestAxis.set(axis);
            }
        }

        for (int j = 0; j < 3; j++) {
            if (!testAxis(b.axes[j], a, b, delta, axis)) {
                return false;
            }
            double overlap = overlapAmount(axis, a, b, delta);
            if (overlap < bestOverlap) {
                bestOverlap = overlap;
                bestAxis.set(axis);
            }
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Vector3d cross = new Vector3d(a.axes[i]).cross(b.axes[j]);
                double length = cross.length();
                if (length < 1.0E-6D) {
                    continue; // parallel edges: axis already covered
                }
                cross.div(length);
                if (!testAxis(cross, a, b, delta, axis)) {
                    return false;
                }
                double overlap = overlapAmount(axis, a, b, delta);
                if (overlap < bestOverlap) {
                    bestOverlap = overlap;
                    bestAxis.set(axis);
                }
            }
        }

        if (bestOverlap == Double.MAX_VALUE || bestOverlap < 0.0D) {
            return false;
        }

        // Point the normal from b towards a.
        if (bestAxis.dot(delta) < 0.0D) {
            bestAxis.negate();
        }
        normalOut.set(bestAxis);
        penetrationOut[0] = bestOverlap;
        return true;
    }

    private static double overlapAmount(Vector3d axis, Obb a, Obb b, Vector3d delta) {
        double distance = Math.abs(delta.dot(axis));
        return a.radiusOn(axis) + b.radiusOn(axis) - distance;
    }

    /** Returns false when the axis separates the boxes. */
    private static boolean testAxis(Vector3d candidate, Obb a, Obb b, Vector3d delta, Vector3d axisOut) {
        axisOut.set(candidate);
        double length = axisOut.length();
        if (length < 1.0E-9D) {
            return true;
        }
        axisOut.div(length);
        return overlapAmount(axisOut, a, b, delta) > 0.0D;
    }

    public AABB toAabb() {
        double rx = radiusOn(new Vector3d(1, 0, 0));
        double ry = radiusOn(new Vector3d(0, 1, 0));
        double rz = radiusOn(new Vector3d(0, 0, 1));
        return new AABB(center.x - rx, center.y - ry, center.z - rz,
                center.x + rx, center.y + ry, center.z + rz);
    }
}
