package com.astranautics.block;

import com.astranautics.engine.EnginePart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 自作エンジンの部品ブロック。
 *
 * <p>同じ種類・違う種類を隣接させて積むと 1 基のエンジンとしてまとまります。
 * 右クリックでそのエンジンの性能（推力・燃費・発熱・宇宙適性）を確認できます。</p>
 */
public class EnginePartBlock extends Block {

    private final EnginePart part;

    public EnginePartBlock(EnginePart part, Properties properties) {
        super(properties);
        this.part = part;
    }

    public EnginePart enginePart() {
        return this.part;
    }

    /** サブクラス（ノズルなど）がプロパティを足せるようにする。 */
    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        // 既定では追加プロパティなし
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        var stats = com.astranautics.engine.EngineEvaluator.evaluate(level, pos,
                com.astranautics.engine.EngineEvaluator.airDensityOf(level));
        if (stats.isEmpty()) {
            return InteractionResult.PASS;
        }
        player.displayClientMessage(Component.translatable("engine.astranautics.header",
                stats.totalParts()), false);
        player.displayClientMessage(stats.summary(), false);
        player.displayClientMessage(Component.translatable("engine.astranautics.parts",
                stats.chambers(), stats.nozzles(), stats.vanes(), stats.intakes(),
                stats.turbines(), stats.coolingFins(), stats.oxidizerTanks(),
                stats.fuelTanks()), false);
        player.displayClientMessage(stats.advice(), false);
        return InteractionResult.CONSUME;
    }

    /** 部品の説明文（ツールチップ用のキー）。 */
    public String descriptionKey() {
        return "tooltip.astranautics.engine_part." + this.part.id;
    }
}
