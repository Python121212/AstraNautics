package com.astranautics.physics;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Set;

/**
 * Air trapped inside a closed hull, which is what makes a boat float.
 *
 * <p>Water buoyancy is displacement: what matters is not how much wood a hull is made of,
 * but how much water it pushes aside. A hollow wooden boat displaces the air it encloses and
 * floats; the same wood as a solid block sinks. This counts the enclosed air cells with a
 * flood fill from the outside of the hull's bounding box - everything the fill cannot reach
 * is sealed in.</p>
 */
public final class HullVolume {
    /** Bail out for absurd bounds rather than allocating a huge array. */
    private static final long MAX_CELLS = 4_000_000L;

    private HullVolume() {
    }

    /**
     * @return the number of air cells sealed inside the hull, or 0 when the hull is open
     *         (or too large to analyse)
     */
    public static int enclosedCells(Set<BlockPos> voxels) {
        if (voxels.isEmpty()) {
            return 0;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : voxels) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // One cell of margin on every side, so the flood fill always has an outside layer.
        int sizeX = maxX - minX + 3;
        int sizeY = maxY - minY + 3;
        int sizeZ = maxZ - minZ + 3;
        long total = (long) sizeX * sizeY * sizeZ;
        if (total > MAX_CELLS) {
            return 0;
        }
        int cells = (int) total;

        boolean[] solid = new boolean[cells];
        for (BlockPos pos : voxels) {
            int index = index(pos.getX() - minX + 1, pos.getY() - minY + 1, pos.getZ() - minZ + 1,
                    sizeX, sizeY);
            if (index >= 0 && index < cells) {
                solid[index] = true;
            }
        }

        boolean[] outside = new boolean[cells];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (x != 0 && x != sizeX - 1 && y != 0 && y != sizeY - 1 && z != 0 && z != sizeZ - 1) {
                        continue;
                    }
                    int index = index(x, y, z, sizeX, sizeY);
                    if (!solid[index] && !outside[index]) {
                        outside[index] = true;
                        queue.add(index);
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int x = current % sizeX;
            int y = (current / sizeX) % sizeY;
            int z = current / (sizeX * sizeY);

            for (int axis = 0; axis < 3; axis++) {
                for (int step = -1; step <= 1; step += 2) {
                    int nx = x + (axis == 0 ? step : 0);
                    int ny = y + (axis == 1 ? step : 0);
                    int nz = z + (axis == 2 ? step : 0);
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                        continue;
                    }
                    int index = index(nx, ny, nz, sizeX, sizeY);
                    if (solid[index] || outside[index]) {
                        continue;
                    }
                    outside[index] = true;
                    queue.add(index);
                }
            }
        }

        int enclosed = 0;
        for (int i = 0; i < cells; i++) {
            if (!solid[i] && !outside[i]) {
                enclosed++;
            }
        }
        return enclosed;
    }

    private static int index(int x, int y, int z, int sizeX, int sizeY) {
        return (z * sizeY + y) * sizeX + x;
    }
}
