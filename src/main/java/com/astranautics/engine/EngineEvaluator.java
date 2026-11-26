package com.astranautics.engine;

import com.astranautics.AstraNautics;
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
 * 連結した部品を 1 基のエンジンとして評価する。
 *
 * <p>燃焼室・ノズル・吸気口・タービン・冷却フィン・タンクを<b>隣接させて置く</b>と
 * 1 基のエンジンとしてまとまり、積み方に応じて推力・燃費・発熱が変わります。
 * これが「エンジンをプレイヤーが作る」ための評価器です。</p>
 *
 * <pre>
 *   推力        = 燃焼室 × 1.00 × ノズル効率(0.6〜1.6) + 吸気口 × 大気係数
 *   ノズル効率  = 1.0 + 0.16 × ノズル数            （上限 1.6）
 *   燃費        = 燃焼室 × 0.55 × (1 − 0.22 × タービン数)（下限 0.25）
 *   発熱        = 燃焼室 × 0.55 ÷ (1 + 0.55 × 冷却フィン数)
 *   燃料容量    = 1.0 + 0.6 × 燃料タンク数
 *   宇宙推力    = 酸化剤タンク ≥ 1 なら大気と同じ、無ければ吸気分を失う
 * </pre>
 */
public final class EngineEvaluator {

    /** エンジン 1 基の最大部品数（重くなりすぎないように）。 */
    public static final int MAX_PARTS = 128;

    private EngineEvaluator() {
    }

    /* ------------------------------------------------------------------ */
    /* 探索                                                                */
    /* ------------------------------------------------------------------ */

    /** 指定位置から連結しているエンジン部品を集める。 */
    public static List<BlockPos> collect(Level level, BlockPos origin) {
        List<BlockPos> found = new ArrayList<>();
        if (EnginePart.fromState(level.getBlockState(origin)) == null) {
            return found;
        }
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = origin.immutable();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && found.size() < MAX_PARTS) {
            BlockPos pos = queue.poll();
            found.add(pos);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir).immutable();
                if (visited.contains(next) || found.size() >= MAX_PARTS) {
                    continue;
                }
                if (EnginePart.fromState(level.getBlockState(next)) != null) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /**
     * エンジンを評価する。
     *
     * @param airDensity 大気の濃さ（0 = 宇宙、1 = 地表）
     */
    public static EngineStats evaluate(Level level, BlockPos origin, double airDensity) {
        return evaluate(collect(level, origin), level, airDensity);
    }

    /** 部品リストから評価する。 */
    public static EngineStats evaluate(List<BlockPos> parts, Level level, double airDensity) {
        int chambers = 0;
        int nozzles = 0;
        int intakes = 0;
        int turbines = 0;
        int coolingFins = 0;
        int oxidizer = 0;
        int fuelTanks = 0;
        double mass = 0.0D;

        for (BlockPos pos : parts) {
            EnginePart part = EnginePart.fromState(level.getBlockState(pos));
            if (part == null) {
                continue;
            }
            mass += part.mass();
            switch (part) {
                case COMBUSTION_CHAMBER -> chambers++;
                case NOZZLE -> nozzles++;
                case INTAKE -> intakes++;
                case TURBINE -> turbines++;
                case COOLING_FIN -> coolingFins++;
                case OXIDIZER_TANK -> oxidizer++;
                case FUEL_TANK -> fuelTanks++;
            }
        }
        return compute(chambers, nozzles, intakes, turbines, coolingFins, oxidizer, fuelTanks,
                parts.size(), mass, airDensity);
    }

