package com.astranautics.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * A shaft with mass. It transmits rotation exactly like a {@link ShaftBlock}, but every
 * flywheel on a network makes the engine slower to spin up and slower to stop, which keeps
 * a heavy ship from lurching when the throttle is slammed.
 */
public class FlywheelBlock extends ShaftBlock {
    public FlywheelBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 9.0F)
                .sound(SoundType.METAL));
    }
}
