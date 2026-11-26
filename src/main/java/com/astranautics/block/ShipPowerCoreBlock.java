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
 * 動力炉（Ship Power Core）。宇宙船の電源です。
 *
 * <p>1 基あたり「電力 100」を供給し、艦内の電力消費（レーダー・照明・砲塔など）を
 * まかないます。電力が足りないと追加装備が動きません。</p>
 *
 * <p>右クリックで「供給 / 消費」を表示します。炉 1 基でレーダー 4 基ぶん。</p>
 */
public class ShipPowerCoreBlock extends Block {

    /** 1 基あたりの供給量。 */
    public static final int SUPPLY = 100;

    public ShipPowerCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        var report = com.astranautics.aero.ShipSystems.scan(level, pos, 12);
        player.displayClientMessage(report.message(), false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.astranautics.ship_power_core")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
