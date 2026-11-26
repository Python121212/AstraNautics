package com.astranautics.client;

import com.astranautics.physics.ShipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 航行中の船のレンダラー。
 *
 * <p>船体はエンティティのセルとして保持されているので、セルごとに
 * ブロックを描画するだけで済みます（チャンクは一切触りません）。</p>
 */
public class ShipRenderer extends EntityRenderer<ShipEntity> {

    private static final ResourceLocation PLACEHOLDER =
            new ResourceLocation("astranautics", "textures/block/physics_core.png");

    private final BlockRenderDispatcher dispatcher;

    public ShipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(ShipEntity ship, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        super.render(ship, yaw, partialTick, pose, buffers, packedLight);
        if (ship.cells().isEmpty()) {
            return;
        }

        pose.pushPose();
        // エンティティ位置から前 tick の位置を引いた「補間済みの移動量」を打ち消す
        Vec3 lerp = ship.getPosition(partialTick).subtract(ship.position());
        pose.translate(-lerp.x, -lerp.y, -lerp.z);
        // 船の向き
        float bodyYaw = Mth.lerp(partialTick, ship.yRotO, ship.getYRot());
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-bodyYaw));
        // セルの原点（エンティティは船体中心にある）
        pose.translate(-0.5D, -0.5D, -0.5D);

        for (ShipEntity.Cell cell : ship.cells()) {
            BlockPos offset = cell.localOffset;
            pose.pushPose();
            pose.translate(offset.getX(), offset.getY(), offset.getZ());
            this.dispatcher.renderSingleBlock(cell.state, pose, buffers,
                    0xF000F0, OverlayTexture.NO_OVERLAY,
                    net.minecraftforge.client.model.data.ModelData.EMPTY,
                    net.minecraft.client.renderer.RenderType.translucent());
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ShipEntity entity) {
        return PLACEHOLDER;
    }

    /** 名前（デバッグ）。 */
    @Override
    protected boolean shouldShowName(ShipEntity entity) {
        return Minecraft.getInstance().options.hideGui == false && entity.hasCustomName();
    }

    /** 未使用警告避け。 */
    protected Component debugName(ShipEntity ship) {
        return Component.literal("Ship(" + ship.cellCount() + ")");
    }
}
