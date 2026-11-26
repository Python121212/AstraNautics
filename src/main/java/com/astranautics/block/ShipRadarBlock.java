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
 * 艦載レーダー。周囲の「艦」を探して距離と方位を表示します。
 *
 * <p>宇宙戦艦の索敵用。敵 MOD の船でも {@code ShipEntity} なら見つかります。
 * 動力炉の電力（20）を消費します。</p>
 */
public class ShipRadarBlock extends Block {

    /** 索敵半径（ブロック）。 */
    public static final double RANGE = 256.0D;

    public ShipRadarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int found = 0;
        for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(pos).inflate(RANGE),
                e -> e instanceof com.astranautics.physics.ShipEntity)) {
            var ship = (com.astranautics.physics.ShipEntity) entity;
            double dist = Math.sqrt(ship.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()));
            double bearing = Math.toDegrees(Math.atan2(ship.getZ() - pos.getZ(),
                    ship.getX() - pos.getX()));
            player.displayClientMessage(Component.translatable(
                            "message.astranautics.radar_contact",
                            String.format("%.0f", dist), String.format("%.0f", bearing),
                            ship.cellCount()),
                    false);
            found++;
            if (found >= 8) {
                break;
            }
        }
        if (found == 0) {
            player.displayClientMessage(Component.translatable(
                    "message.astranautics.radar_clear"), false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.astranautics.ship_radar")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
