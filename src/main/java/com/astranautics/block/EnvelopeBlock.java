package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 気密外皮（Envelope）。熱気を閉じ込める布／膜のブロック。
 *
 * <p>このブロックで囲った空間に熱気を貯めると浮きます。
 * 上をきちんと閉じるのがコツで、下の開口部の真下に熱気バーナーを置きます。</p>
 *
 * <p>中は空洞のままにしてください（ブロックを置くほど浮力が減ります）。</p>
 */
public class EnvelopeBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public EnvelopeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
