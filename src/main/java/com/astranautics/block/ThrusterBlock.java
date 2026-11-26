package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * スラスター。船に取り付けると推力が増えます（ノズルの向き＝噴射方向）。
 *
 * <p>放置していても安全（火は出ません）が、物理化して航行すると
 * 1 基あたり {@code THRUST_THRUSTER} の推力を船に与えます。</p>
 */
public class ThrusterBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 稼働中（航行中に点灯）。 */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public ThrusterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction dir = state.getValue(FACING).getOpposite();
        double x = pos.getX() + 0.5D + dir.getStepX() * 0.6D;
        double y = pos.getY() + 0.5D + dir.getStepY() * 0.6D;
        double z = pos.getZ() + 0.5D + dir.getStepZ() * 0.6D;
        level.addParticle(ParticleTypes.FLAME, x, y, z,
                -dir.getStepX() * 0.08D, -dir.getStepY() * 0.08D, -dir.getStepZ() * 0.08D);
        level.addParticle(ParticleTypes.SMOKE, x, y, z,
                -dir.getStepX() * 0.05D, 0.02D, -dir.getStepZ() * 0.05D);
    }

    /** 稼働音（航行中のみ）。 */
    public static void playSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.2F);
    }
}
