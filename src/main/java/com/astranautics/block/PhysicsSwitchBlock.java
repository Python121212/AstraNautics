package com.astranautics.block;

import com.astranautics.block.entity.ModBlockEntities;
import com.astranautics.block.entity.PhysicsSwitchBlockEntity;
import com.astranautics.entity.PhysicsShip;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * A switch placed next to a hull that is already physical starts ON and takes control of
     * that hull, so the block shows the state of the world it is sitting in.
     *
     * <p>Breaking the switch of a hull leaves the hull physical (that is deliberate); before
     * this, placing a new switch next to it looked switched off even though the structure was
     * still a rigid body, and clicking it tried to assemble a second time.</p>
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Only a freshly placed switch: a state update of the same block (turning it off,
        // for example) must not be treated as a placement, or it could never be switched off.
        if (oldState.getBlock() instanceof PhysicsSwitchBlock || state.getValue(ENABLED)) {
            return;
        }
        PhysicsShip nearby = PhysicsSwitchBlockEntity.findNearbyShip(serverLevel, pos);
        if (nearby == null || !PhysicsSwitchBlockEntity.isShipWithin(nearby, pos, 2.0D)) {
            // Only a switch that really sits on/inside a hull adopts it; anything further
            // away is a fresh control for a new structure.
            return;
        }
        if (level.getBlockEntity(pos) instanceof PhysicsSwitchBlockEntity switchEntity) {
            switchEntity.linkShip(nearby.getUUID());
        }
        level.setBlock(pos, state.setValue(ENABLED, true), 3);
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

        // The switch itself is part of the structure, so assembling removes this very
        // block: evaluate everything from the pre-toggle state instead of re-reading it.
        boolean newState = !state.getValue(ENABLED);

        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F,
                newState ? 1.0F : 0.6F);

        PhysicsSwitchBlockEntity.apply(level, pos, newState, player);

        level.playSound(null, pos, newState ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.BLOCKS, 0.5F, newState ? 1.4F : 0.7F);

        return InteractionResult.CONSUME;
    }

    // Breaking the switch does not stop an already assembled ship: the hull is an
    // independent rigid body and is switched off by using it (right-click).

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(ENABLED) ? 15 : 0;
    }
}
