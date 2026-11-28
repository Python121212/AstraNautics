package com.astranautics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Lift gas in a block: every balloon block holds up {@code aerodynamics.balloonLift} mass
 * units, so enough of them make a hull rise.
 *
 * <p>The force itself is applied by {@code Aerodynamics}; this block only marks the voxel as
 * a lift source and gives it a light, soft feel.</p>
 */
public class BalloonBlock extends Block {
    public BalloonBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_WHITE)
                .strength(0.4F)
                .sound(SoundType.WOOL)
                .noOcclusion());
    }
}
