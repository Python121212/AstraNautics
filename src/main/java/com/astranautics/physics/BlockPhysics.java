package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Per-block physical constants and world queries. */
public final class BlockPhysics {
    /** Default mass of one cubic block, in arbitrary mass units (1 m³ = 1 unit). */
    public static final double DEFAULT_MASS = 1.0D;

    private BlockPhysics() {
    }

    /**
     * Mass estimate: harder blocks are heavier. Unbreakable blocks are treated as
     * very heavy so that ships made of them behave sensibly.
     */
    public static double massOf(BlockState state) {
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness < 0.0F) {
            return 10.0D;
        }
        return Math.min(6.0D, 0.4D + 0.35D * hardness);
    }

    /** True when the block has a solid collision shape that participates in physics. */
    public static boolean isSolid(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }
}
