package com.astranautics.engine;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 自作エンジンの部品の種類。
 *
 * <p>連結した部品を 1 基のエンジンとして評価します（{@link EngineEvaluator}）。
 * 部品ごとに「何を増やすか」が違うので、積み方で性能が変わります。</p>
 */
public enum EnginePart {

    /** 燃焼室：推力の源。増やすと推力 ↑ だが燃費と発熱も ↑。 */
    COMBUSTION_CHAMBER("combustion_chamber", 1.00D, 0.0D, 0.0D, 0.0D, 0.55D, 0.0D),
    /** ノズル：推力をまとめる。多いほど効率が上がる（上限あり）。 */
    NOZZLE("nozzle", 0.0D, 0.16D, 0.0D, 0.0D, 0.0D, 0.12D),
    /** ノズルの羽根：ノズルの周りを囲むと推力が伸び、最高速度が上がる。 */
    NOZZLE_VANE("nozzle_vane", 0.02D, 0.05D, 0.02D, 0.02D, 0.0D, 0.04D),
    /** 吸気口：大気中で推力を追加。宇宙（大気 0）では効果なし。 */
    INTAKE("intake", 0.12D, 0.0D, 0.0D, 0.0D, 0.0D, 0.10D),
    /** タービン：燃費を改善（推力は少し下がる）。 */
    TURBINE("turbine", 0.0D, -0.05D, 0.22D, 0.0D, 0.0D, -0.05D),
    /** 冷却フィン：過熱を抑える。 */
    COOLING_FIN("cooling_fin", 0.0D, 0.0D, 0.0D, 0.55D, -0.10D, 0.0D),
    /** 酸化剤タンク：宇宙でも燃焼できる（大気依存をなくす）。 */
    OXIDIZER_TANK("oxidizer_tank", 0.0D, 0.0D, 0.05D, 0.0D, -0.05D, 0.0D),
    /** 燃料タンク：容量（航続距離）を増やす。 */
    FUEL_TANK("fuel_tank", 0.0D, 0.0D, 0.0D, 0.0D, -0.05D, 0.0D);

    /** 登録 ID のパス。 */
    public final String id;
    private final double thrust;      // 推力への加算
    private final double efficiency;  // ノズル効率への加算
    private final double economy;     // 燃費改善への加算
    private final double cooling;     // 冷却への加算
    private final double heat;        // 発熱への加算
    private final double airThrust;   // 大気があるときの追加推力

    EnginePart(String id, double thrust, double efficiency, double economy,
               double cooling, double heat, double airThrust) {
        this.id = id;
        this.thrust = thrust;
        this.efficiency = efficiency;
        this.economy = economy;
        this.cooling = cooling;
        this.heat = heat;
        this.airThrust = airThrust;
    }

    public double thrustPerBlock() {
        return this.thrust;
    }

    public double efficiencyPerBlock() {
        return this.efficiency;
    }

    public double economyPerBlock() {
        return this.economy;
    }

    public double coolingPerBlock() {
        return this.cooling;
    }

    public double heatPerBlock() {
        return this.heat;
    }

    public double airThrustPerBlock() {
        return this.airThrust;
    }

    /** 質量（重い部品ほど船が鈍くなる）。 */
    public double mass() {
        return switch (this) {
            case COMBUSTION_CHAMBER -> 3.0D;
            case OXIDIZER_TANK, FUEL_TANK -> 2.5D;
            case NOZZLE -> 1.5D;
            case TURBINE -> 2.0D;
            case COOLING_FIN -> 1.0D;
            case INTAKE -> 1.2D;
            case NOZZLE_VANE -> 0.8D;
        };
    }

    /** ブロックステートから部品種別を判定（無ければ null）。 */
    public static EnginePart fromState(BlockState state) {
        if (state == null) {
            return null;
        }
        for (EnginePart part : values()) {
            if (state.is(com.astranautics.registry.ModBlocks.enginePart(part))) {
                return part;
            }
        }
        return null;
    }
}
