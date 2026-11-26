package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.IsoCanvas;
import com.astranautics.client.ponder.PonderContext;
import com.astranautics.client.ponder.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 接着剤の Ponder シーン。
 *
 * <pre>
 *   1. 空中に 3 つのブロックが並ぶ
 *   2. マウスが出て「右クリック」→ 3 つが選択される
 *   3. またマウスが出て「左クリック」のマーク → 接着が確定
 *   4. 物理化装置を設置して ON → 3 つとも「ポン」と落ちる
 * </pre>
 */
public final class AdhesiveSteps {

    private static final ResourceLocation STONE = new ResourceLocation("minecraft", "block/stone");
    private static final ResourceLocation GLASS = new ResourceLocation("minecraft", "block/glass");
    private static final ResourceLocation PLANKS = new ResourceLocation("minecraft", "block/oak_planks");
    private static final ResourceLocation DEVICE = new ResourceLocation("astranautics", "block/physicalizer");
    private static final ResourceLocation ADHESIVE = new ResourceLocation("astranautics", "item/adhesive");

    /** 3 つのブロックの位置（横並び）。 */
    private static final double[][] BLOCKS = {
            {-1.15D, 0.0D},
            {0.0D, 0.0D},
            {1.15D, 0.0D},
    };

    private AdhesiveSteps() {
    }

    public static PonderScene create() {
        return new PonderScene(
                Component.translatable("ponder.astranautics.adhesive.title"),
                ADHESIVE, 260, PonderContext.ItemKind.ADHESIVE,
                AdhesiveSteps::render)
                .text(Component.translatable("ponder.astranautics.adhesive.1"))
                .text(Component.translatable("ponder.astranautics.adhesive.2"))
                .text(Component.translatable("ponder.astranautics.adhesive.3"))
                .text(Component.translatable("ponder.astranautics.adhesive.4"));
    }

    /** 「接着＝1 つの剛体」であることを説明する追加シーン。 */
    public static PonderScene createCombineScene() {
        return new PonderScene(
                Component.translatable("ponder.astranautics.adhesive.combine.title"),
                ADHESIVE, 180, PonderContext.ItemKind.ADHESIVE,
                (context, canvas) -> {
                    int tick = PhysicalizerSteps.CURRENT_TICK;
                    canvas.background(0xFF0E1A22, 0xFF1B3340);
                    IsoCanvas.Sampler stone = context.blockTexture(STONE);
                    double rise = PonderContext.easeOut(PonderContext.progress(tick, 20, 70));
                    double spin = tick * 0.012D;
                    canvas.shadow(0.0D, 0.02D, 0.0D, 0.9D, 0.5D);
                    for (int i = 0; i < 3; i++) {
                        double angle = spin + i * Math.PI * 2.0D / 3.0D;
                        double radius = 0.62D;
                        canvas.cube(Math.cos(angle) * radius, 1.6D + rise * 0.5D + Math.sin(tick * 0.1D + i) * 0.04D,
                                Math.sin(angle) * radius, 0.8D, stone, stone, stone,
                                1.0D, 0.74D, 0.52D);
                    }
                })
                .text(Component.translatable("ponder.astranautics.adhesive.combine.1"));
    }

