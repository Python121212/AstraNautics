package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 熱気バーナー。真上の気密外皮の中の空気を暖めます（Create Aeronautics の Hot Air Burner）。
 *
 * <p>レッドストーン信号で出力が変わります（0〜15）。出力が高いほど熱気の温度が上がり、
 * 浮力が増えます。信号が無いと暖めません。</p>
 *
 * <p>置き方のコツ: <b>気密外皮で囲った袋の開口部の真下</b>に置く。
 * 上をきちんと閉じないと熱気が抜けて浮きません。</p>
 */
public class HotAirBurnerBlock extends Block {

    /** 燃えているか。 */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** 出力レベル（1..3 の見た目用）。 */
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public HotAirBurnerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(HEAT, 0)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, HEAT, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        int power = level.getBestNeighborSignal(pos);
        BlockState next = state
                .setValue(LIT, power > 0)
                .setValue(HEAT, power <= 0 ? 0 : Math.min(3, 1 + power / 6));
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            net.minecraft.util.RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
                pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                0.0D, 0.02D, 0.0D);
        if (random.nextInt(4) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                    0.0D, 0.03D, 0.0D);
        }
    }
}
