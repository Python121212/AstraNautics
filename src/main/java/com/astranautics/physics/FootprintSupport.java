package com.astranautics.physics;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Height of a rotated hull voxel's top face directly under a horizontal point.
 *
 * <p>Standing on a hull used to be detected with a separating-axis test, which answers
 * "which way do I push out?" by picking the axis with the smallest overlap. Near the seam
 * of two deck blocks - or near the deck edge - the horizontal overlap is only a few
 * centimetres, less than the ~0.08 a player sinks every tick, so the contact came out as a
 * <em>wall</em>. The wall test then discards it (the feet are trimmed by 0.12), the support
 * is lost, and the player drops through the joint.</p>
 *
 * <p>Asking a different question has no such failure mode: for the point under your feet,
 * which voxel's <em>top face</em> is there, and how high is it? Adjacent voxels tile the
 * surface exactly, so a seam cannot produce a hole, and the answer does not depend on how
 * deep the feet have sunk.</p>
 */
public final class FootprintSupport {
    /** Local coordinates are clamped to the face, with this much rounding slack. */
    private static final double EDGE_SLACK = 1.0E-6D;

    private FootprintSupport() {
    }

    /**
     * @param rotation world rotation of the hull (voxels are axis aligned in local space)
     * @param center   world centre of the voxel
     * @param x        world X of the point to look down from
     * @param z        world Z of the point to look down from
     * @return world Y of the top face under that point, or {@code NaN} when the point is not
     *         over this voxel's top face (including for a hull rotated so far that the face
     *         is vertical and nothing can be stood on)
     */
    public static double topHeightAt(Matrix3d rotation, Vector3d center, double x, double z) {
        // world = center + rotation * (lx, 0.5, lz); solve the X/Z half for (lx, lz).
        double ax = rotation.m00;
        double bx = rotation.m20;
        double az = rotation.m02;
        double bz = rotation.m22;
        double det = ax * bz - bx * az;
        if (Math.abs(det) < 1.0E-6D) {
            return Double.NaN;
        }
        double rx = x - center.x - rotation.m10 * 0.5D;
        double rz = z - center.z - rotation.m12 * 0.5D;
        double lx = (rx * bz - bx * rz) / det;
        double lz = (ax * rz - rx * az) / det;
        if (Math.abs(lx) > 0.5D + EDGE_SLACK || Math.abs(lz) > 0.5D + EDGE_SLACK) {
            return Double.NaN;
        }
        return center.y + rotation.m01 * lx + rotation.m11 * 0.5D + rotation.m21 * lz;
    }
}
