package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uniform-grid index of a ship's world-space surface voxels.
 *
 * <p>Rebuilt once per tick and used for ship-vs-ship, ship-vs-entity and rendering
 * broad-phase queries, so those systems do not have to walk every voxel.</p>
 */
public final class VoxelSpatialIndex {
    private static final double CELL_SIZE = 2.0D;

    private final Map<Long, List<Entry>> cells = new HashMap<>(64);
    private final List<Entry> entries = new ArrayList<>();

    /** One world-space surface voxel. */
    public static final class Entry {
        public final BlockPos local;
        public final Vector3d center = new Vector3d();
        /** Half size of the voxel's collision box (0.5 each for a full block). */
        public final Vector3d halfExtents = new Vector3d(0.5D, 0.5D, 0.5D);

        Entry(BlockPos local) {
            this.local = local;
        }
    }

    public static VoxelSpatialIndex build(RigidBody body, VoxelBody voxels) {
        VoxelSpatialIndex index = new VoxelSpatialIndex();
        Vector3d localCenter = new Vector3d();

        for (BlockPos local : voxels.surfaceVoxels()) {
            AABB box = voxels.collisionBox(local);
            if (VoxelShapes.isEmpty(box)) {
                // Torches, flowers, signs: they render, but nothing collides with them.
                continue;
            }
            voxels.localCenterRelativeToCom(local, localCenter);
            // From the voxel centre to the centre of its collision box (slabs, stairs, ...).
            localCenter.x += (box.minX + box.maxX) * 0.5D - 0.5D;
            localCenter.y += (box.minY + box.maxY) * 0.5D - 0.5D;
            localCenter.z += (box.minZ + box.maxZ) * 0.5D - 0.5D;
            Entry entry = new Entry(local);
            body.toWorld(localCenter, entry.center);
            entry.halfExtents.set(box.getXsize() * 0.5D, box.getYsize() * 0.5D, box.getZsize() * 0.5D);
            index.add(entry);
        }
        return index;
    }

    private void add(Entry entry) {
        entries.add(entry);
        cells.computeIfAbsent(cellKey(entry.center.x, entry.center.y, entry.center.z),
                key -> new ArrayList<>(8)).add(entry);
    }

    private static long cellKey(double x, double y, double z) {
        long cx = (long) Math.floor(x / CELL_SIZE);
        long cy = (long) Math.floor(y / CELL_SIZE);
        long cz = (long) Math.floor(z / CELL_SIZE);
        return (cx & 0x1FFFFFL) << 42 | (cy & 0x1FFFFFL) << 21 | (cz & 0x1FFFFFL);
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Appends every voxel whose centre is within {@code radius} of {@code point}. */
    public void query(Vector3d point, double radius, List<Entry> out) {
        int minX = (int) Math.floor((point.x - radius) / CELL_SIZE);
        int maxX = (int) Math.floor((point.x + radius) / CELL_SIZE);
        int minY = (int) Math.floor((point.y - radius) / CELL_SIZE);
        int maxY = (int) Math.floor((point.y + radius) / CELL_SIZE);
        int minZ = (int) Math.floor((point.z - radius) / CELL_SIZE);
        int maxZ = (int) Math.floor((point.z + radius) / CELL_SIZE);

        double radiusSq = radius * radius;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long key = ((long) x & 0x1FFFFFL) << 42 | ((long) y & 0x1FFFFFL) << 21 | ((long) z & 0x1FFFFFL);
                    List<Entry> cell = cells.get(key);
                    if (cell == null) {
                        continue;
                    }
                    for (Entry entry : cell) {
                        if (entry.center.distanceSquared(point) <= radiusSq) {
                            out.add(entry);
                        }
                    }
                }
            }
        }
    }
}
