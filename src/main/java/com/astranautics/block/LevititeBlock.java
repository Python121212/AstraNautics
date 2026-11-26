package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * レビタイト（浮く岩）。船に組み込むと常時浮力を出します。
 *
 * <p>上昇はできません（ノズルや熱気の役目）。重い船体の底上げに使います。
 * 低速では抵抗が大きいので、動き出せば軽くなります。</p>
 */
public class LevititeBlock extends Block {

    private final boolean pearlescent;

    public LevititeBlock(boolean pearlescent) {
        super(BlockBehaviour.Properties.of()
                .mapColor(pearlescent ? MapColor.COLOR_PINK : MapColor.COLOR_PURPLE)
                .strength(3.0F, 9.0F)
                .sound(SoundType.AMETHYST)
                .lightLevel(state -> 6)
                .requiresCorrectToolForDrops());
        this.pearlescent = pearlescent;
    }

    /** 真珠レビタイト（1.5 倍の浮力）。 */
    public boolean pearlescent() {
        return this.pearlescent;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }
}
