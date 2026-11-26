package com.astranautics.item;

import com.astranautics.block.PhysicalizedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 物理化装置（Physicalizer）。
 *
 * <p>ブロックに対して使用すると、そのブロックを「物理化」して
 * 浮遊・回転するオブジェクトに変化させます（{@link PhysicalizedBlock}）。
 * 物理化されたブロックに使用すると元のブロックへ戻します。</p>
 */
public class PhysicalizerItem extends Item {

    public PhysicalizerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockState(pos).is(com.astranautics.registry.ModBlocks.PHYSICALIZED.get())) {
            // 物理化解除
            if (!level.isClientSide) {
                boolean restored = PhysicalizedBlock.restore(level, pos);
                if (restored) {
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS,
                            0.8F, 1.4F);
                    spawnRestoreParticles(level, pos);
                    if (player != null) {
                        player.displayClientMessage(
                                Component.translatable("message.astranautics.restored")
                                        .withStyle(ChatFormatting.AQUA), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 物理化
        boolean success = PhysicalizedBlock.physicalize(level, pos);
        if (!success) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.8F);
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.astranautics.physicalized")
                                .withStyle(ChatFormatting.AQUA), true);
                // サバイバルでは 1 個消費
                if (!player.getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.astranautics.physicalizer_help")
                                .withStyle(ChatFormatting.GRAY), false);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static void spawnRestoreParticles(Level level, BlockPos pos) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    12, 0.35, 0.35, 0.35, 0.02);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.astranautics.physicalizer.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.astranautics.physicalizer.2")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
