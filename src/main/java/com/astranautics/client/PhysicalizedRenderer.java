package com.astranautics.client;

import com.astranautics.block.PhysicalizedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * <p>姿勢は<b>回転させず</b>、物理エンジンが計算したオフセット（前 tick → 今 tick を補間）だけを
 * 平行移動として適用します。ブロック更新を使わないため、描画コストはほぼ普通のブロックと同じです。</p>
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

        // 前 tick と今 tick のオフセットを補間（軽量・回転なし）
        double x = Mth.lerp(partialTick, be.prevOffX, be.getOffX());
        double y = Mth.lerp(partialTick, be.prevOffY, be.getOffY());
        double z = Mth.lerp(partialTick, be.prevOffZ, be.getOffZ());

        boolean moving = Math.abs(x) > 1.0E-4D || Math.abs(y) > 1.0E-4D || Math.abs(z) > 1.0E-4D;

        pose.pushPose();
        pose.translate(x, y, z);
        // 物理演算 OFF（レバー下）のときは元の位置に固定表示

        if (moving) {
            // 動いている間だけ発光させて「物理演算中」を分かりやすくする
            this.dispatcher.renderSingleBlock(original, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
        } else {
            this.dispatcher.renderSingleBlock(original, pose, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        }

        pose.popPose();
    }
}
