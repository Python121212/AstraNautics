package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 物理化ブロックの状態を示す「レバー」のオーバーレイ描画。
 *
 * <p>`images/lever_on.png` / `lever_off.png` を元にした 16x16 テクスチャを、
 * ブロック上面に立てた 2 枚の板（クロス）として描画します。モデルを追加しないので軽量です。</p>
 */
public final class LeverOverlayRenderer {

    private static final ResourceLocation LEVER_ON =
            new ResourceLocation(AstraNautics.MOD_ID, "textures/block/lever_on.png");
    private static final ResourceLocation LEVER_OFF =
            new ResourceLocation(AstraNautics.MOD_ID, "textures/block/lever_off.png");

    private static Boolean available;

    private LeverOverlayRenderer() {
    }

    /** テクスチャが存在するか（存在しない場合は描画しない）。 */
    public static boolean isAvailable() {
        if (available == null) {
            var manager = Minecraft.getInstance().getResourceManager();
            available = manager.getResource(LEVER_ON).isPresent()
                    || manager.getResource(LEVER_OFF).isPresent();
        }
        return available;
    }

    /** リソースパック切り替え時に再判定できるようキャッシュを破棄する。 */
    public static void invalidate() {
        available = null;
    }

    /**
     * ブロックの上にレバーを描画する。
     */
    public static void render(PhysicalizedBlockEntity be, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int packedLight) {
        if (!isAvailable()) {
            return;
        }

        boolean powered = be.isPhysicsEnabled();
        ResourceLocation texture = powered ? LEVER_ON : LEVER_OFF;
        RenderType type = RenderType.entityCutoutNoCull(texture);
        VertexConsumer consumer = buffers.getBuffer(type);

        double x = Mth.lerp(partialTick, be.prevOffX, be.getOffX());
        double y = Mth.lerp(partialTick, be.prevOffY, be.getOffY());
        double z = Mth.lerp(partialTick, be.prevOffZ, be.getOffZ());

        pose.pushPose();
        pose.translate(x + 0.5D, y + 1.0D, z + 0.5D);
        // レバーのサイズ（ブロックの 55%）
        float half = 0.275F;
        float height = 0.55F;
        PoseStack.Pose entry = pose.last();

        // 板 1（XZ 方向に立てる）
        quad(entry, consumer, -half, 0.0F, -half, half, 0.0F, half, packedLight,
                new float[]{0.0F, 1.0F, 0.0F});
        // 板 2（90 度回した板）
        quad(entry, consumer, -half, 0.0F, 0.0F, half, 0.0F, 0.0F, packedLight,
                new float[]{1.0F, 0.0F, 0.0F});

        pose.popPose();
    }

    /**
     * 1 枚の板を張る。
     *
     * @param alongX X 方向に広げるか（true）、Z 方向に広げるか（false）
     */
    private static void quad(PoseStack.Pose entry, VertexConsumer consumer,
                             float min, float minY, float minZ, float max, float maxY, float maxZ,
                             int light, float[] normal) {
        boolean alongX = normal[2] != 0.0F;
        // 水平方向の座標（2 枚目は 45 度回す代わりに縦板を直交させる）
        float ax, az, bx, bz;
        if (alongX) {
            ax = -0.275F;
            az = 0.0F;
            bx = 0.275F;
            bz = 0.0F;
        } else {
            ax = 0.0F;
            az = -0.275F;
            bx = 0.0F;
            bz = 0.275F;
        }
        float y0 = 0.0F;
        float y1 = 0.55F;

        float nx = alongX ? 0.0F : 1.0F;
        float nz = alongX ? 1.0F : 0.0F;

        // 表
        vertex(entry, consumer, ax, y1, az, 0.0F, 0.0F, nx, ny(normal), nz, light);
        vertex(entry, consumer, bx, y1, bz, 1.0F, 0.0F, nx, ny(normal), nz, light);
        vertex(entry, consumer, bx, y0, bz, 1.0F, 1.0F, nx, ny(normal), nz, light);
        vertex(entry, consumer, ax, y0, az, 0.0F, 1.0F, nx, ny(normal), nz, light);
        // 裏（反転）
        vertex(entry, consumer, ax, y0, az, 0.0F, 1.0F, -nx, ny(normal), -nz, light);
        vertex(entry, consumer, bx, y0, bz, 1.0F, 1.0F, -nx, ny(normal), -nz, light);
        vertex(entry, consumer, bx, y1, bz, 1.0F, 0.0F, -nx, ny(normal), -nz, light);
        vertex(entry, consumer, ax, y1, az, 0.0F, 0.0F, -nx, ny(normal), -nz, light);
    }

    private static float ny(float[] normal) {
        return normal[1];
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
