package com.astranautics.block;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * Thrust in a block: a powered propeller pushes the hull along its facing at
 * {@code aerodynamics.propellerThrust} per block.
 *
 * <p>Because a hull's voxels are no longer blocks in the world, a propeller cannot be
 * clicked as a block either - {@code PhysicsShip#interact} raycasts the hull and flips
 * {@link #POWERED} for the voxel under the crosshair.</p>
 */
public class PropellerBlock extends HorizontalDirectionalBlock {
    /** Whether this propeller is spinning. Toggled by right-clicking it on a hull. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public PropellerBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Thrust points where the player is looking, so putting one on the back of a hull
        // pushes it away from you.
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(POWERED, Boolean.FALSE);
    }
}