    /** 船のようにワールドに存在しない部品リストから評価する（最大推力のエンジンを採用）。 */
    public static EngineStats evaluateBest(List<EnginePartList> engines, double airDensity) {
        EngineStats best = EngineStats.EMPTY;
        for (EnginePartList engine : engines) {
            double mass = 0.0D;
            int chambers = 0, nozzles = 0, intakes = 0, turbines = 0, cooling = 0, oxi = 0, tanks = 0;
            for (int i = 0; i < engine.size(); i++) {
                EnginePart part = EnginePart.fromState(engine.state(i));
                if (part == null) {
                    continue;
                }
                mass += part.mass();
                switch (part) {
                    case COMBUSTION_CHAMBER -> chambers++;
                    case NOZZLE -> nozzles++;
                    case INTAKE -> intakes++;
                    case TURBINE -> turbines++;
                    case COOLING_FIN -> cooling++;
                    case OXIDIZER_TANK -> oxi++;
                    case FUEL_TANK -> tanks++;
                }
            }
            EngineStats stats = compute(chambers, nozzles, intakes, turbines, cooling, oxi, tanks,
                    engine.size(), mass, airDensity);
            if (stats.thrust() > best.thrust()) {
                best = stats;
            }
        }
        return best;
    }

    /** 数値から性能を計算する（テストしやすいよう分離）。 */
    public static EngineStats compute(int chambers, int nozzles, int intakes, int turbines,
                                      int coolingFins, int oxidizer, int fuelTanks,
                                      int totalParts, double mass, double airDensity) {
        if (chambers == 0) {
            return new EngineStats(chambers, nozzles, intakes, turbines, coolingFins, oxidizer,
                    fuelTanks, totalParts, 0.0D, 0.0D, 0.0D, 1.0D + 0.6D * fuelTanks, 0.0D, mass);
        }

        double clampedAir = Math.max(0.0D, Math.min(1.0D, airDensity));

        // ノズル効率：0.6 ～ 1.6
        double nozzleEfficiency = Math.min(1.6D, 0.6D + 0.16D * nozzles);
        // 燃費：タービンで改善（下限 0.25 倍）
        double economy = Math.max(0.25D, 1.0D - 0.22D * turbines);
        // 冷却
        double cooling = 1.0D + 0.55D * coolingFins;

        // 燃焼室ぶんの推力
        double baseThrust = chambers * 1.00D * nozzleEfficiency;
        // 吸気は大気があるときだけ効く
        double intakeThrust = intakes * 0.12D * clampedAir;
        double thrust = baseThrust + intakeThrust;

        // 酸化剤があれば宇宙でも同じ推力、無ければ吸気分と 20% を失う
        double vacuumThrust = oxidizer > 0 ? thrust : baseThrust * 0.8D;

        // 燃費（1 tick あたり）
        double fuelUse = chambers * 0.55D * economy * 0.01D;
        double heat = chambers * 0.55D / cooling;

        return new EngineStats(chambers, nozzles, intakes, turbines, coolingFins, oxidizer,
                fuelTanks, totalParts, thrust, vacuumThrust, fuelUse,
                1.0D + 0.6D * fuelTanks, heat, mass);
    }

    /** 大気密度（地表 = 1.0、宇宙 = 0.0）。ディメンションで変える布石。 */
    public static double airDensityOf(Level level) {
        var key = level.dimension().location();
        if (key.getPath().contains("space") || key.getPath().contains("moon")) {
            return 0.0D;
        }
        if (level.dimension() == Level.NETHER) {
            return 0.35D;
        }
        if (level.dimension() == Level.END) {
            return 0.05D;
        }
        // 高度で薄くなる（y=64 で 1.0、y=320 で 0.55）
        double y = level instanceof net.minecraft.server.level.ServerLevel serverLevel
                ? serverLevel.getSharedSpawnPos().getY() : 64.0D;
        return Math.max(0.55D, Math.min(1.0D, 1.0D - (y - 64.0D) / 1024.0D));
    }

    /** デバッグ用ログ。 */
    public static void log(BlockPos origin, EngineStats stats) {
        AstraNautics.LOGGER.debug("[AstraNautics] エンジン {}: 部品={} 推力={} 燃料={} 発熱={}",
                origin, stats.totalParts(), stats.thrust(), stats.fuelUse(), stats.heat());
    }
}
