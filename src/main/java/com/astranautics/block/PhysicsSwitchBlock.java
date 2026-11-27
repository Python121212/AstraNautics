package com.astranautics.block;

import com.astranautics.block.entity.ModBlockEntities;
import com.astranautics.block.entity.PhysicsSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The physics switch - the single control block of the mod.
 *
 * <p>Right-clicking it detaches every block connected to it into one rigid body
 * (a {@code PhysicsShip}); right-clicking it again puts the blocks back.
 * Shift + right-click prints the current state without toggling.</p>
 */
public class PhysicsSwitchBlock extends BaseEntityBlock {
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    public PhysicsSwitchBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL)
                .lightLevel(state -> state.getValue(ENABLED) ? 7 : 0));
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
        return new PhysicsSwitchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.PHYSICS_SWITCH.get(),
                PhysicsSwitchBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Shift + right click only reports, so the state cannot be flipped by accident.
        if (player.isShiftKeyDown()) {
            if (level.getBlockEntity(pos) instanceof PhysicsSwitchBlockEntity switchEntity) {
                switchEntity.sendStatus(player);
            }
            return InteractionResult.CONSUME;
        }

        PhysicsSwitchBlockEntity.toggle(level, pos, player);

        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F,
                level.getBlockState(pos).getValue(ENABLED) ? 1.0F : 0.6F);

        if (level.getBlockState(pos).getValue(ENABLED)) {
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.4F);
        } else {
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 0.7F);
        }

        return InteractionResult.CONSUME;
    }

    /** Breaking the switch puts an already assembled structure back into the world. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof PhysicsSwitchBlockEntity switchEntity) {
            switchEntity.dissolveOnRemoval(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(ENABLED) ? 15 : 0;
    }
}
