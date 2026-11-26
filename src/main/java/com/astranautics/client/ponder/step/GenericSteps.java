package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.IsoCanvas;
import com.astranautics.client.ponder.PonderContext;
import com.astranautics.client.ponder.PonderScene;
import com.astranautics.client.ponder.PonderTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;

/**
 * 汎用の Ponder シーン生成。
 *
 * <p>「単体ブロックを置く」「部品を連結して光る」「船に組み込んで飛ぶ」など、
 * よくある見せ方をテンプレート化しています。各ブロックはこれを使って
 * 短いコードでシーンを持てます。</p>
 */
public final class GenericSteps {

    private GenericSteps() {
    }

    /** シーンのビルダー（テキストを順番に足せる）。 */
    public static final class Builder {
        private final PonderScene scene;

        private Builder(PonderScene scene) {
            this.scene = scene;
        }

        public Builder text(String key, Object... args) {
            this.scene.text(args.length == 0
                    ? Component.translatable(key)
                    : Component.translatable(key, args));
            return this;
        }

        public PonderScene build() {
            return this.scene;
        }
    }

    /** ブロック 1 つを置くだけのシーン。 */
    public static Builder singleBlock(Component title, ResourceLocation texture, PonderTheme theme,
                                      String... textKeys) {
        int duration = 140;
        PonderScene scene = new PonderScene(title, texture, duration,
                PonderContext.ItemKind.NONE,
                (context, canvas) -> {
                    int tick = PhysicalizerSteps.CURRENT_TICK;
                    canvas.background(theme.sceneTop(), theme.sceneBottom());
                    IsoCanvas.Sampler sampler = context.blockTexture(texture);
                    double rise = PonderContext.easeOut(PonderContext.progress(tick, 10, 40));
                    double y = 1.4D + (1.0D - rise) * 1.4D + Math.sin(tick * 0.08D) * 0.05D;
                    canvas.shadow(0.0D, 0.02D, 0.0D, 0.8D, 0.5D);
                    canvas.cube(0.0D, y, 0.0D, 1.0D, sampler, sampler, sampler,
                            1.0D, 0.76D, 0.54D);
                    // 登場時のきらめき
                    if (tick < 46) {
                        spin(canvas, tick, 10, 46, y, theme.accent());
                    }
                }, theme);
        Builder builder = new Builder(scene);
        for (String key : textKeys) {
            builder.text(key);
        }
        return builder;
    }

    /** 複数のブロック（部品）を連結して、エンジンとして光るシーン。 */
    public static Builder connectedParts(Component title, List<ResourceLocation> textures,
                                         PonderTheme theme, String... textKeys) {
        int duration = 200;
        PonderScene scene = new PonderScene(title, textures.isEmpty() ? null : textures.get(0),
                duration, PonderContext.ItemKind.NONE,
                (context, canvas) -> {
                    int tick = PhysicalizerSteps.CURRENT_TICK;
                    canvas.background(theme.sceneTop(), theme.sceneBottom());
                    int count = textures.size();
                    // 中央から左右に伸びる配置（連結していることが分かる形）
                    double spacing = 1.05D;
                    double totalWidth = (count - 1) * spacing;
                    double connect = PonderContext.progress(tick, 60, 100);
                    for (int i = 0; i < count; i++) {
                        double x = -totalWidth / 2.0D + i * spacing;
                        double appear = PonderContext.easeOut(PonderContext.progress(
                                tick, 8 + i * 6, 34 + i * 6));
                        double y = 1.5D + (1.0D - appear) * 1.5D + Math.sin(tick * 0.09D + i) * 0.04D;
                        IsoCanvas.Sampler sampler = context.blockTexture(textures.get(i));
                        canvas.cube(x, y, 0.0D, 1.0D, sampler, sampler, sampler,
                                1.0D, 0.76D, 0.54D);
                        // 連結部分の光
                        if (i > 0 && connect > 0.01D) {
                            double[] a = canvas.project(x - spacing / 2.0D, y, 0.0D);
                            double[] b = canvas.project(x - spacing / 2.0D, y + 0.35D, 0.0D);
                            for (int s = 0; s <= 12; s++) {
                                double t = s / 12.0D;
                                canvas.glow(a[0], a[1] + (b[1] - a[1]) * t, 0.0D, 2.2D,
                                        ((int) (200 * connect) << 24) | (theme.accent() & 0xFFFFFF));
                            }
                        }
                    }
                    // 完成時の発光
                    if (tick > 110) {
                        double t = PonderContext.progress(tick, 110, 140);
                        for (int i = 0; i < 10; i++) {
                            double angle = i / 10.0D * Math.PI * 2.0D + tick * 0.03D;
                            canvas.glow(Math.cos(angle) * (0.8D + t * 0.9D), 1.5D + t * 0.4D,
                                    Math.sin(angle) * (0.8D + t * 0.9D), 2.0D,
                                    ((int) (160 * (1.0D - t)) << 24) | (theme.accent() & 0xFFFFFF));
                        }
                    }
                }, theme);
        Builder builder = new Builder(scene);
        for (String key : textKeys) {
            builder.text(key);
        }
        return builder;
    }

