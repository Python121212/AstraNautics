package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A small toggle switch. Right-clicking it flips the physics simulation of every
 * {@link PhysicsAssemblerBlock} that is directly adjacent to it (all six sides).
 */
public class PhysicsSwitchBlock extends Block {
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean newState = !state.getValue(ENABLED);
        level.setBlock(pos, state.setValue(ENABLED, newState), 3);

        int toggled = toggleAdjacentAnchors(level, pos, newState);

        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F,
                newState ? 1.0F : 0.6F);

        if (toggled == 0) {
            player.displayClientMessage(
                    Component.translatable("message.astranautics.physics_switch.no_anchor"), true);
        } else {
            player.displayClientMessage(
                    Component.translatable(newState
                                    ? "message.astranautics.physics_switch.on"
                                    : "message.astranautics.physics_switch.off",
                            toggled), true);
        }

        return InteractionResult.CONSUME;
    }

    private int toggleAdjacentAnchors(Level level, BlockPos pos, boolean enabled) {
        int toggled = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState neighbour = level.getBlockState(neighbourPos);
            if (neighbour.getBlock() instanceof PhysicsAssemblerBlock) {
                level.setBlock(neighbourPos,
                        neighbour.setValue(PhysicsAssemblerBlock.ENABLED, enabled), 3);
                level.playSound(null, neighbourPos,
                        enabled ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.7F, enabled ? 1.4F : 0.7F);
                toggled++;
            }
        }
        return toggled;
    }
}
