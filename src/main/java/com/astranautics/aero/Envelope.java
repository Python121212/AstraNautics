package com.astranautics.aero;

import com.astranautics.block.EnvelopeBlock;
import com.astranautics.block.HotAirBurnerBlock;
import com.astranautics.block.SteamVentBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 気密外皮（Envelope）で囲まれた気体体積と浮力の計算。
 *
 * <p>Create Aeronautics の「Envelope で囲った空間に熱気を貯めて浮く」考え方を
 * 実装したものです。</p>
 *
 * <pre>
 *   1. 気密外皮で囲まれた空洞（空気）を数える
 *   2. 空洞に接して熱気バーナー／蒸気ベントがあれば「暖められた体積」になる
 *   3. 体積と温度（＝出力）から浮力が決まる
 * </pre>
 *
 * <p>大きな宇宙船でも使えるよう、1 つの気密空間は最大 4096 セルまで数えます。</p>
 */
public final class Envelope {

    /** 1 セルあたりの最大浮力（質量 1.0 のブロックを 1 個浮かせる）。 */
    public static final double LIFT_PER_CELL = 1.0D;
    /** 熱気の最高温度（出力 1.0 のとき）。 */
    public static final double MAX_TEMPERATURE = 1.0D;

    private Envelope() {
    }

    /** 1 つの気密空間。 */
    public record Volume(int cells, boolean heated, int burners, int vents,
                         double temperature, double lift) {
    }

    /** 気密空間の一覧と合計。 */
    public record Report(List<Volume> volumes, int totalCells, double lift, double heat) {

        public static final Report EMPTY = new Report(List.of(), 0, 0.0D, 0.0D);

        public boolean floats() {
            return this.lift > 0.0D;
        }

        /** 1 行サマリ。 */
        public String describe() {
            return this.totalCells + " cells / "
                    + String.format("%.1f", this.lift) + " kpg";
        }
    }

    /**
     * クラスタのセル群から気密空間を見つけて浮力を計算する。
     *
     * @param level  ワールド（外側の密閉判定に使う）
     * @param cells  セルの実座標
     * @param states 各セルのブロックステート
     */
    public static Report scan(Level level, List<BlockPos> cells, List<BlockState> states) {
        if (cells.isEmpty()) {
            return Report.EMPTY;
        }
        Set<BlockPos> envelope = new HashSet<>();
        Set<BlockPos> heaters = new HashSet<>();
        Set<BlockPos> vents = new HashSet<>();
        List<BlockPos> airCandidates = new ArrayList<>();

        for (int i = 0; i < cells.size(); i++) {
            BlockState state = states.get(i);
            if (state.getBlock() instanceof EnvelopeBlock) {
                envelope.add(cells.get(i));
            } else if (state.getBlock() instanceof HotAirBurnerBlock) {
                heaters.add(cells.get(i));
            } else if (state.getBlock() instanceof SteamVentBlock) {
                vents.add(cells.get(i));
            } else if (state.isAir()) {
                airCandidates.add(cells.get(i));
            }
        }
        if (envelope.isEmpty() || airCandidates.isEmpty()) {
            return Report.EMPTY;
        }

        List<Volume> volumes = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        double totalLift = 0.0D;
        double totalHeat = 0.0D;
        int totalCells = 0;

        for (BlockPos start : airCandidates) {
            if (visited.contains(start)) {
                continue;
            }
            Deque<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> region = new ArrayList<>();
            queue.add(start);
            visited.add(start);
            boolean sealed = true;
            int burners = 0;
            int steam = 0;

            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                if (region.size() >= 4096) {
                    sealed = false;
                    break;
                }
                region.add(pos);
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (envelope.contains(next)) {
                        continue;
                    }
                    if (heaters.contains(next)) {
                        burners++;
                        continue;
                    }
                    if (vents.contains(next)) {
                        steam++;
                        continue;
                    }
                    if (visited.contains(next)) {
                        continue;
                    }
                    BlockState neighbor = level.getBlockState(next);
                    if (!(neighbor.isAir() || neighbor.canBeReplaced())) {
                        // 外皮でもバーナーでもないブロック＝密閉されていない
                        sealed = false;
                        continue;
                    }
                    visited.add(next);
                    queue.add(next);
                }
            }

            if (!sealed || region.isEmpty()) {
                continue;
            }
            boolean heated = burners > 0 || steam > 0;
            // 蒸気ベントは熱気バーナーの 2.5 倍ぶんの出力を持つ
            double temperature = heated
                    ? Math.min(MAX_TEMPERATURE, (burners + steam * 2.5D) * 0.5D)
                    : 0.0D;
            double lift = region.size() * LIFT_PER_CELL * temperature;
            volumes.add(new Volume(region.size(), heated, burners, steam, temperature, lift));
            totalCells += region.size();
            totalLift += lift;
            totalHeat += temperature;
        }

        return new Report(volumes, totalCells, totalLift, totalHeat);
    }
}
