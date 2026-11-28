package com.astranautics.block;

import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Transmits rotation along its own axis, like Create's shafts. Two shafts only mesh when
 * they sit end to end along the same axis, so turning a corner needs a
 * {@link CogwheelBlock} - that is the "build it yourself" part of the drivetrain.
 */
public class ShaftBlock extends RotatedPillarBlock {
    public ShaftBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.2F, 4.0F)
                .sound(SoundType.WOOD));
    }

    protected ShaftBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
