package com.astranautics.engine;

import net.minecraft.util.StringRepresentable;

/**
 * ノズルの大きさ。1 マス（ハーフ）〜超でかい（5×5）まで選べます。
 *
 * <p>大きいノズルほど推力・最高速度は伸びますが、質量と発熱も増えます。
 * 超でかい宇宙船は「大きなノズルをいくつか」＋「スラスターを複数」で組みます。</p>
 */
public enum NozzleSize implements StringRepresentable {

    /** 縦ハーフ／小型（1 マスの半分の大きさ）。 */
    HALF("half", 0.55D, 0.55D, 0.90D, 0.85D,
            "tooltip.astranautics.nozzle_size.half"),
    /** 標準 1 マス。 */
    NORMAL("normal", 1.00D, 1.00D, 1.00D, 1.00D,
            "tooltip.astranautics.nozzle_size.normal"),
    /** 2×2（大きな船向け）。 */
    LARGE("large", 2.10D, 1.70D, 1.80D, 1.35D,
            "tooltip.astranautics.nozzle_size.large"),
    /** 超でかい 5×5。超大型宇宙船の主推進。 */
    HUGE("huge", 3.20D, 3.00D, 2.80D, 1.60D,
            "tooltip.astranautics.nozzle_size.huge");

    public final String id;
    /** 推力倍率。 */
    public final double thrustMul;
    /** 最高速度への加算。 */
    public final double speedBonus;
    /** 質量倍率。 */
    public final double massMul;
    /** 発熱倍率。 */
    public final double heatMul;
    /** 説明の翻訳キー。 */
    public final String descriptionKey;

    NozzleSize(String id, double thrustMul, double speedBonus, double massMul, double heatMul,
               String descriptionKey) {
        this.id = id;
        this.thrustMul = thrustMul;
        this.speedBonus = speedBonus;
        this.massMul = massMul;
        this.heatMul = heatMul;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public NozzleSize next() {
        NozzleSize[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    /** 見た目の倍率（モデルを拡大する）。 */
    public double visualScale() {
        return switch (this) {
            case HALF -> 0.55D;
            case NORMAL -> 1.0D;
            case LARGE -> 1.85D;
            case HUGE -> 3.6D;
        };
    }

    public static NozzleSize fromState(net.minecraft.world.level.block.state.BlockState state) {
        if (state == null || !state.hasProperty(com.astranautics.block.NozzleBlock.SIZE)) {
            return NORMAL;
        }
        return state.getValue(com.astranautics.block.NozzleBlock.SIZE);
    }

    public static NozzleSize byId(String id) {
        if (id != null) {
            for (NozzleSize size : values()) {
                if (size.id.equals(id.toLowerCase(java.util.Locale.ROOT))) {
                    return size;
                }
            }
        }
        return NORMAL;
    }
}
