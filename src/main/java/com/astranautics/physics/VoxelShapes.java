package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local collision box of a block state, used so a hull made of slabs, stairs, fences or a
 * modded block collides like the block does instead of like a full cube.
 *
 * <p>The box is in the voxel's own coordinates: {@code (0,0,0)} to {@code (1,1,1)} is the
 * whole block. An empty box means "you can walk through it" (torches, flowers, signs), which
 * is why such voxels are left out of the collision index entirely.</p>
 *
 * <p>Shapes are resolved without a level, since a hull's voxels have no world position any
 * more; a state whose shape needs the level is treated as a full cube, which is what the
 * mod assumed for everything before.</p>
 */
public final class VoxelShapes {
    public static final AABB FULL = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    /** Smaller than this on any axis means the block has no collision at all. */
    private static final double EMPTY_EPSILON = 1.0E-4D;

    /**
     * Cache: {@link BlockState}s are interned, so identity is the key. Assembly rebuilds the
     * whole structure on every block change, and this keeps that from re-resolving shapes.
     */
    private static final Map<BlockState, AABB> CACHE = new ConcurrentHashMap<>(256);
    private static final int CACHE_LIMIT = 4096;

    private VoxelShapes() {
    }

    public static AABB collisionBox(BlockState state) {
        AABB cached = CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        AABB box;
        try {
            VoxelShape shape = state.getCollisionShape(null, BlockPos.ZERO);
            box = shape.isEmpty() ? Shapes.empty().bounds() : shape.bounds();
        } catch (RuntimeException exception) {
            // A modded block that insists on a real level: treat it as a full cube.
            box = FULL;
        }
        if (CACHE.size() < CACHE_LIMIT) {
            CACHE.putIfAbsent(state, box);
        }
        return box;
    }

    /** True when the state has no collision at all. */
    public static boolean isEmpty(BlockState state) {
        return isEmpty(collisionBox(state));
    }

    /** True when the box is too thin to collide on at least one axis. */
    public static boolean isEmpty(AABB box) {
        return box.getXsize() < EMPTY_EPSILON || box.getYsize() < EMPTY_EPSILON
                || box.getZsize() < EMPTY_EPSILON;
    }
}
