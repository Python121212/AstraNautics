package com.astranautics.client;

import com.astranautics.item.AdhesiveItem;
import com.astranautics.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 接着剤の範囲選択の可視化（ワールドのワイヤーフレーム＋HUD のクリックガイド）。
 *
 * <p>Ponder 風のチュートリアルと同じ「次に何を押すか」を画面に出す演出です。</p>
 */
@Mod.EventBusSubscriber(modid = com.astranautics.AstraNautics.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AdhesiveSelectionRenderer {

    private AdhesiveSelectionRenderer() {
    }

    private static ItemStack heldAdhesive(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof AdhesiveItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof AdhesiveItem ? off : ItemStack.EMPTY;
    }

    /* ------------------------------------------------------------------ */
    /* ワールド：選択範囲のワイヤーフレーム                                  */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack stack = heldAdhesive(player);
        if (stack.isEmpty()) {
            return;
        }

        List<BlockPos> selection = AdhesiveItem.getSelectionForRender(stack);
        if (selection.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(RenderType.lines());

        long time = minecraft.level.getGameTime();
        float pulse = 0.55F + 0.45F * (float) Math.sin(time * 0.25D);

        for (BlockPos pos : selection) {
            AABB box = new AABB(pos.getX() + 0.02, pos.getY() + 0.02, pos.getZ() + 0.02,
                    pos.getX() + 0.98, pos.getY() + 0.98, pos.getZ() + 0.98);
            drawBox(pose, consumer, box.move(-camera.x, -camera.y, -camera.z),
                    1.0F, 0.85F, 0.25F, pulse);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawBox(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                AABB box, float r, float g, float b, float alpha) {
        var matrix = pose.last().pose();
        float x0 = (float) box.minX;
        float y0 = (float) box.minY;
        float z0 = (float) box.minZ;
        float x1 = (float) box.maxX;
        float y1 = (float) box.maxY;
        float z1 = (float) box.maxZ;

        // 下
        line(matrix, consumer, x0, y0, z0, x1, y0, z0, r, g, b, alpha);
        line(matrix, consumer, x1, y0, z0, x1, y0, z1, r, g, b, alpha);
        line(matrix, consumer, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
        line(matrix, consumer, x0, y0, z1, x0, y0, z0, r, g, b, alpha);
        // 上
        line(matrix, consumer, x0, y1, z0, x1, y1, z0, r, g, b, alpha);
        line(matrix, consumer, x1, y1, z0, x1, y1, z1, r, g, b, alpha);
        line(matrix, consumer, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        line(matrix, consumer, x0, y1, z1, x0, y1, z0, r, g, b, alpha);
        // 縦
        line(matrix, consumer, x0, y0, z0, x0, y1, z0, r, g, b, alpha);
        line(matrix, consumer, x1, y0, z0, x1, y1, z0, r, g, b, alpha);
        line(matrix, consumer, x1, y0, z1, x1, y1, z1, r, g, b, alpha);
        line(matrix, consumer, x0, y0, z1, x0, y1, z1, r, g, b, alpha);
    }

    private static void line(org.joml.Matrix4f matrix,
                             com.mojang.blaze3d.vertex.VertexConsumer consumer,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, float g, float b, float a) {
        consumer.vertex(matrix, x0, y0, z0).color(r, g, b, a).normal(0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0.0F, 1.0F, 0.0F).endVertex();
    }

    /* ------------------------------------------------------------------ */
    /* HUD：次に押すマウスボタンのガイド                                    */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        ItemStack stack = heldAdhesive(player);
        if (stack.isEmpty()) {
            return;
        }
        int selected = AdhesiveItem.getSelectionForRender(stack).size();
        int glued = AdhesiveItem.totalGluedBlocks(stack);

        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int cx = width / 2;
        int cy = height - 62;

        // 背景パネル
        graphics.fill(cx - 120, cy - 20, cx + 120, cy + 22, 0xB0001018);
        graphics.fill(cx - 120, cy - 20, cx + 120, cy - 19, 0xFF2AC8D2);

        String line1;
        if (selected > 0) {
            line1 = net.minecraft.network.chat.Component.translatable(
                    "hud.astranautics.adhesive_confirm", selected).getString();
            drawMouse(graphics, cx - 96, cy - 2, MouseButton.LEFT, true);
        } else if (glued > 0) {
            line1 = net.minecraft.network.chat.Component.translatable(
                    "hud.astranautics.adhesive_apply", glued).getString();
            drawMouse(graphics, cx - 96, cy - 2, MouseButton.LEFT, false);
        } else {
            line1 = net.minecraft.network.chat.Component.translatable(
                    "hud.astranautics.adhesive_select").getString();
            drawMouse(graphics, cx - 96, cy - 2, MouseButton.RIGHT, true);
        }
        graphics.drawString(minecraft.font, line1, cx - 72, cy - 8, 0xFFDFF8FA, false);
        graphics.drawString(minecraft.font, net.minecraft.network.chat.Component.translatable(
                        "hud.astranautics.adhesive_size", AdhesiveItem.radiusName(stack)),
                cx - 72, cy + 3, 0xFF7FD9E0, false);
    }

    private enum MouseButton { LEFT, RIGHT }

    /** Ponder 風の小さなマウスアイコン（押されているボタンを光らせる）。 */
    private static void drawMouse(GuiGraphics graphics, int x, int y, MouseButton button, boolean active) {
        int body = 0xFF1B2A33;
        int edge = 0xFF8FE7EE;
        int glow = active ? 0xFF5FE0EA : 0xFF2C4550;
        graphics.fill(x, y, x + 12, y + 18, body);
        graphics.fill(x, y, x + 12, y + 1, edge);
        graphics.fill(x, y + 17, x + 12, y + 18, edge);
        graphics.fill(x, y, x + 1, y + 18, edge);
        graphics.fill(x + 11, y, x + 12, y + 18, edge);
        // 左ボタン / 右ボタン
        if (button == MouseButton.LEFT) {
            graphics.fill(x + 2, y + 2, x + 6, y + 8, glow);
        } else {
            graphics.fill(x + 6, y + 2, x + 10, y + 8, glow);
        }
        // スクロールホイール風の線
        graphics.fill(x + 5, y + 2, x + 7, y + 6, 0xFF13202A);
    }
}
