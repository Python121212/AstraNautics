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

/**
 * 蒸気ベント。熱気バーナーの蒸気版で、<b>1 基でバーナー 2.5 基ぶん</b>の熱を出します。
 *
 * <p>Create Aeronautics では蒸気を作るのに蒸気エンジンが必要ですが、この MOD では
 * レッドストーン信号だけで動く簡略版にしています（水をくみ上げる手間を省くため）。
 * 大きな宇宙船の気嚢を一気に暖めたいときに。</p>
 */
public class SteamVentBlock extends Block {

    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SteamVentBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
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
        boolean lit = level.getBestNeighborSignal(pos) > 0;
        if (state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            net.minecraft.util.RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.5D,
                    pos.getY() + 1.05D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.5D,
                    0.0D, 0.06D, 0.0D);
        }
    }
}
