package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.IsoCanvas;
import com.astranautics.client.ponder.PonderContext;
import com.astranautics.client.ponder.PonderScene;
import com.astranautics.client.ponder.PonderTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 舵輪の Ponder シーン。
 *
 * <pre>
 *   1. 接着＋物理化した船体がある
 *   2. 舵輪を載せる
 *   3. 右クリックで搭乗して航行開始（船体が動き出す）
 * </pre>
 */
public final class HelmSteps {

    private static final ResourceLocation HELM =
            new ResourceLocation("astranautics", "block/ship_helm_top");
    private static final ResourceLocation HULL =
            new ResourceLocation("minecraft", "block/oak_planks");

    private HelmSteps() {
    }

    public static PonderScene create() {
        return new PonderScene(
                Component.translatable("ponder.astranautics.helm.title"),
                new ResourceLocation("astranautics", "block/ship_helm_top"),
                240, PonderContext.ItemKind.PHYSICALIZER, HelmSteps::render,
                PonderTheme.SHIP_HELM)
                .text(Component.translatable("ponder.astranautics.helm.1"))
                .text(Component.translatable("ponder.astranautics.helm.2"))
                .text(Component.translatable("ponder.astranautics.helm.3"))
                .text(Component.translatable("ponder.astranautics.helm.4"));
    }

    private static void render(PonderContext context, IsoCanvas canvas) {
        int tick = PhysicalizerSteps.CURRENT_TICK;
        canvas.background(PonderTheme.SHIP_HELM.sceneTop(), PonderTheme.SHIP_HELM.sceneBottom());

        IsoCanvas.Sampler helm = context.blockTexture(HELM);
        IsoCanvas.Sampler hull = context.blockTexture(HULL);

        double place = PonderContext.smooth(PonderContext.progress(tick, 40, 70));
        double board = PonderContext.smooth(PonderContext.progress(tick, 110, 140));
        double fly = PonderContext.smooth(PonderContext.progress(tick, 150, 235));

        canvas.shadow(-fly * 1.4D, 0.02D, 0.0D, 1.7D, 0.5D);

        // 船体（3x2 の板）
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 1; z++) {
                canvas.cube(-fly * 1.4D - 1.0D + x * 1.02D, 0.5D, z * 1.02D, 1.0D,
                        hull, hull, hull, 1.0D, 0.76D, 0.54D);
            }
        }
        // 舵輪
        double helmY = 1.05D + (1.0D - place) * 1.5D;
        canvas.cube(-fly * 1.4D - 1.0D, helmY + 0.45D, 0.0D, 0.95D, helm, helm, helm,
                1.0D, 0.8D, 0.58D);

        // 設置のきらめき
        if (tick < 78) {
            GenericSteps.spin(canvas, tick, 40, 78, helmY + 0.5D, PonderTheme.SHIP_HELM.accent());
        }

        // 搭乗（プレイヤーっぽい人影）
        if (board > 0.02D) {
            double px = -fly * 1.4D - 1.0D;
            canvas.cube(px, 1.35D + 0.1D * Math.sin(tick * 0.2D), 0.0D, 0.42D,
                    (u, v) -> 0xFF3A6EA8, (u, v) -> 0xFF2E5A88, (u, v) -> 0xFF244A70,
                    1.0D, 0.8D, 0.6D);
            canvas.cube(px, 1.72D + 0.1D * Math.sin(tick * 0.2D), 0.0D, 0.34D,
                    (u, v) -> 0xFFE8C090, (u, v) -> 0xFFD8B080, (u, v) -> 0xFFC8A070,
                    1.0D, 0.85D, 0.65D);
        }

        // 航行開始：前方向の光
        if (fly > 0.02D) {
            for (int i = 0; i < 6; i++) {
                double x = -fly * 1.4D + 1.1D + i * 0.35D;
                canvas.line(new double[]{x, 0.75D, 0.0D}, new double[]{x + 0.2D + fly * 0.6D, 0.75D, 0.0D},
                        ((int) (180 * fly) << 24) | (PonderTheme.SHIP_HELM.accent() & 0xFFFFFF));
            }
        }
    }
}
