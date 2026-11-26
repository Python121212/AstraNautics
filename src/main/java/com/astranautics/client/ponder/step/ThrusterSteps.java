package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.IsoCanvas;
import com.astranautics.client.ponder.PonderContext;
import com.astranautics.client.ponder.PonderScene;
import com.astranautics.client.ponder.PonderTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * スラスターの Ponder シーン。
 *
 * <pre>
 *   1. 船体の横にスラスターが付く
 *   2. 点火 → ノズルから炎
 *   3. 船が前へ進む（推力の向き＝ノズルの反対）
 * </pre>
 */
public final class ThrusterSteps {

    private static final ResourceLocation THRUSTER =
            new ResourceLocation("astranautics", "block/thruster_front");
    private static final ResourceLocation HULL =
            new ResourceLocation("minecraft", "block/iron_block");

    private ThrusterSteps() {
    }

    public static PonderScene create() {
        return new PonderScene(
                Component.translatable("ponder.astranautics.thruster.title"),
                new ResourceLocation("astranautics", "block/thruster_front"),
                220, PonderContext.ItemKind.NONE, ThrusterSteps::render, PonderTheme.THRUSTER)
                .text(Component.translatable("ponder.astranautics.thruster.1"))
                .text(Component.translatable("ponder.astranautics.thruster.2"))
                .text(Component.translatable("ponder.astranautics.thruster.3"));
    }

    private static void render(PonderContext context, IsoCanvas canvas) {
        int tick = PhysicalizerSteps.CURRENT_TICK;
        canvas.background(PonderTheme.THRUSTER.sceneTop(), PonderTheme.THRUSTER.sceneBottom());

        IsoCanvas.Sampler thruster = context.blockTexture(THRUSTER);
        IsoCanvas.Sampler hull = context.blockTexture(HULL);

        double attach = PonderContext.smooth(PonderContext.progress(tick, 20, 50));
        double ignite = PonderContext.progress(tick, 90, 110);
        double fly = PonderContext.smooth(PonderContext.progress(tick, 120, 210));

        canvas.shadow(-fly * 1.2D, 0.02D, 0.0D, 1.4D, 0.5D);

        // 船体（3 ブロック）
        for (int i = 0; i < 3; i++) {
            canvas.cube(-fly * 1.2D + i * 1.02D, 0.9D, 0.0D, 1.0D, hull, hull, hull,
                    1.0D, 0.76D, 0.54D);
        }
        // スラスター（左端の後ろ）
        double tx = -fly * 1.2D - 0.95D - (1.0D - attach) * 1.2D;
        canvas.cube(tx, 0.9D, 0.0D, 1.0D, thruster, thruster, thruster,
                1.0D, 0.8D, 0.58D);

        // 炎（点火後）
        if (ignite > 0.02D) {
            double power = ignite * (1.0D + fly);
            for (int i = 0; i < 9; i++) {
                double t = i / 9.0D;
                canvas.glow(tx - 0.55D - t * (0.9D + fly * 1.6D), 0.9D + Math.sin(tick * 0.4D + i) * 0.05D,
                        0.0D, 3.6D * power * (1.0D - t * 0.7D),
                        ((int) (230 * power * (1.0D - t * 0.6D)) << 24) | 0xFFC46A);
            }
            // 地面の焦げ跡
            canvas.glow(tx - 0.8D, 0.06D, 0.0D, 6.0D * power, ((int) (90 * power) << 24) | 0x7A3A10);
        }

        // 推力の向きを示す矢印
        if (tick > 96 && tick < 140) {
            double p = PonderContext.progress(tick, 96, 140);
            double x0 = tx - 0.8D;
            double len = 0.7D + p * 0.8D;
            canvas.line(new double[]{x0, 1.1D, 0.0D}, new double[]{x0 - len, 1.1D, 0.0D},
                    ((int) (220 * (1.0D - p)) << 24) | 0xFFFFD8A0);
            canvas.line(new double[]{x0, 1.1D, 0.0D}, new double[]{x0 + 0.5D - fly, 1.1D, 0.0D},
                    ((int) (200 * (1.0D - p)) << 24) | 0x8FF0A0);
        }
    }
}
