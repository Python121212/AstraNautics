package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 物理化ブロックの真上に載る「本物の 3D レバー」。
 *
 * <p>テクスチャは <b>1 枚のアトラス PNG</b>（`images/lever_atlas.png` / 64x32）だけを使い、
 * UV を切り替えて台座と持ち手を「組み立て」ます。</p>
 *
 * <pre>
 *  レイアウト（16px セル）
 *    (0,0) 台座      u 0.00 - 0.25
 *    (1,0) 持ち手ON  u 0.25 - 0.50
 *    (2,0) 持ち手OFF u 0.50 - 0.75
 * </pre>
 */
public final class LeverOverlayRenderer {

    private static final ResourceLocation ATLAS =
            new ResourceLocation(AstraNautics.MOD_ID, "textures/block/lever_atlas.png");

    /** アトラス内の UV 範囲（u0, v0, u1, v1）。 */
    private static final float[] UV_BASE = {0.00F, 0.00F, 0.25F, 0.50F};
    private static final float[] UV_HANDLE_ON = {0.25F, 0.00F, 0.50F, 0.50F};
    private static final float[] UV_HANDLE_OFF = {0.50F, 0.00F, 0.75F, 0.50F};

    private static Boolean available;

    private LeverOverlayRenderer() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = Minecraft.getInstance().getResourceManager().getResource(ATLAS).isPresent();
        }
        return available;
    }

    public static void invalidate() {
        available = null;
    }

    /** レバー本体（台座＋持ち手＋ノブ）を描画する。 */
    public static void render(PhysicalizedBlockEntity be, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int packedLight) {
        if (!isAvailable()) {
            return;
        }

        double x = Mth.lerp(partialTick, be.prevOffX, be.getOffX());
        double y = Mth.lerp(partialTick, be.prevOffY, be.getOffY());
        double z = Mth.lerp(partialTick, be.prevOffZ, be.getOffZ());

        // ON = 立ち上がり(0度) / OFF = 横倒し(80度)。描画時のみ滑らかに補間。
        float target = be.isPhysicsEnabled() ? 0.0F : 80.0F;
        be.leverTilt += (target - be.leverTilt) * 0.35F;
        if (target == 0.0F && be.leverTilt < 0.01F) {
            be.leverTilt = 0.0F;
        }

        int light = be.isPhysicsEnabled() ? LightTexture.FULL_BRIGHT : packedLight;

        // 1 枚のアトラスから 1 つのバッファだけ取得（描画コールは 1 回）
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(ATLAS));

        pose.pushPose();
        pose.translate(x + 0.5D, y + 1.0D, z + 0.5D);

        // --- 台座 ---
        pose.pushPose();
        pose.translate(0.0D, 0.045D, 0.0D);
        box(pose.last(), consumer, UV_BASE, 0.22F, 0.09F, 0.22F, light);
        pose.popPose();

        // --- 持ち手（ON / OFF で UV を切り替え） ---
        float[] handleUv = be.isPhysicsEnabled() ? UV_HANDLE_ON : UV_HANDLE_OFF;
        pose.pushPose();
        pose.translate(0.0D, 0.09D, 0.0D);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(be.leverTilt));
        pose.translate(0.0D, 0.30D, 0.0D);
        box(pose.last(), consumer, handleUv, 0.055F, 0.30F, 0.055F, light);
        pose.translate(0.0D, 0.34D, 0.0D);
        box(pose.last(), consumer, handleUv, 0.10F, 0.09F, 0.10F, light);
        pose.popPose();

        pose.popPose();
    }

    /* ------------------------------------------------------------------ */

    /** 中心原点・指定サイズの直方体を 6 面描く（アトラスの UV 範囲を各面に貼る）。 */
    private static void box(PoseStack.Pose entry, VertexConsumer consumer, float[] uv,
                            float halfX, float halfY, float halfZ, int light) {
        float x0 = -halfX, x1 = halfX;
        float y0 = -halfY, y1 = halfY;
        float z0 = -halfZ, z1 = halfZ;

        quad(entry, consumer, uv, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, light);
        quad(entry, consumer, uv, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0, light);
        quad(entry, consumer, uv, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, light);
        quad(entry, consumer, uv, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, light);
        quad(entry, consumer, uv, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, light);
        quad(entry, consumer, uv, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, light);
    }

    private static void quad(PoseStack.Pose entry, VertexConsumer consumer, float[] uv,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz,
                             float nx, float ny, float nz, int light) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        vertex(entry, consumer, ax, ay, az, u0, v1, nx, ny, nz, light);
        vertex(entry, consumer, bx, by, bz, u0, v0, nx, ny, nz, light);
        vertex(entry, consumer, cx, cy, cz, u1, v0, nx, ny, nz, light);
        vertex(entry, consumer, dx, dy, dz, u1, v1, nx, ny, nz, light);
    }

    private static void vertex(PoseStack.Pose entry, VertexConsumer consumer,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light) {
        consumer.vertex(entry.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(entry.normal(), nx, ny, nz)
                .endVertex();
    }
}
