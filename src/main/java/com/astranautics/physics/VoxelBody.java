package com.astranautics.physics;

import net.minecraft.core.BlockPos;
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
}
