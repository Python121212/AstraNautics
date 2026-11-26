package com.astranautics.client;

import com.astranautics.block.PhysicalizedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 物理化されたブロックのレンダラー。
 *
 * <p>元ブロックの見た目をそのまま描画しつつ、ふわふわ浮遊・ゆっくり回転する
 * 「物理演算っぽい」アニメーションを加えます。</p>
 */
public class PhysicalizedRenderer implements BlockEntityRenderer<PhysicalizedBlockEntity> {

    private final BlockRenderDispatcher dispatcher;

    public PhysicalizedRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PhysicalizedBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {

        BlockState original = be.getOriginalState();
        if (original == null || original.isAir() || original.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }

        float time = be.age + partialTick;
        // ブロックごとに位相をずらして「個体差」を出す
        float phase = Math.abs(be.getBlockPos().hashCode() % 628) / 100.0F;

        // 浮遊（上下）
        float bob = Mth.sin((time * 0.06F) + phase) * 0.09F;
        // 回転（Y 軸をゆっくり、X/Z はわずかに揺れる）
        float yaw = (time * 1.2F) + Mth.sin(phase) * 360.0F;
        float tiltX = Mth.sin((time * 0.045F) + phase) * 7.0F;
        float tiltZ = Mth.cos((time * 0.038F) + phase * 1.3F) * 7.0F;
        // スケール（呼吸するように僅かに拡縮）
        float scale = 0.72F + Mth.sin((time * 0.05F) + phase) * 0.02F;

        pose.pushPose();
        // ブロック中心へ移動
        pose.translate(0.5D, 0.5D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(tiltX));
        pose.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        pose.scale(scale, scale, scale);
        pose.translate(-0.5D, -0.5D, -0.5D);

        // 発光しているように明るく見せる（0xF000F0 = 最大光量）
        this.dispatcher.renderSingleBlock(original, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);

        pose.popPose();
    }
}