    /** 船に組み込まれて動き出すシーン（ブロック → 船 → 航行）。 */
    public static Builder mountedOnShip(Component title, ResourceLocation blockTexture,
                                        ResourceLocation companionTexture, PonderTheme theme,
                                        String... textKeys) {
        int duration = 240;
        PonderScene scene = new PonderScene(title, blockTexture, duration,
                PonderContext.ItemKind.NONE,
                (context, canvas) -> {
                    int tick = PhysicalizerSteps.CURRENT_TICK;
                    canvas.background(theme.sceneTop(), theme.sceneBottom());
                    IsoCanvas.Sampler hull = context.blockTexture(companionTexture);
                    IsoCanvas.Sampler part = context.blockTexture(blockTexture);

                    // 船体（3x1 の台）
                    double install = PonderContext.smooth(PonderContext.progress(tick, 30, 60));
                    canvas.shadow(0.0D, 0.02D, 0.0D, 1.5D, 0.5D);
                    for (int i = -1; i <= 1; i++) {
                        canvas.cube(i * 1.02D, 0.5D, 0.0D, 1.0D, hull, hull, hull,
                                1.0D, 0.76D, 0.54D);
                    }
                    // 部品が上に載る
                    double y = 1.05D + (1.0D - install) * 1.6D;
                    canvas.cube(0.0D, y + 0.5D, 0.0D, 0.9D, part, part, part,
                            1.0D, 0.8D, 0.58D);

                    // 航行（船体が前へ進む）
                    double move = PonderContext.smooth(PonderContext.progress(tick, 130, 220));
                    if (move > 0.0D) {
                        // 炎（推力）
                        for (int i = 0; i < 8; i++) {
                            canvas.glow(-1.9D - move * 2.0D - i * 0.16D, 0.55D + y,
                                    0.0D, 2.4D,
                                    ((int) (220 - i * 24) << 24) | (theme.accent() & 0xFFFFFF));
                        }
                        // スピード線
                        for (int i = 0; i < 5; i++) {
                            double z = (i - 2) * 0.4D;
                            canvas.line(new double[]{1.4D - move * 2.5D, 0.5D + y * 0.0D + 0.4D + z * 0.2D, z},
                                    new double[]{2.4D - move * 2.5D, 0.5D + z * 0.2D, z},
                                    ((int) (120 * (1.0D - move)) << 24) | (theme.text() & 0xFFFFFF));
                        }
                    }
                }, theme);
        Builder builder = new Builder(scene);
        for (String key : textKeys) {
            builder.text(key);
        }
        return builder;
    }

    /** きらめき（汎用）。 */
    static void spin(IsoCanvas canvas, int tick, int start, int end, double y, int color) {
        double t = PonderContext.progress(tick, start, end);
        for (int i = 0; i < 6; i++) {
            double angle = i / 6.0D * Math.PI * 2.0D + tick * 0.1D;
            double radius = 0.75D * (1.0D - t) + 0.2D;
            canvas.glow(Math.cos(angle) * radius, y + 0.1D, Math.sin(angle) * radius,
                    2.2D, ((int) (200 * (1.0D - t)) << 24) | (color & 0xFFFFFF));
        }
    }

    /** 便利：テキスト消費者（将来の拡張用）。 */
    public static Consumer<Builder> texts(String... keys) {
        return builder -> {
            for (String key : keys) {
                builder.text(key);
            }
        };
    }
}
