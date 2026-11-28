package com.astranautics.client;

import com.astranautics.block.PropellerBlock;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.ShipStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
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

    /**
     * A red arrow out of every propeller, pointing the way it pushes.
     *
     * <p>A hull is a block grid, so a propeller's orientation is only visible as a texture on
     * one face; with a dozen of them bolted on it is impossible to tell which way a ship will
     * go. The arrow is drawn in the ship's own frame, so it tilts and rolls with the hull.</p>
     */
    private void renderThrustArrows(ShipStructure structure, Vector3d centerOfMass,
                                    PoseStack poseStack, MultiBufferSource bufferSource) {
        if (structure.thrusters().isEmpty() && !hasAnyPropeller(structure)) {
            return;
        }
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        for (BlockPos local : structure.surfaceVoxels()) {
            BlockState state = structure.voxels().get(local);
            if (state == null || !(state.getBlock() instanceof PropellerBlock)) {
                continue;
            }
            Direction facing = state.getValue(PropellerBlock.FACING);
            boolean powered = state.getValue(PropellerBlock.POWERED);
            double dx = facing.getStepX();
            double dz = facing.getStepZ();

            double cx = local.getX() + 0.5D - centerOfMass.x;
            double cy = local.getY() + 0.5D - centerOfMass.y;
            double cz = local.getZ() + 0.5D - centerOfMass.z;

            // Start just outside the blade, so the arrow reads as "out of this face".
            double startX = cx + dx * 0.55D;
            double startZ = cz + dz * 0.55D;
            double endX = cx + dx * 1.35D;
            double endZ = cz + dz * 1.35D;

            float r = powered ? 1.0F : 0.62F;
            float g = powered ? 0.16F : 0.10F;
            float b = powered ? 0.16F : 0.10F;
            float a = powered ? 1.0F : 0.75F;

            arrowLine(lines, matrix, startX, cy, startZ, endX, cy, endZ, r, g, b, a);
            // Two barbs make the head, in the horizontal plane, plus a small cross at the tail.
            double perpX = -dz;
            double perpZ = dx;
            arrowLine(lines, matrix, endX, cy, endZ,
                    endX - dx * 0.28D + perpX * 0.22D, cy, endZ - dz * 0.28D + perpZ * 0.22D, r, g, b, a);
            arrowLine(lines, matrix, endX, cy, endZ,
                    endX - dx * 0.28D - perpX * 0.22D, cy, endZ - dz * 0.28D - perpZ * 0.22D, r, g, b, a);
            arrowLine(lines, matrix, startX, cy - 0.2D, startZ, startX, cy + 0.2D, startZ, r, g, b, a);
        }
    }

    private static boolean hasAnyPropeller(ShipStructure structure) {
        for (BlockState state : structure.voxels().values()) {
            if (state.getBlock() instanceof PropellerBlock) {
                return true;
            }
        }
        return false;
    }

    private static void arrowLine(VertexConsumer lines, Matrix4f matrix,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  float r, float g, float b, float a) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        lines.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(nx, ny, nz).endVertex();
        lines.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(nx, ny, nz).endVertex();
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

        this.renderThrustArrows(structure, centerOfMass, poseStack, bufferSource);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
