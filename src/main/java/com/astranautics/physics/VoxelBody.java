package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.Set;

/**
 * The collision-relevant view of a ship: which voxels touch the outside world and
 * where they sit relative to the centre of mass.
 */
public interface VoxelBody {
    /** Voxels that have at least one empty neighbour. */
    Set<BlockPos> surfaceVoxels();

    /** Centre of the given local voxel, expressed relative to the centre of mass. */
    Vector3d localCenterRelativeToCom(BlockPos local, Vector3d dest);

    /**
     * Collision box of the voxel in its own coordinates ({@code (0,0,0)} to {@code (1,1,1)}
     * is the whole block), so partial blocks - slabs, stairs, fences, modded shapes - and
     * blocks with no collision at all are handled the way the game handles them.
     */
    default AABB collisionBox(BlockPos local) {
        return VoxelShapes.FULL;
    }
}
