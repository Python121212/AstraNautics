package com.astranautics.block;

import net.minecraft.ChatFormatting;
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
 * 艦橋（Ship Core）。宇宙戦艦の頭脳です。
 *
 * <p>接着した構造物に 1 基置くと、その構造物が「1 隻の艦」として扱われます。
 * 右クリックで艦の状態（質量・推力・浮力・装甲・電力）を表示します。</p>
 *
 * <p>1 隻に 1 基だけ。複数置くと最初の 1 基が採用されます。</p>
 */
public class ShipCoreBlock extends Block {

    public ShipCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        var balance = com.astranautics.aero.AirshipBalance.scan(level, pos, 8);
        player.displayClientMessage(balance.message(), false);
        player.displayClientMessage(Component.translatable(
                        "message.astranautics.core_hint")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.astranautics.ship_core")
                .withStyle(ChatFormatting.GRAY));
    }
}
