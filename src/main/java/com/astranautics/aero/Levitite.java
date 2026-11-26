package com.astranautics.aero;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * レビタイト（浮く岩）。Create Aeronautics の Levitite と同じ役割です。
 *
 * <p>熱気を使わずに<b>常時</b>浮力を出しますが、<b>上昇はできません</b>
 * （上昇はノズル／熱気の仕事）。低速では抵抗が大きく、速度が乗ると軽くなります。</p>
 *
 * <p>1 個あたり 2.0 kpg の浮力。重い船体の「底上げ」に使います。</p>
 */
public final class Levitite {

    /** 1 個あたりの浮力（質量 2.0 ぶん）。 */
    public static final double LIFT_PER_BLOCK = 2.0D;
    /** 低速時の余計な抵抗（速度が乗ると減る）。 */
    public static final double LOW_SPEED_DRAG = 0.06D;
    /** 抵抗が消える速度。 */
    public static final double DRAG_FADE_SPEED = 0.35D;

    private Levitite() {
    }

    /** 1 種類（通常／真珠）の浮力。 */
    public record Result(int normal, int pearlescent, double lift) {
        public static final Result EMPTY = new Result(0, 0, 0.0D);

        public int total() {
            return this.normal + this.pearlescent;
        }

        public boolean floats() {
            return this.lift > 0.0D;
        }
    }

    /** セル群からレビタイトを数える。 */
    public static Result scan(List<BlockPos> cells, List<BlockState> states) {
        int normal = 0;
        int pearlescent = 0;
        for (BlockState state : states) {
            if (state.getBlock() instanceof com.astranautics.block.LevititeBlock levitite) {
                if (levitite.pearlescent()) {
                    pearlescent++;
                } else {
                    normal++;
                }
            }
        }
        double lift = normal * LIFT_PER_BLOCK + pearlescent * LIFT_PER_BLOCK * 1.5D;
        return new Result(normal, pearlescent, lift);
    }

    /** 速度に応じた抵抗（低速ほど重い）。 */
    public static double dragFor(double speed, Result result) {
        if (!result.floats()) {
            return 1.0D;
        }
        double t = Math.min(1.0D, speed / DRAG_FADE_SPEED);
        double penalty = LOW_SPEED_DRAG * (1.0D - t) * Math.min(4.0D, result.total() * 0.25D);
        return 1.0D - penalty;
    }
}
