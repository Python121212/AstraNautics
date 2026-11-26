package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 推力レバー。宇宙船の「何 % でエンジンを回すか」を決めます。
 *
 * <p>右クリックで 25% ずつ上がり、100% の次は 0%（アイドル）へ戻ります。
 * レバーの設定は<b>その船の推力上限</b>として使われ、設定より強いスロットルは出ません。</p>
 */
public class ShipThrottleBlock extends Block {

    /** 設定できるレベル（0, 25, 50, 75, 100）。 */
    public static final int[] LEVELS = {0, 25, 50, 75, 100};

    /** 推力レベル（0..4）。 */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty
            SHIP_THROTTLE_PROPERTY =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create(
                    "throttle", 0, LEVELS.length - 1);

    public ShipThrottleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SHIP_THROTTLE_PROPERTY, 0));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int index = state.getValue(SHIP_THROTTLE_PROPERTY);
        int next = (index + 1) % LEVELS.length;
        level.setBlock(pos, state.setValue(SHIP_THROTTLE_PROPERTY, next), Block.UPDATE_ALL);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LEVER_CLICK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.9F + next * 0.15F);
        player.displayClientMessage(Component.translatable(
                "message.astranautics.throttle", LEVELS[next]), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHIP_THROTTLE_PROPERTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "tooltip.astranautics.throttle")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
