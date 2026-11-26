package com.astranautics.engine;

import net.minecraft.network.chat.Component;

/**
 * 1 基のエンジンの評価結果。
 *
 * <p>「プレイヤーが部品を積んで作る」エンジンの性能を表します。
 * 推力・燃費・発熱・大気依存に加えて、<b>最高速度</b>と
 * <b>設計圧力</b>（囲みすぎると壊れる）まで扱います。</p>
 */
public record EngineStats(
        int chambers,
        int nozzles,
        int intakes,
        int turbines,
        int coolingFins,
        int oxidizerTanks,
        int fuelTanks,
        int vanes,            // ノズルの羽根
        int totalParts,
        double thrust,        // 大気中での推力（ブロック/tick^2）
        double vacuumThrust,  // 宇宙（大気 0）での推力
        double fuelUse,       // 1 tick あたりの燃料消費
        double fuelCapacity,  // 燃料容量（航行時間の目安）
        double heat,          // 発熱（1.0 を超えると過熱）
        double mass,          // エンジン部分の質量
        double topSpeed,      // このエンジンで出せる最高速度（ブロック/tick）
        double designPressure // 設計圧力（1.0 超で損傷リスク）
) {

    public boolean isEmpty() {
        return this.totalParts == 0;
    }

    /** 大気中の推力効率（1.0 以上で宇宙より強い）。 */
    public double airBonus() {
        return this.vacuumThrust <= 0.0D ? 1.0D : this.thrust / this.vacuumThrust;
    }

    /** 過熱するか。 */
    public boolean overheats() {
        return this.heat > 1.0D;
    }

    /** 宇宙で使えるか（酸化剤があるか、または推力が十分か）。 */
    public boolean worksInSpace() {
        return this.oxidizerTanks > 0 || this.vacuumThrust > 0.0D;
    }

    /** 羽根でノズルを囲えているか（囲みが有効）。 */
    public boolean vaneBoosted() {
        return this.vanes >= 2 && this.nozzles > 0;
    }

    /** 設計圧力が高すぎる（羽根で囲みすぎ）。 */
    public boolean overStressed() {
        return this.designPressure > 1.0D;
    }

    /** 表示用の要約（1 行）。 */
    public Component summary() {
        return Component.translatable("engine.astranautics.summary",
                String.format("%.2f", this.thrust),
                String.format("%.2f", this.vacuumThrust),
                String.format("%.0f", this.heat * 100.0D),
                String.format("%.2f", this.topSpeed));
    }

    /** 積み方の評価コメント（設計のヒント）。 */
    public Component advice() {
        if (this.chambers == 0) {
            return Component.translatable("engine.astranautics.advice.no_chamber");
        }
        if (this.nozzles == 0) {
            return Component.translatable("engine.astranautics.advice.no_nozzle");
        }
        if (this.overStressed()) {
            return Component.translatable("engine.astranautics.advice.over_stress");
        }
        if (this.overheats()) {
            return Component.translatable("engine.astranautics.advice.overheat");
        }
        if (!this.worksInSpace()) {
            return Component.translatable("engine.astranautics.advice.no_oxidizer");
        }
        if (this.vanes == 0) {
            return Component.translatable("engine.astranautics.advice.no_vane");
        }
        if (this.coolingFins == 0) {
            return Component.translatable("engine.astranautics.advice.no_cooling");
        }
        if (this.turbines == 0) {
            return Component.translatable("engine.astranautics.advice.no_turbine");
        }
        return Component.translatable("engine.astranautics.advice.good");
    }

    /** 空のエンジン。 */
    public static final EngineStats EMPTY = new EngineStats(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 0.4D, 0.0D);
}
