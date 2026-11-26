package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.IsoCanvas;
import com.astranautics.client.ponder.PonderContext;
import com.astranautics.client.ponder.PonderScene;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 物理化装置の Ponder シーン。
 *
 * <pre>
 *   1. 空中に石ブロックが 1 つ浮かぶ
 *   2. 物理化装置をその石に設置
 *   3. レバーを ON にすると石が「ポン」と落ちる
 * </pre>
 */
public final class PhysicalizerSteps {

    private static final ResourceLocation STONE =
            new ResourceLocation("minecraft", "block/stone");
    private static final ResourceLocation DEVICE =
            new ResourceLocation("astranautics", "block/physicalizer");

    private PhysicalizerSteps() {
    }

    public static PonderScene create() {
        int duration = 200;
        return new PonderScene(
                Component.translatable("ponder.astranautics.physicalizer.title"),
                DEVICE, duration, PonderContext.ItemKind.PHYSICALIZER,
                PhysicalizerSteps::render)
                .text(Component.translatable("ponder.astranautics.physicalizer.1"))
                .text(Component.translatable("ponder.astranautics.physicalizer.2"))
                .text(Component.translatable("ponder.astranautics.physicalizer.3"));
    }

    private static void render(PonderContext context, IsoCanvas canvas) {
        int tick = CURRENT_TICK;
        // 背景
        canvas.background(0xFF0E1A22, 0xFF1B3340);

        IsoCanvas.Sampler stone = context.blockTexture(STONE);
        IsoCanvas.Sampler device = context.blockTexture(DEVICE);

        // ---- 石ブロック：空中に浮かぶ（y = 2.4） ----
        double settle = PonderContext.progress(tick, 0, 20);
        double floatY = 2.4D + Math.sin(tick * 0.08D) * 0.06D * (1.0D - PonderContext.progress(tick, 60, 80));

        // ---- 装置の設置（tick 60..80）----
        double install = PonderContext.smooth(PonderContext.progress(tick, 55, 78));

        // ---- レバー ON（tick 100..112）----
        double lever = PonderContext.smooth(PonderContext.progress(tick, 100, 112));

        // ---- 落下（tick 120 以降）：ポンと落ちる ----
        double drop = PonderContext.fallCurve(PonderContext.progress(tick, 122, 150));
        double bounceBack = 0.0D;
        if (tick > 150 && tick < 175) {
            double b = PonderContext.progress(tick, 150, 175);
            bounceBack = Math.sin(b * Math.PI) * 0.16D * (1.0D - b);
        }
        double stoneY = floatY - drop * 2.4D + bounceBack;

        // 地面の影
        canvas.shadow(0.0D, 0.02D, 0.0D, 0.7D - drop * 0.15D, 0.55D);

        // 石ブロック
        canvas.cube(0.0D, stoneY, 0.0D, 1.0D, stone, stone, stone, 1.0D, 0.74D, 0.52D);

        // 物理化装置（石の上に乗る 0.32 角・厚み 0.10）
        double deviceY = stoneY + 0.5D + 0.05D + (1.0D - install) * 1.2D;
        if (install > 0.01D) {
            drawDevice(canvas, device, 0.0D, deviceY, 0.0D, install, lever);
            // 設置時のきらめき
            if (tick >= 55 && tick <= 78) {
                double t = PonderContext.progress(tick, 55, 78);
                for (int i = 0; i < 6; i++) {
                    double angle = i / 6.0D * Math.PI * 2.0D + tick * 0.1D;
                    double radius = 0.55D * (1.0D - t) + 0.15D;
                    canvas.glow(Math.cos(angle) * radius, deviceY + 0.1D, Math.sin(angle) * radius,
                            2.0D, ((int) (200 * (1.0D - t)) << 24) | 0xFFE9A0);
                }
            }
        }

        // レバー ON の光
        if (lever > 0.1D) {
            canvas.glow(0.0D, deviceY + 0.22D, 0.0D, 7.0D * lever,
                    ((int) (150 * lever) << 24) | 0x5FE0EA);
        }

        // 落下のパーティクル（ポン）
        if (tick > 122 && tick < 160) {
            double t = PonderContext.progress(tick, 122, 160);
            for (int i = 0; i < 8; i++) {
                double angle = i / 8.0D * Math.PI * 2.0D;
                double radius = t * 0.9D;
                canvas.glow(Math.cos(angle) * radius, 0.1D + (1.0D - t) * 0.35D,
                        Math.sin(angle) * radius, 2.2D * (1.0D - t),
                        ((int) (200 * (1.0D - t)) << 24) | 0xCFEFFF);
            }
        }

        // カーソル：装置を置く → レバーを右クリック
        if (tick >= 45 && tick < 58) {
            double[] p = canvas.project(0.0D, stoneY + 0.8D, 0.0D);
            context.drawCursor(canvas, p[0] + 26, p[1] - 10, PonderContext.Cursor.RIGHT_CLICK,
                    PonderContext.smooth(PonderContext.progress(tick, 45, 52)));
        } else if (tick >= 96 && tick < 118) {
            double[] p = canvas.project(0.0D, deviceY, 0.0D);
            context.drawCursor(canvas, p[0] + 22, p[1] - 16, PonderContext.Cursor.RIGHT_HOLD,
                    PonderContext.smooth(PonderContext.progress(tick, 96, 104)));
        }

        // ここで使った tick はシーン側から供給される
        if (settle < 1.0D) {
            // 出現時のフェード（何もしない：背景のみ）
        }
    }

    /** 物理化装置（0.32 角の台座＋レバー）を描く。 */
    static void drawDevice(IsoCanvas canvas, IsoCanvas.Sampler device,
                           double x, double y, double z, double appear, double lever) {
        double size = 0.42D;
        // 台座
        canvas.cube(x, y, z, size, device, device, device, 1.0D, 0.78D, 0.56D);
        // レバー（ON: 立ち上がり、OFF: 横倒し）
        double angle = Math.toRadians(20.0D + 70.0D * lever);
        double length = 0.34D;
        double baseY = y + size * 0.35D;
        double tipX = x + Math.sin(angle) * length;
        double tipY = baseY + Math.cos(angle) * length;
        int shaftColor = 0xFFD8B070;
        canvas.line(new double[]{x, baseY, z}, new double[]{tipX, tipY, z}, shaftColor);
        canvas.line(new double[]{x, baseY, z + 0.02D}, new double[]{tipX, tipY, z + 0.02D}, 0xFF9A6E32);
        // ノブ
        int knob = lever > 0.5D ? 0xFF7FE8F2 : 0xFF9AA6AE;
        canvas.glow(tipX, tipY, z, 3.2D, knob);
    }

    /** シーン描画時に使う tick（PonderScreen が毎フレーム設定する）。 */
    public static int CURRENT_TICK;
}
