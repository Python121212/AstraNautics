package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 艦載砲塔。宇宙戦艦の武装です。
 *
 * <p>動力炉の電力（35）を使い、前方へ弾を撃ちます。連射はできません
 * （クールダウン 40 tick）。船に載せて物理化すると<b>船と一緒に動きます</b>。</p>
 */
public class ShipTurretBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty READY = BooleanProperty.create("ready");

    /** 連射間隔（tick）。 */
    public static final int COOLDOWN = 40;
    /** 弾速。 */
    public static final double MUZZLE_SPEED = 1.6D;

    public ShipTurretBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(READY, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, READY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!state.getValue(READY)) {
            return InteractionResult.CONSUME;
        }
        Direction facing = state.getValue(FACING);
        double dx = facing.getStepX() * MUZZLE_SPEED;
        double dz = facing.getStepZ() * MUZZLE_SPEED;
        var shot = new com.astranautics.physics.TurretShotEntity(level,
                pos.getX() + 0.5D + facing.getStepX() * 0.7D,
                pos.getY() + 0.7D,
                pos.getZ() + 0.5D + facing.getStepZ() * 0.7D,
                dx, 0.0D, dz);
        shot.setOwner(player);
        level.addFreshEntity(shot);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.7F, 1.6F);
        level.setBlock(pos, state.setValue(READY, false), Block.UPDATE_ALL);
        // クールダウン後に再装填（サーバーのスケジューラを使う）
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.scheduleTick(pos, this, COOLDOWN);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                     net.minecraft.util.RandomSource random) {
        if (!state.getValue(READY)) {
            level.setBlock(pos, state.setValue(READY, true), Block.UPDATE_ALL);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "tooltip.astranautics.ship_turret")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
