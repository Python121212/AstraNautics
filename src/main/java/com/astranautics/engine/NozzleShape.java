package com.astranautics.engine;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * ノズルの形状。プレイヤーが自分で選べます（設置後に素手で右クリックで切替）。
 *
 * <p>大きさ（{@link NozzleSize}）と組み合わせて、
 * 1 マス〜超でかいノズルまで作れます。</p>
 *
 * <ul>
 *   <li>整形 … ベル／ロングベル／コーン（末広がり）／エアロスパイク／ベクター</li>
 *   <li>向き … 水平／斜め（噴射方向が角度を持つ。どの方向にも置ける）</li>
 * </ul>
 */
public enum NozzleShape implements StringRepresentable {

    /** ベル：標準。バランス型。 */
    BELL("bell", true, false, 1.00D, 0.00D, 0.00D, 1.00D, 1.00D, true,
            "tooltip.astranautics.nozzle_shape.bell"),
    /** ロングベル：高効率・低速。 */
    LONG_BELL("long_bell", true, false, 1.10D, 0.10D, -0.04D, 0.95D, 1.15D, true,
            "tooltip.astranautics.nozzle_shape.long_bell"),
    /** コーン：末広がり。軽くて最高速重視。 */
    CONE("cone", true, false, 0.90D, -0.05D, 0.14D, 1.05D, 0.85D, true,
            "tooltip.astranautics.nozzle_shape.cone"),
    /** エアロスパイク：大気中で最強。宇宙でも普通。 */
    AEROSPIKE("aerospike", true, false, 1.05D, 0.05D, 0.06D, 1.20D, 1.30D, true,
            "tooltip.astranautics.nozzle_shape.aerospike"),
    /** ベクター：旋回が速い（ジンバル）。 */
    VECTOR("vector", true, false, 0.95D, 0.02D, 0.08D, 1.10D, 1.10D, true,
            "tooltip.astranautics.nozzle_shape.vector"),
    /** 斜めノズル：噴射方向が斜め。横付けの姿勢制御・斜め推力を生む。 */
    ANGLED("angled", false, true, 0.85D, 0.03D, 0.05D, 1.05D, 1.05D, false,
            "tooltip.astranautics.nozzle_shape.angled"),
    /** 斜めロング：斜め＋高効率。 */
    ANGLED_LONG("angled_long", false, true, 0.95D, 0.12D, -0.02D, 1.00D, 1.20D, false,
            "tooltip.astranautics.nozzle_shape.angled_long"),
    /** 斜めコーン：斜め＋高速。 */
    ANGLED_CONE("angled_cone", false, true, 0.80D, -0.04D, 0.16D, 1.10D, 0.90D, false,
            "tooltip.astranautics.nozzle_shape.angled_cone");

    public final String id;
    /** まっすぐ下向き（通常のブロック形状）か。false なら斜め。 */
    public final boolean straight;
    /** 斜めモデルか。 */
    public final boolean angled;
    /** 推力倍率。 */
    public final double thrustMul;
    /** 効率への加算。 */
    public final double efficiencyBonus;
    /** 最高速度への加算。 */
    public final double speedBonus;
    /** 発熱倍率。 */
    public final double heatMul;
    /** 質量倍率。 */
    public final double massMul;
    /** プレイヤーがクラフト／選択できるか。 */
    public final boolean craftable;
    /** 説明の翻訳キー。 */
    public final String descriptionKey;

    NozzleShape(String id, boolean straight, boolean angled, double thrustMul,
                double efficiencyBonus, double speedBonus, double heatMul, double massMul,
                boolean craftable, String descriptionKey) {
        this.id = id;
        this.straight = straight;
        this.angled = angled;
        this.thrustMul = thrustMul;
        this.efficiencyBonus = efficiencyBonus;
        this.speedBonus = speedBonus;
        this.heatMul = heatMul;
        this.massMul = massMul;
        this.craftable = craftable;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    /** 次の形状（素手右クリックで順に切り替え）。 */
    public NozzleShape next() {
        NozzleShape[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    /** モデル名の接尾辞（straight/bell は既存の engine_nozzle を使い回す）。 */
    public String modelSuffix() {
        return this == BELL ? "" : "_" + this.id;
    }

    /** ブロックステートから形状を読む（ノズル以外は BELL）。 */
    public static NozzleShape fromState(BlockState state) {
        if (state == null || !state.hasProperty(com.astranautics.block.NozzleBlock.SHAPE)) {
            return BELL;
        }
        return state.getValue(com.astranautics.block.NozzleBlock.SHAPE);
    }

    /** ID から取得。 */
    public static NozzleShape byId(String id) {
        if (id == null) {
            return BELL;
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (NozzleShape shape : values()) {
            if (shape.id.equals(key)) {
                return shape;
            }
        }
        return BELL;
    }
}
