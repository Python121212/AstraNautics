package com.astranautics.block;

import com.astranautics.block.entity.GravityAnchorBlockEntity;
import com.astranautics.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * The gravity anchor. While {@link #ENABLED} is true the block entity runs the
 * "physics simulation": every entity inside the configured radius is accelerated
 * towards the anchor.
 *
 * <p>The state is flipped by an adjacent {@link GravitySwitchBlock}.</p>
 */
public class GravityAnchorBlock extends BaseEntityBlock {
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    public GravityAnchorBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(4.0F, 12.0F)
                .sound(SoundType.METAL)
                .lightLevel(state -> state.getValue(ENABLED) ? 10 : 2));
        this.registerDefaultState(this.stateDefinition.any().setValue(ENABLED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENABLED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravityAnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        // The physics simulation only runs on the logical server.
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.GRAVITY_ANCHOR.get(),
                GravityAnchorBlockEntity::serverTick);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(ENABLED) ? 15 : 0;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }
}
