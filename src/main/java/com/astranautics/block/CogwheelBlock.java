package com.astranautics.block;

import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * A gear: always meshes with whatever rotatable block touches it, no matter the axis, so
 * it is the joint that lets a drivetrain turn a corner.
 */
public class CogwheelBlock extends RotatedPillarBlock {
    public CogwheelBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL));
    }
}
