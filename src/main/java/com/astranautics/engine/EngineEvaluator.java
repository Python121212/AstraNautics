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
 *   最高速度    = 0.45 + 0.18 × ノズル + 0.06 × 羽根 - 0.02 × 燃焼室
 *   設計圧力    = 羽根数 ÷ (ノズル数 × 4)   （1.0 超で損傷リスク）
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
        int vanes = 0;
        double mass = 0.0D;
        double thrustMul = 1.0D;
        double efficiencyBonus = 0.0D;
        double speedBonus = 0.0D;
        double heatMul = 1.0D;
        int shapeCount = 0;

        for (BlockPos pos : parts) {
            EnginePart part = EnginePart.fromState(level.getBlockState(pos));
            if (part == null) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            mass += part.mass();
            if (part == EnginePart.NOZZLE) {
                NozzleShape shape = NozzleShape.fromState(state);
                thrustMul *= shape.thrustMul;
                efficiencyBonus += shape.efficiencyBonus;
                speedBonus += shape.speedBonus;
                heatMul *= shape.heatMul;
                mass = mass - part.mass() + part.mass() * shape.massMul;
                shapeCount++;
            }
            switch (part) {
                case COMBUSTION_CHAMBER -> chambers++;
                case NOZZLE -> nozzles++;
                case INTAKE -> intakes++;
                case TURBINE -> turbines++;
                case COOLING_FIN -> coolingFins++;
                case OXIDIZER_TANK -> oxidizer++;
                case FUEL_TANK -> fuelTanks++;
                case NOZZLE_VANE -> vanes++;
            }
        }
        return compute(chambers, nozzles, intakes, turbines, coolingFins, oxidizer, fuelTanks,
                vanes, parts.size(), mass, airDensity,
                shapeCount == 0 ? 1.0D : Math.pow(thrustMul, 1.0D / shapeCount),
                efficiencyBonus, speedBonus,
                shapeCount == 0 ? 1.0D : Math.pow(heatMul, 1.0D / shapeCount));
    }

    /** スラスターに接しているノズルか（＝スラスターの周りに置いたノズル）。 */
    private static boolean isMountedNozzle(EnginePartList engine, int index) {
        BlockPos pos = engine.pos(index);
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockState neighbor = engine.at(pos.relative(dir));
            if (neighbor != null
                    && neighbor.getBlock() instanceof com.astranautics.block.ThrusterBlock) {
                return true;
            }
        }
        return false;
    }

    /** 全エンジンを合算して評価する（大きな船でスラスターを複数積む想定）。 */
    public static EngineStats evaluateAll(List<EnginePartList> engines, double airDensity) {
        int chambers = 0, nozzles = 0, intakes = 0, turbines = 0, cooling = 0, oxi = 0, tanks = 0;
        int vanes = 0, thrusters = 0, mounted = 0, totalParts = 0;
        double mass = 0.0D;
        double thrustMul = 1.0D;
        double efficiencyBonus = 0.0D;
        double speedBonus = 0.0D;
        double heatMul = 1.0D;
        int shapeCount = 0;
        for (EnginePartList engine : engines) {
            for (int i = 0; i < engine.size(); i++) {
                BlockState state = engine.state(i);
                EnginePart part = EnginePart.fromState(state);
                if (part == null) {
                    if (state != null
                            && state.getBlock() instanceof com.astranautics.block.ThrusterBlock) {
                        thrusters++;
                        totalParts++;
                    }
                    continue;
                }
                totalParts++;
                mass += part.mass();
                if (part == EnginePart.NOZZLE) {
                    NozzleShape shape = NozzleShape.fromState(state);
                    NozzleSize size = NozzleSize.fromState(state);
                    thrustMul *= shape.thrustMul * size.thrustMul;
                    efficiencyBonus += shape.efficiencyBonus;
                    speedBonus += shape.speedBonus + size.speedBonus;
                    heatMul *= shape.heatMul * size.heatMul;
                    mass = mass - part.mass()
                            + part.mass() * shape.massMul * size.massMul;
                    shapeCount++;
                    if (isMountedNozzle(engine, i)) {
                        mounted++;
                    }
                }
                switch (part) {
                    case COMBUSTION_CHAMBER -> chambers++;
                    case NOZZLE -> nozzles++;
                    case INTAKE -> intakes++;
                    case TURBINE -> turbines++;
                    case COOLING_FIN -> cooling++;
                    case OXIDIZER_TANK -> oxi++;
                    case FUEL_TANK -> tanks++;
                    case NOZZLE_VANE -> vanes++;
                }
            }
        }
        if (totalParts == 0) {
            return EngineStats.EMPTY;
        }
        return compute(chambers, nozzles, intakes, turbines, cooling, oxi, tanks,
                vanes, totalParts, mass, airDensity,
                shapeCount == 0 ? 1.0D : Math.pow(thrustMul, 1.0D / shapeCount),
                efficiencyBonus, speedBonus,
                shapeCount == 0 ? 1.0D : Math.pow(heatMul, 1.0D / shapeCount),
                thrusters, mounted);
    }

    /** 船のようにワールドに存在しない部品リストから評価する（最大推力のエンジンを採用）。 */
    public static EngineStats evaluateBest(List<EnginePartList> engines, double airDensity) {
        EngineStats best = EngineStats.EMPTY;
        for (EnginePartList engine : engines) {
            double mass = 0.0D;
            int chambers = 0, nozzles = 0, intakes = 0, turbines = 0, cooling = 0, oxi = 0, tanks = 0;
            int vanes = 0;
            double thrustMul = 1.0D;
            double efficiencyBonus = 0.0D;
            double speedBonus = 0.0D;
            double heatMul = 1.0D;
            int shapeCount = 0;
            for (int i = 0; i < engine.size(); i++) {
                EnginePart part = EnginePart.fromState(engine.state(i));
                if (part == null) {
                    continue;
                }
                mass += part.mass();
                if (part == EnginePart.NOZZLE) {
                    NozzleShape shape = NozzleShape.fromState(engine.state(i));
                    thrustMul *= shape.thrustMul;
                    efficiencyBonus += shape.efficiencyBonus;
                    speedBonus += shape.speedBonus;
                    heatMul *= shape.heatMul;
                    mass = mass - part.mass() + part.mass() * shape.massMul;
                    shapeCount++;
                }
                switch (part) {
                    case COMBUSTION_CHAMBER -> chambers++;
                    case NOZZLE -> nozzles++;
                    case INTAKE -> intakes++;
                    case TURBINE -> turbines++;
                    case COOLING_FIN -> cooling++;
                    case OXIDIZER_TANK -> oxi++;
                    case FUEL_TANK -> tanks++;
                    case NOZZLE_VANE -> vanes++;
                }
            }
            EngineStats stats = compute(chambers, nozzles, intakes, turbines, cooling, oxi, tanks,
                    vanes, engine.size(), mass, airDensity,
                    shapeCount == 0 ? 1.0D : Math.pow(thrustMul, 1.0D / shapeCount),
                    efficiencyBonus, speedBonus,
                    shapeCount == 0 ? 1.0D : Math.pow(heatMul, 1.0D / shapeCount));
            if (stats.thrust() > best.thrust()) {
                best = stats;
            }
        }
        return best;
    }

    /** 形状指定なしの計算（テスト・互換用）。 */
    public static EngineStats compute(int chambers, int nozzles, int intakes, int turbines,
                                      int coolingFins, int oxidizer, int fuelTanks,
                                      int vanes, int totalParts, double mass, double airDensity) {
        return compute(chambers, nozzles, intakes, turbines, coolingFins, oxidizer, fuelTanks,
                vanes, totalParts, mass, airDensity, 1.0D, 0.0D, 0.0D, 1.0D);
    }

    /** 形状補正つき（スラスターなし）。 */
    public static EngineStats compute(int chambers, int nozzles, int intakes, int turbines,
                                      int coolingFins, int oxidizer, int fuelTanks,
                                      int vanes, int totalParts, double mass, double airDensity,
                                      double thrustMul, double efficiencyBonus,
                                      double speedBonus, double heatMul) {
        return compute(chambers, nozzles, intakes, turbines, coolingFins, oxidizer, fuelTanks,
                vanes, totalParts, mass, airDensity, thrustMul, efficiencyBonus, speedBonus,
                heatMul, 0, 0);
    }

    /**
     * 数値から性能を計算する（形状補正＋スラスター対応）。
     *
     * @param thrusters      スラスターの数（大きな船は複数積む）
     * @param mountedNozzles スラスターの周りに置いたノズルの数
     */
    public static EngineStats compute(int chambers, int nozzles, int intakes, int turbines,
                                      int coolingFins, int oxidizer, int fuelTanks,
                                      int vanes, int totalParts, double mass, double airDensity,
                                      double thrustMul, double efficiencyBonus,
                                      double speedBonus, double heatMul,
                                      int thrusters, int mountedNozzles) {
        double capacity = 1.0D + 0.6D * fuelTanks;
        double hullMass = mass + thrusters * 1.0D;
        if (chambers == 0) {
            return new EngineStats(chambers, nozzles, intakes, turbines, coolingFins, oxidizer,
                    fuelTanks, vanes, totalParts, 0.0D, 0.0D, 0.0D,
                    capacity, 0.0D, hullMass, 0.45D, 0.0D);
        }

        double clampedAir = Math.max(0.0D, Math.min(1.0D, airDensity));

        // ノズル効率：0.6 ～ 2.0。羽根・装着ノズルで伸びる
        double nozzleEfficiency = Math.min(2.0D,
                0.6D + 0.16D * nozzles + 0.05D * vanes + 0.06D * mountedNozzles
                        + efficiencyBonus);
        // 燃費：タービンと羽根で改善（下限 0.25 倍）
        double economy = Math.max(0.25D, 1.0D - 0.22D * turbines - 0.02D * vanes);
        // 冷却
        double cooling = 1.0D + 0.55D * coolingFins;

        // 燃焼室ぶんの推力
        double baseThrust = chambers * 1.00D * nozzleEfficiency * thrustMul;
        // スラスターぶんの推力（装着ノズルで 1 基あたりが伸びる）
        double thrusterUnit = 0.30D * (1.0D + 0.10D * mountedNozzles);
        double thrusterThrust = thrusters * thrusterUnit * thrustMul;
        // 吸気は大気があるときだけ効く
        double intakeThrust = intakes * 0.12D * clampedAir;
        double thrust = baseThrust + thrusterThrust + intakeThrust;

        // 酸化剤があれば宇宙でも同じ推力、無ければ吸気分と 20% を失う
        double vacuumThrust = oxidizer > 0
                ? thrust
                : (baseThrust + thrusterThrust) * 0.8D;

        // 燃費（1 tick あたり）
        double fuelUse = chambers * 0.55D * economy * 0.01D;
        double heat = (chambers * 0.55D + thrusters * 0.10D) * heatMul / cooling;

        // 最高速度：ノズル・装着ノズル・羽根で伸びる。スラスターを増やすと頭打ちが緩む
        double topSpeed = Math.max(0.35D, Math.min(3.2D,
                0.45D + 0.18D * nozzles + 0.10D * mountedNozzles + 0.06D * vanes
                        + 0.08D * thrusters - 0.02D * chambers + speedBonus));

        // 設計圧力：羽根でノズルを囲みすぎると圧に耐えられない
        double designPressure = nozzles > 0 ? (double) vanes / (nozzles * 4.0D) : 0.0D;

        return new EngineStats(chambers, nozzles, intakes, turbines, coolingFins, oxidizer,
                fuelTanks, vanes, totalParts, thrust, vacuumThrust, fuelUse,
                capacity, heat, hullMass, topSpeed, designPressure);
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
