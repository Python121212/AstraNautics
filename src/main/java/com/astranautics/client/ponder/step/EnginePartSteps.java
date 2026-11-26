package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.IsoCanvas;
import com.astranautics.client.ponder.PonderContext;
import com.astranautics.client.ponder.PonderScene;
import com.astranautics.client.ponder.PonderTheme;
import com.astranautics.engine.EngineEvaluator;
import com.astranautics.engine.EnginePart;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * エンジン部品の Ponder シーン（部品ごとに内容が変わります）。
 *
 * <p>その部品を<b>どこに足すと何が変わるか</b>を、実物の部品と計算式の
 * 両方で見せます。シーンの配色も部品ごとに変えます。</p>
 */
public final class EnginePartSteps {

    private EnginePartSteps() {
    }

    /** 部品専用のシーン。 */
    public static PonderScene create(EnginePart part) {
        PonderTheme theme = themeFor(part);
        ResourceLocation icon = new ResourceLocation("astranautics", "block/engine_" + part.id + "_top");
        List<ResourceLocation> layout = layoutFor(part);
        var stats = sampleStats(part);

        PonderScene scene = new PonderScene(
                Component.translatable("ponder.astranautics.engine." + part.id + ".title"),
                icon, 210, PonderContext.ItemKind.NONE,
                (context, canvas) -> render(context, canvas, part, theme, layout),
                theme);

        scene.text(Component.translatable("ponder.astranautics.engine." + part.id + ".1"));
        scene.text(Component.translatable("ponder.astranautics.engine." + part.id + ".2"));
        // 部品ごとの「効き目」を数値で見せる
        scene.text(stats.summary());
        scene.text(stats.advice());
        return scene;
    }

    /** 部品ごとの配色。 */
    public static PonderTheme themeFor(EnginePart part) {
        return switch (part) {
            case COMBUSTION_CHAMBER -> PonderTheme.ENGINE_HOT;
            case NOZZLE -> PonderTheme.ENGINE;
            case INTAKE -> new PonderTheme(0xFF6FC8E8, 0xFF142A33, 0xFF0E1C22,
                    0xFF0C1A20, 0xFF18333D, 0xFFE6F7FC, 0xFFA8E0F0, 0xFF7FD0E8);
            case TURBINE -> new PonderTheme(0xFFD8B870, 0xFF2E2617, 0xFF1C180E,
                    0xFF1A160D, 0xFF362E1B, 0xFFFFF6E2, 0xFFE8D6A8, 0xFFE0C078);
            case COOLING_FIN -> new PonderTheme(0xFF9FE0E8, 0xFF1B2E31, 0xFF102022,
                    0xFF0E1C1F, 0xFF1D3538, 0xFFEAFBFC, 0xFFBEEEF2, 0xFF8FE0E8);
            case OXIDIZER_TANK -> new PonderTheme(0xFF7FB8F0, 0xFF141F33, 0xFF0D1622,
                    0xFF0B131C, 0xFF16263A, 0xFFE8F2FF, 0xFFA8CCF0, 0xFF7FB8F0);
            case FUEL_TANK -> new PonderTheme(0xFFE8A468, 0xFF33240F, 0xFF20170A,
                    0xFF1C1408, 0xFF38280F, 0xFFFFF2E4, 0xFFF0C094, 0xFFE8A468);
            case NOZZLE_VANE -> new PonderTheme(0xFFB8D0E0, 0xFF1B2730, 0xFF121A20,
                    0xFF101820, 0xFF22323D, 0xFFEAF4FA, 0xFFC0D8E8, 0xFF9AC8E0);
        };
    }

    /** その部品を含む代表的なエンジン配置（見せるためのサンプル）。 */
    private static List<ResourceLocation> layoutFor(EnginePart part) {
        List<ResourceLocation> list = new ArrayList<>();
        list.add(texture(part));
        if (part == EnginePart.NOZZLE_VANE) {
            // 羽根はノズルを囲む形で見せる
            list.add(texture(EnginePart.NOZZLE_VANE));
            list.add(texture(EnginePart.NOZZLE));
            list.add(texture(EnginePart.NOZZLE_VANE));
            list.add(texture(EnginePart.NOZZLE_VANE));
            return list;
        }
        // 燃焼室 → ノズル（最小構成のエンジン）
        list.add(texture(EnginePart.NOZZLE));
        list.add(texture(EnginePart.COMBUSTION_CHAMBER));
        list.add(texture(EnginePart.COOLING_FIN));
        return list;
    }

    private static ResourceLocation texture(EnginePart part) {
        return new ResourceLocation("astranautics", "block/engine_" + part.id + "_top");
    }

    /** その部品を 1 個足したときの性能（比較用）。 */
    private static com.astranautics.engine.EngineStats sampleStats(EnginePart part) {
        int chambers = 1;
        int nozzles = 1;
        int intakes = 0;
        int turbines = 0;
        int cooling = 0;
        int oxi = 0;
        int tanks = 0;
        int vanes = 0;
        switch (part) {
            case COMBUSTION_CHAMBER -> chambers = 2;
            case NOZZLE -> nozzles = 2;
            case NOZZLE_VANE -> vanes = 4;
            case INTAKE -> intakes = 2;
            case TURBINE -> turbines = 2;
            case COOLING_FIN -> cooling = 2;
            case OXIDIZER_TANK -> oxi = 1;
            case FUEL_TANK -> tanks = 2;
        }
        double mass = part.mass() * 2;
        return EngineEvaluator.compute(chambers, nozzles, intakes, turbines, cooling, oxi, tanks,
                vanes, 4, mass, 1.0D);
    }

