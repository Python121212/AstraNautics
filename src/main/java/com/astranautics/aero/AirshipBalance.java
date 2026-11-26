package com.astranautics.aero;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 船体の「浮力 / 重さ / 推進」バランスを近傍から計算します。
 *
 * <p>組み立て台を右クリックしたときに表示する数値です。
 * Create Aeronautics の Contraption Diagram（力の内訳表示）の簡易版です。</p>
 */
public final class AirshipBalance {

    private AirshipBalance() {
    }

    /** 計算結果。 */
    public record Result(int envelopeCells, double envelopeLift, int levitite, double levititeLift,
                         int thrusters, int nozzles, double weight, double lift, double thrust) {

        public double ratio() {
            return this.weight <= 0.0D ? 99.0D : this.lift / this.weight;
        }

        public Component message() {
            return Component.translatable("message.astranautics.balance",
                    String.format("%.1f", this.weight),
                    String.format("%.1f", this.lift),
                    String.format("%.0f", this.ratio() * 100.0D),
                    this.envelopeCells, this.levitite,
                    this.thrusters, this.nozzles);
        }
    }

    /**
     * 半径 radius の立方体内のブロックからバランスを計算する（簡易走査）。
     */
    public static Result scan(Level level, BlockPos center, int radius) {
        List<BlockPos> cells = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    cells.add(cursor.immutable());
                    states.add(state);
                }
            }
        }

        Envelope.Report envelope = Envelope.scan(level, cells, states);
        Levitite.Result levitite = Levitite.scan(cells, states);

        int thrusters = 0;
        int nozzles = 0;
        double weight = 0.0D;
        for (BlockState state : states) {
            if (state.getBlock() instanceof com.astranautics.block.ThrusterBlock) {
                thrusters++;
            }
            if (state.getBlock() instanceof com.astranautics.block.NozzleBlock) {
                nozzles++;
            }
            weight += com.astranautics.physics.MaterialProfile.of(state).mass();
        }

        double thrust = thrusters * 0.30D + nozzles * 0.12D;
        return new Result(envelope.totalCells(), envelope.lift(), levitite.total(), levitite.lift(),
                thrusters, nozzles, weight, envelope.lift() + levitite.lift(), thrust);
    }

    /** カラー付きの説明（将来の GUI 用）。 */
    public static List<Component> describe(Result result) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("message.astranautics.balance.weight",
                String.format("%.1f", result.weight())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("message.astranautics.balance.lift",
                String.format("%.1f", result.lift())).withStyle(ChatFormatting.AQUA));
        return lines;
    }
}
