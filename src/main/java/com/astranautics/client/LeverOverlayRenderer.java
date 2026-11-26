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
 * <p>台座（真鍮の立方体）＋ 持ち手（回転する角柱）で構成します。
 * レバー ON では持ち手が立ち上がり、OFF では横倒しになります。
 * テクスチャは `images/lever_base.png` / `lever_handle_on.png` / `lever_handle_off.png`。</p>
 */
public final class LeverOverlayRenderer {

    private static final ResourceLocation BASE =
            new ResourceLocation(AstraNautics.MOD_ID, "textures/block/lever_base.png");
    private static final ResourceLocation HANDLE_ON =
            new ResourceLocation(AstraNautics.MOD_ID, "textures/block/lever_handle_on.png");
    private static final ResourceLocation HANDLE_OFF =
            new ResourceLocation(AstraNautics.MOD_ID, "textures/block/lever_handle_off.png");

    private static Boolean available;

    private LeverOverlayRenderer() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            var manager = Minecraft.getInstance().getResourceManager();
            available = manager.getResource(BASE).isPresent()
                    && manager.getResource(HANDLE_ON).isPresent();
        }
        return available;
    }

    public static void invalidate() {
        available = null;
    }

    /**
     * レバー本体を描画する。
     * ブロックの上面中央に台座を置き、その上で持ち手を回転させる。
     */
    public static void render(PhysicalizedBlockEntity be, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int packedLight) {
        if (!isAvailable()) {
            return;
        }

        double x = Mth.lerp(partialTick, be.prevOffX, be.getOffX());
        double y = Mth.lerp(partialTick, be.prevOffY, be.getOffY());
        double z = Mth.lerp(partialTick, be.prevOffZ, be.getOffZ());

        // ON / OFF の角度を滑らかに補間（0 = 立ち上がり, 80度 = 横倒し）
        float target = be.isPhysicsEnabled() ? 0.0F : 80.0F;
        be.leverTilt += (target - be.leverTilt) * 0.35F;
        if (be.leverTilt < 0.01F && target == 0.0F) {
            be.leverTilt = 0.0F;
        }

        int light = be.isPhysicsEnabled()
                ? LightTexture.FULL_BRIGHT     // ON は光って見える
                : packedLight;

        pose.pushPose();
        pose.translate(x + 0.5D, y + 1.0D, z + 0.5D);

        // --- 台座（真鍮の小さな立方体） ---
        VertexConsumer base = buffers.getBuffer(RenderType.entityCutout(BASE));
        pose.pushPose();
        pose.translate(0.0D, 0.045D, 0.0D);
        box(pose.last(), base, 0.22F, 0.09F, 0.22F, light);
        pose.popPose();

        // --- 持ち手（回転する角柱） ---
        VertexConsumer handle = buffers.getBuffer(RenderType.entityCutout(
                be.isPhysicsEnabled() ? HANDLE_ON : HANDLE_OFF));
        pose.pushPose();
        pose.translate(0.0D, 0.09D, 0.0D);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(be.leverTilt));
        // 根元から上へ伸びる棒
        pose.translate(0.0D, 0.30D, 0.0D);
        box(pose.last(), handle, 0.055F, 0.30F, 0.055F, light);
        // ノブ（握り）
        pose.translate(0.0D, 0.34D, 0.0D);
        box(pose.last(), handle, 0.10F, 0.09F, 0.10F, light);
        pose.popPose();

        pose.popPose();
    }

    /* ------------------------------------------------------------------ */

    /** 中心原点・指定サイズの直方体を 6 面描く（テクスチャは面ごとに UV 全体）。 */
    private static void box(PoseStack.Pose entry, VertexConsumer consumer,
                            float halfX, float halfY, float halfZ, int light) {
        float x0 = -halfX, x1 = halfX;
        float y0 = -halfY, y1 = halfY;
        float z0 = -halfZ, z1 = halfZ;

        // 上
        quad(entry, consumer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, light);
        // 下
        quad(entry, consumer, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0, light);
        // 北 (-Z)
        quad(entry, consumer, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, light);
        // 南 (+Z)
        quad(entry, consumer, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, light);
        // 西 (-X)
        quad(entry, consumer, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, light);
        // 東 (+X)
        quad(entry, consumer, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, light);
    }

    private static void quad(PoseStack.Pose entry, VertexConsumer consumer,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz,
                             float nx, float ny, float nz, int light) {
        vertex(entry, consumer, ax, ay, az, 0.0F, 1.0F, nx, ny, nz, light);
        vertex(entry, consumer, bx, by, bz, 0.0F, 0.0F, nx, ny, nz, light);
        vertex(entry, consumer, cx, cy, cz, 1.0F, 0.0F, nx, ny, nz, light);
        vertex(entry, consumer, dx, dy, dz, 1.0F, 1.0F, nx, ny, nz, light);
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
