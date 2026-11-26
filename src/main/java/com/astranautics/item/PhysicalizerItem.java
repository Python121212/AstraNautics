package com.astranautics.item;

import com.astranautics.block.PhysicalizedBlock;
import com.astranautics.registry.ModBlocks;
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
 * 物理化装置（Physicalizer）。レバーのように物理演算を ON / OFF する。
 *
 * <ul>
 *   <li>ブロック（または空気）に使用 → <b>レバー ON（上）</b>: 物理演算を追加</li>
 *   <li>物理化ブロックに使用 → <b>レバー OFF（下）</b>: 物理演算を停止して元の位置へ</li>
 *   <li>レバー OFF の物理化ブロックに使用 → 元のブロックへ復元</li>
 *   <li>空気に使用 → その場に物理コアが空中ブロック化され、落ちていく</li>
 * </ul>
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
        ItemStack stack = context.getItemInHand();

        // --- 既に物理化されているブロック ---
        if (level.getBlockState(pos).is(ModBlocks.PHYSICALIZED.get())) {
            if (player == null) {
                return InteractionResult.PASS;
            }
            // スニーク時は完全復元、通常はレバー OFF（物理演算の停止）
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    boolean restored = PhysicalizedBlock.restore(level, pos);
                    if (restored) {
                        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
                        message(player, "message.astranautics.restored", ChatFormatting.AQUA);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof com.astranautics.block.PhysicalizedBlockEntity be) {
                if (be.isPhysicsEnabled()) {
                    be.disablePhysics();
                    level.setBlock(pos, level.getBlockState(pos)
                            .setValue(PhysicalizedBlock.POWERED, Boolean.FALSE), 3);
                    level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.8F);
                    message(player, "message.astranautics.lever_off", ChatFormatting.GRAY);
                } else {
                    be.enablePhysics();
                    level.setBlock(pos, level.getBlockState(pos)
                            .setValue(PhysicalizedBlock.POWERED, Boolean.TRUE), 3);
                    level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.2F);
                    message(player, "message.astranautics.lever_on", ChatFormatting.AQUA);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // --- 接着剤のグループがあれば「まとめて物理化」 ---
        if (!level.isClientSide && player != null) {
            for (ItemStack candidate : player.getInventory().items) {
                if (candidate.getItem() instanceof AdhesiveItem && AdhesiveItem.totalGluedBlocks(candidate) > 0) {
                    int count = AdhesiveItem.physicalizeGluedGroup(level, candidate, pos);
                    if (count > 0) {
                        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 1.2F);
                        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                                SoundSource.BLOCKS, 0.9F, 0.6F);
                        player.displayClientMessage(Component.translatable(
                                        "message.astranautics.adhesive_batch", count)
                                .withStyle(ChatFormatting.AQUA), true);
                        return InteractionResult.sidedSuccess(false);
                    }
                }
            }
        }

        // --- 新規に物理化（対象が空気なら空中にブロック化） ---
        boolean airborne = level.getBlockState(pos).isAir();
        if (!PhysicalizedBlock.physicalize(level, pos)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.7F, 1.15F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F);
            if (player != null) {
                message(player, airborne ? "message.astranautics.airborne_on"
                        : "message.astranautics.lever_on", ChatFormatting.AQUA);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable("message.astranautics.physicalizer_help")
                            .withStyle(ChatFormatting.GRAY), false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void message(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.astranautics.physicalizer.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.astranautics.physicalizer.2").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.astranautics.physicalizer.3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.astranautics.engine").withStyle(ChatFormatting.DARK_GRAY));
    }
}
