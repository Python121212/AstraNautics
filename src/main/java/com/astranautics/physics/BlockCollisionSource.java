package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Supplies the static collision geometry the ship is tested against.
 *
 * <p>Abstracted away from {@code Level} so the solver can be unit tested against a
 * synthetic voxel world.</p>
 */
public interface BlockCollisionSource {
    /** World-space collision boxes of the block at {@code pos}; empty for air. */
    List<AABB> collisionBoxesAt(BlockPos pos);
}
