package com.astranautics.physics;

import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Warm starting: carries the accumulated impulses of each contact over to the next
 * physics step so the solver starts from last frame's solution.
 *
 * <p>This is the single biggest win for both quality (less jitter) and cost (the
 * solver needs far fewer iterations to converge).</p>
 */
public final class ContactCache {
    private static final int MAX_ENTRIES = 4096;

    private Map<Long, double[]> entries = new HashMap<>(512);
    private Map<Long, double[]> spare = new HashMap<>(512);

    /** Stable, position-quantised key for a contact point + normal. */
    public static long keyOf(Vector3d point, Vector3d normal) {
        long qx = Math.round(point.x * 4.0D);
        long qy = Math.round(point.y * 4.0D);
        long qz = Math.round(point.z * 4.0D);
        long axis = dominantAxis(normal);
        return (qx * 73856093L) ^ (qy * 19349663L) ^ (qz * 83492791L) ^ (axis * 2654435761L);
    }

    private static long dominantAxis(Vector3d normal) {
        double ax = Math.abs(normal.x);
        double ay = Math.abs(normal.y);
        double az = Math.abs(normal.z);
        long index = ay >= ax && ay >= az ? 1L : (ax >= az ? 0L : 2L);
        // Sign matters: a floor contact and a ceiling contact must not share impulses.
        double value = index == 0L ? normal.x : (index == 1L ? normal.y : normal.z);
        return index * 2L + (value >= 0.0D ? 1L : 0L);
    }

    public double[] get(long key) {
        return entries.get(key);
    }

    public void put(long key, double normalImpulse, double tangentImpulse,
                    double tx, double ty, double tz) {
        if (spare.size() >= MAX_ENTRIES) {
            return;
        }
        spare.put(key, new double[]{normalImpulse, tangentImpulse, tx, ty, tz});
    }

    /** Swaps the freshly collected entry table with the one from the previous step. */
    public void swap() {
        Map<Long, double[]> previous = entries;
        entries = spare;
        spare = previous;
        spare.clear();
    }

    public void clear() {
        entries.clear();
        spare.clear();
    }
}