    private static void render(PonderContext context, IsoCanvas canvas, EnginePart part,
                               PonderTheme theme, List<ResourceLocation> layout) {
        int tick = PhysicalizerSteps.CURRENT_TICK;
        canvas.background(theme.sceneTop(), theme.sceneBottom());

        int count = layout.size();
        double spacing = 1.02D;
        double totalWidth = (count - 1) * spacing;
        double connect = PonderContext.progress(tick, 70, 110);

        // 対象の部品を強調する（最初の 1 個）
        for (int i = 0; i < count; i++) {
            double x = -totalWidth / 2.0D + i * spacing;
            double appear = PonderContext.easeOut(PonderContext.progress(tick, 8 + i * 5, 32 + i * 5));
            double y = 1.45D + (1.0D - appear) * 1.4D + Math.sin(tick * 0.09D + i) * 0.04D;
            IsoCanvas.Sampler sampler = context.blockTexture(layout.get(i));
            canvas.cube(x, y, 0.0D, 1.0D, sampler, sampler, sampler, 1.0D, 0.76D, 0.54D);

            // 対象の部品に枠
            if (i == 0) {
                int alpha = (int) (150 + 105 * Math.sin(tick * 0.2D));
                canvas.cubeOutlineThick(x, y, 0.0D, 1.06D,
                        (Math.max(60, alpha) << 24) | (theme.accent() & 0xFFFFFF), 2.0D);
            }
            // 連結の光
            if (i > 0 && connect > 0.01D) {
                double cx = x - spacing / 2.0D;
                for (int s = 0; s <= 10; s++) {
                    double t = s / 10.0D;
                    canvas.glow(cx, y - 0.35D + t * 0.7D, 0.0D, 2.0D,
                            ((int) (190 * connect) << 24) | (theme.accent() & 0xFFFFFF));
                }
            }
        }

        // 部品の「効果」を視覚化
        double pulse = PonderContext.progress(tick, 110, 160);
        if (pulse > 0.01D && pulse < 1.0D) {
            double cx = -totalWidth / 2.0D;
            switch (part) {
                case COMBUSTION_CHAMBER -> {
                    // 熱（赤い輪）
                    for (int i = 0; i < 12; i++) {
                        double angle = i / 12.0D * Math.PI * 2.0D + tick * 0.2D;
                        canvas.glow(cx + Math.cos(angle) * (0.4D + pulse * 0.5D),
                                1.45D + Math.sin(angle) * (0.4D + pulse * 0.5D), 0.0D, 2.4D,
                                ((int) (220 * (1.0D - pulse)) << 24) | 0xFF8A5A);
                    }
                }
                case NOZZLE -> {
                    // 推力の矢印
                    for (int i = 0; i < 5; i++) {
                        double z = (i - 2) * 0.22D;
                        canvas.line(new double[]{cx - 0.9D, 1.45D + z, 0.0D},
                                new double[]{cx - 2.2D, 1.45D + z, 0.0D},
                                ((int) (200 * (1.0D - pulse)) << 24) | 0xA8E0F0);
                    }
                }
                case INTAKE -> {
                    // 吸い込む空気
                    for (int i = 0; i < 8; i++) {
                        double angle = i / 8.0D * Math.PI * 2.0D;
                        double r = 1.4D * (1.0D - pulse) + 0.2D;
                        canvas.glow(cx + Math.cos(angle) * r, 1.45D + Math.sin(angle) * r, 0.0D,
                                2.0D, ((int) (180 * (1.0D - pulse)) << 24) | 0xA8E0F0);
                    }
                }
                case TURBINE -> {
                    // 回転
                    for (int i = 0; i < 6; i++) {
                        double angle = i / 6.0D * Math.PI * 2.0D + pulse * Math.PI * 4.0D;
                        canvas.glow(cx + Math.cos(angle) * 0.42D, 1.45D + Math.sin(angle) * 0.32D,
                                0.0D, 2.2D, ((int) (200 * (1.0D - pulse)) << 24) | 0xE8D6A8);
                    }
                }
                case COOLING_FIN -> {
                    // 冷気
                    for (int i = 0; i < 7; i++) {
                        double y = 1.1D + i * 0.12D;
                        canvas.glow(cx, y + pulse * 0.5D, 0.0D, 1.8D,
                                ((int) (170 * (1.0D - pulse)) << 24) | 0xBEEEF2);
                    }
                }
                case OXIDIZER_TANK -> {
                    // 青い液体の波
                    for (int i = 0; i < 8; i++) {
                        double y = 1.2D + i * 0.06D;
                        canvas.line(new double[]{cx - 0.4D, y + Math.sin(tick * 0.2D + i) * 0.04D, 0.5D},
                                new double[]{cx + 0.4D, y + Math.cos(tick * 0.2D + i) * 0.04D, 0.5D},
                                ((int) (160 * (1.0D - pulse)) << 24) | 0xA8CCF0);
                    }
                }
                case NOZZLE_VANE -> {
                    // 噴射を整える整流の光
                    for (int i = 0; i < 6; i++) {
                        double y = 1.1D + i * 0.14D;
                        canvas.line(new double[]{cx - 0.55D, y, 0.0D},
                                new double[]{cx + 0.55D, y, 0.0D},
                                ((int) (170 * (1.0D - pulse)) << 24) | 0xC8E8F0);
                    }
                }
                case FUEL_TANK -> {
                    // 燃料が満ちる
                    for (int i = 0; i < 8; i++) {
                        double y = 1.15D + i * 0.07D * pulse;
                        canvas.line(new double[]{cx - 0.35D, y, 0.5D},
                                new double[]{cx + 0.35D, y, 0.5D},
                                ((int) (170 * (1.0D - pulse * 0.5D)) << 24) | 0xF0C094);
                    }
                }
            }
        }
    }
}
