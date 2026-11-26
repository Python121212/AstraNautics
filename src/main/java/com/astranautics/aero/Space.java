package com.astranautics.aero;

import net.minecraft.world.level.Level;

/**
 * 宇宙空間の判定。
 *
 * <p>この MOD の船は「宇宙船・宇宙戦艦」を想定しているので、
 * 大気が無い（＝空気抵抗なし・吸気ノズル無効・酸化剤必須）領域を一箇所で判定します。</p>
 *
 * <ul>
 *   <li>エンド：ほぼ真空</li>
 *   <li>オーバーワールド：Y 200 以上で真空に近づく</li>
 *   <li>ネザー：薄い大気（0.35）</li>
 * </ul>
 */
public final class Space {

    /** 真空とみなす大気密度のしきい値。 */
    public static final double VACUUM = 0.10D;

    private Space() {
    }

    /** そのディメンションが宇宙か（エンド）。 */
    public static boolean isSpaceDimension(Level level) {
        return level.dimension() == Level.END;
    }

    /** その位置の大気密度（0..1）。 */
    public static double airDensity(Level level, double y) {
        if (level.dimension() == Level.END) {
            return 0.02D;
        }
        if (level.dimension() == Level.NETHER) {
            return 0.35D;
        }
        if (y > 200.0D) {
            // 高度 200 以上は急激に薄くなる（実質宇宙）
            return Math.max(0.0D, 1.0D - (y - 200.0D) / 120.0D);
        }
        return Math.min(1.0D, 0.55D + y / 400.0D);
    }

    /** 宇宙空間か（大気がほぼ無い）。 */
    public static boolean isVacuum(Level level, double y) {
        return airDensity(level, y) <= VACUUM;
    }

    /** 宇宙では空気抵抗がほぼ無い。 */
    public static double drag(Level level, double y) {
        return isVacuum(level, y) ? 0.9995D : 0.985D;
    }
}
