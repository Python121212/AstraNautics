package com.astranautics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * The power source of a drivetrain. It burns fuel to spin the network it is bolted into;
 * the propellers on that network turn that rotation into thrust.
 *
 * <p>Its state (throttle, remaining fuel, current RPM) is kept in the hull entity, not in a
 * block entity, because a physical hull's blocks are no longer in the world. Right-click
 * with fuel to stoke it, sneak-right-click to step the throttle 0..4.</p>
 */
public class SteamEngineBlock extends RotatedPillarBlock {
    /** Firebox lit: burning fuel with the throttle open. Drives the glowing model. */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Throttle positions, 0 (stopped) to {@link #MAX_THROTTLE}. */
    public static final int MAX_THROTTLE = 4;

    public SteamEngineBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(3.5F, 9.0F)
                .sound(SoundType.METAL)
                .lightLevel(state -> state.getValue(LIT) ? 12 : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, net.minecraft.core.Direction.Axis.Y)
                .setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