    private static void render(PonderContext context, IsoCanvas canvas) {
        int tick = PhysicalizerSteps.CURRENT_TICK;
        canvas.background(0xFF0E1A22, 0xFF1B3340);

        IsoCanvas.Sampler stone = context.blockTexture(STONE);
        IsoCanvas.Sampler glass = context.blockTexture(GLASS);
        IsoCanvas.Sampler planks = context.blockTexture(PLANKS);
        IsoCanvas.Sampler device = context.blockTexture(DEVICE);
        IsoCanvas.Sampler[] samplers = {stone, glass, planks};

        // ---- 選択の進捗（右クリック 70..90 で 3 つ順にハイライト）----
        double selectProgress = PonderContext.progress(tick, 72, 96);
        // ---- 確定（左クリック 130..145）----
        double confirm = PonderContext.progress(tick, 132, 148);
        // ---- 装置の設置（165..185）----
        double install = PonderContext.smooth(PonderContext.progress(tick, 165, 185));
        // ---- レバー ON（195..207）----
        double lever = PonderContext.smooth(PonderContext.progress(tick, 195, 207));
        // ---- 3 つとも落下（215〜）----
        double drop = PonderContext.fallCurve(PonderContext.progress(tick, 215, 240));

        // 共通の浮遊
        double floatY = 2.25D + Math.sin(tick * 0.09D) * 0.05D;

        canvas.shadow(0.0D, 0.02D, 0.0D, 1.7D, 0.5D);

        for (int i = 0; i < BLOCKS.length; i++) {
            double bx = BLOCKS[i][0];
            double bz = BLOCKS[i][1];
            // 選択済みか（1 つずつ順番に）
            double cell = PonderContext.progress(tick, 72 + i * 8, 80 + i * 8);
            boolean selected = cell >= 1.0D;

            // 落下は少しずつ時間差（ポン、ポン、ポン）
            double localDrop = PonderContext.fallCurve(
                    PonderContext.progress(tick, 215 + i * 6, 240 + i * 6));
            double y = floatY - localDrop * 2.25D;

            canvas.cube(bx, y, bz, 1.0D, samplers[i], samplers[i], samplers[i],
                    1.0D, 0.74D, 0.52D);

            // 選択ハイライト（黄色の枠＋接着マーク）
            int outlineAlpha = selected ? (int) (255 * (0.55D + 0.45D * Math.sin(tick * 0.3D + i))) : 0;
            if (outlineAlpha > 0) {
                canvas.cubeOutlineThick(bx, y, bz, 1.04D,
                        (outlineAlpha << 24) | 0xFFE9A0, 2.0D);
            }
            // 接着（左クリック確定）後は緑の枠に変わる
            if (confirm > 0.01D) {
                int gluedAlpha = (int) (200 * confirm);
                canvas.cubeOutlineThick(bx, y, bz, 1.06D, (gluedAlpha << 24) | 0x7FF0A0, 2.0D);
                // ブロック同士をつなぐ接着ライン
                if (i > 0) {
                    canvas.line(new double[]{BLOCKS[i - 1][0] + 0.3D, y, bz},
                            new double[]{bx - 0.3D, y, bz},
                            ((int) (220 * confirm) << 24) | 0x7FF0A0);
                }
            }
        }

        // ---- 物理化装置（真ん中のブロックの上）----
        double centerY = floatY - PonderContext.fallCurve(PonderContext.progress(tick, 221, 246)) * 2.25D;
        double deviceY = centerY + 0.5D + 0.05D + (1.0D - install) * 1.2D;
        if (install > 0.01D) {
            PhysicalizerSteps.drawDevice(canvas, device, 0.0D, deviceY, 0.0D, install, lever);
            if (tick >= 165 && tick <= 185) {
                double t = PonderContext.progress(tick, 165, 185);
                for (int i = 0; i < 6; i++) {
                    double angle = i / 6.0D * Math.PI * 2.0D + tick * 0.1D;
                    double radius = 0.55D * (1.0D - t) + 0.15D;
                    canvas.glow(Math.cos(angle) * radius, deviceY + 0.1D, Math.sin(angle) * radius,
                            2.0D, ((int) (200 * (1.0D - t)) << 24) | 0xFFE9A0);
                }
            }
            if (lever > 0.1D) {
                canvas.glow(0.0D, deviceY + 0.22D, 0.0D, 7.0D * lever,
                        ((int) (150 * lever) << 24) | 0x5FE0EA);
            }
        }

        // ---- カーソル演出 ----
        // 右クリック（選択）：tick 60..100 でブロックの上に出る
        if (tick >= 58 && tick < 104) {
            double[] p = canvas.project(0.0D, floatY + 0.7D, 0.0D);
            context.drawCursor(canvas, p[0] + 30, p[1] - 12, PonderContext.Cursor.RIGHT_CLICK,
                    PonderContext.smooth(PonderContext.progress(tick, 58, 68)));
        }
        // 左クリック（確定）：tick 120..150 「マーク」が出る
        if (tick >= 120 && tick < 155) {
            double[] p = canvas.project(0.0D, floatY + 0.7D, 0.0D);
            context.drawCursor(canvas, p[0] + 30, p[1] - 12, PonderContext.Cursor.LEFT_CLICK,
                    PonderContext.smooth(PonderContext.progress(tick, 120, 130)));
        }
        // 装置の設置 → レバー ON
        if (tick >= 158 && tick < 172) {
            double[] p = canvas.project(0.0D, floatY + 0.9D, 0.0D);
            context.drawCursor(canvas, p[0] + 26, p[1] - 10, PonderContext.Cursor.RIGHT_CLICK,
                    PonderContext.smooth(PonderContext.progress(tick, 158, 165)));
        } else if (tick >= 188 && tick < 212) {
            double[] p = canvas.project(0.0D, deviceY, 0.0D);
            context.drawCursor(canvas, p[0] + 22, p[1] - 16, PonderContext.Cursor.RIGHT_HOLD,
                    PonderContext.smooth(PonderContext.progress(tick, 188, 196)));
        }

        // ---- 落下のパーティクル ----
        if (tick > 215 && tick < 250) {
            double t = PonderContext.progress(tick, 215, 250);
            for (int i = 0; i < 10; i++) {
                double angle = i / 10.0D * Math.PI * 2.0D;
                double radius = t * 1.3D;
                canvas.glow(Math.cos(angle) * radius, 0.1D + (1.0D - t) * 0.4D,
                        Math.sin(angle) * radius, 2.4D * (1.0D - t),
                        ((int) (200 * (1.0D - t)) << 24) | 0xCFEFFF);
            }
        }
    }

    /** デバッグ用：選択される位置（シーンと同じ座標系）。 */
    public static List<BlockPos> previewSelection() {
        return List.of(BlockPos.ZERO, BlockPos.ZERO.offset(1, 0, 0), BlockPos.ZERO.offset(-1, 0, 0));
    }
}
