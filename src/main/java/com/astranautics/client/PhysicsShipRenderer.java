package com.astranautics.client;

import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.ShipStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;


/**
 * Renders a physics ship by drawing every voxel with the ship's orientation.
 */
public class PhysicsShipRenderer extends EntityRenderer<PhysicsShip> {
    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation("minecraft", "textures/block/stone.png");

    public PhysicsShipRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PhysicsShip entity) {
        return FALLBACK_TEXTURE;
    }

    @Override
    public void render(PhysicsShip entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        ShipStructure structure = entity.getStructure();
        if (structure == null || structure.voxels().isEmpty()) {
            return;
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        Quaterniond orientation = entity.getRenderOrientation(partialTick);
        Vector3d centerOfMass = structure.getCenterOfMass();

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf((float) orientation.x, (float) orientation.y,
                (float) orientation.z, (float) orientation.w));

        // Only surface voxels can be seen - rendering the interior would multiply the
        // draw calls of a solid structure for pixels that are always hidden.
        for (BlockPos local : structure.surfaceVoxels()) {
            BlockState state = structure.voxels().get(local);
            if (state == null) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(
                    local.getX() + 0.5D - centerOfMass.x,
                    local.getY() + 0.5D - centerOfMass.y,
                    local.getZ() + 0.5D - centerOfMass.z);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            dispatcher.renderSingleBlock(state, poseStack, bufferSource,
                    packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
