package com.astranautics.client.ponder;

import com.astranautics.client.ponder.step.PhysicalizerSteps;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Ponder（思考 UI）画面。
 *
 * <p>アイテムにカーソルを当てて <b>W を長押し</b>すると開きます。Create の Ponder と
 * 同じ操作感で、等角投影のアニメーション＋説明テキスト＋ホットバーのアイテム表示、
 * 下部のタイムライン（進捗バー）付きです。</p>
 *
 * <p>描画は {@link IsoCanvas} に 1 フレームずつ描いて {@link DynamicTexture} に
 * アップロードしています（GUI にポリゴン描画が無いため）。</p>
 */
public class PonderScreen extends Screen {

    private final PonderScene scene;
    private final int index;

    /** シーンの経過 tick（20 tick = 1 秒）。 */
    private int tick;

    /** キャンバス（シーン描画用）。 */
    private static final int CANVAS_W = 320;
    private static final int CANVAS_H = 200;
    private final IsoCanvas canvas = new IsoCanvas(CANVAS_W, CANVAS_H, 46.0D,
            CANVAS_W / 2.0D, CANVAS_H * 0.70D);
    private final PonderContext context;

    private DynamicTexture dynamicTexture;
    private ResourceLocation textureId;
    private boolean textureDirty = true;

    /** 画面の表示アニメーション（ふわっと開く）。 */
    private float openProgress;

    public PonderScreen(PonderScene scene, int index) {
        super(scene.title);
        this.scene = scene;
        this.index = index;
        this.context = new PonderContext(Minecraft.getInstance());
    }

    @Override
    protected void init() {
        super.init();
        this.tick = 0;
        if (this.textureId == null) {
            this.dynamicTexture = new DynamicTexture(CANVAS_W, CANVAS_H, false);
            this.textureId = Minecraft.getInstance().getTextureManager()
                    .register("astranautics_ponder", this.dynamicTexture);
            this.dynamicTexture.setFilter(false, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.openProgress = Math.min(1.0F, this.openProgress + partialTick * 0.08F);

        // 背景（暗くする）
        graphics.fill(0, 0, this.width, this.height, 0xC8081016);

        // シーンを進める
        this.tick++;
        if (this.tick > this.scene.durationTicks + 60) {
            // ループ
            this.tick = 0;
        }

        // キャンバスに描画してテクスチャ更新
        PhysicalizerSteps.CURRENT_TICK = this.tick;
        this.canvas.clear(0x00000000);
        this.scene.renderer.accept(this.context, this.canvas);
        uploadTexture();

        // パネル配置
        int panelW = 420;
        int panelH = 300;
        int x0 = (this.width - panelW) / 2;
        int y0 = (this.height - panelH) / 2;
        float scale = 0.9F + 0.1F * this.openProgress;

        graphics.pose().pushPose();
        graphics.pose().translate(x0 + panelW / 2.0F, y0 + panelH / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-(x0 + panelW / 2.0F), -(y0 + panelH / 2.0F), 0.0F);

        PonderTheme theme = this.scene.theme;
        // 外枠（Ponder 風の角ばったパネル。ブロックごとに配色が変わる）
        graphics.fill(x0 - 4, y0 - 4, x0 + panelW + 4, y0 + panelH + 4, theme.accent());
        graphics.fill(x0, y0, x0 + panelW, y0 + panelH, theme.panel());
        // ヘッダー
        graphics.fill(x0, y0, x0 + panelW, y0 + 22, theme.header());
        graphics.fill(x0, y0 + 22, x0 + panelW, y0 + 23, theme.accent());

        // アイコン＋タイトル
        if (this.scene.icon != null) {
            graphics.blit(this.scene.icon, x0 + 6, y0 + 3, 0, 0, 16, 16, 16, 16);
        }
        graphics.drawString(this.font, this.scene.title, x0 + 28, y0 + 7, theme.text(), false);
        graphics.drawString(this.font, (this.index + 1) + "/" + PonderScene.count(),
                x0 + panelW - 30, y0 + 7, theme.title(), false);

        // シーン
        int sx = x0 + (panelW - CANVAS_W) / 2;
        int sy = y0 + 34;
        RenderSystem.enableBlend();
        graphics.blit(this.textureId, sx, sy, 0, 0, CANVAS_W, CANVAS_H, CANVAS_W, CANVAS_H);
        RenderSystem.disableBlend();
        // シーンの縁
        graphics.fill(sx, sy + CANVAS_H, sx + CANVAS_W, sy + CANVAS_H + 1, theme.accent());

        // 説明テキスト（順番に切り替わる）
        int textIndex = this.scene.textIndexAt(this.tick);
        if (textIndex >= 0 && textIndex < this.scene.texts.size()) {
            Component line = this.scene.texts.get(textIndex);
            graphics.drawString(this.font, line, x0 + 12, y0 + CANVAS_H + 42, theme.text(), false);
        }

        // タイムライン
        int barY = y0 + panelH - 8;
        graphics.fill(x0 + 12, barY, x0 + panelW - 12, barY + 3, theme.header());
        double progress = Math.min(1.0D, (double) this.tick / Math.max(1, this.scene.durationTicks));
        graphics.fill(x0 + 12, barY, x0 + 12 + (int) ((panelW - 24) * progress), barY + 3, theme.progress());

        // カーソルのガイド
        graphics.drawString(this.font, net.minecraft.network.chat.Component.translatable(
                        "ponder.astranautics.controls"),
                x0 + 12, y0 + panelH - 18, theme.title(), false);

        graphics.pose().popPose();
    }

    /** キャンバスのピクセルを DynamicTexture に転送。 */
    private void uploadTexture() {
        if (this.dynamicTexture == null) {
            return;
        }
        NativeImage image = this.dynamicTexture.getPixels();
        if (image == null) {
            return;
        }
        int w = Math.min(image.getWidth(), CANVAS_W);
        int h = Math.min(image.getHeight(), CANVAS_H);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(x, y, this.canvas.pixels[y * CANVAS_W + x]);
            }
        }
        this.dynamicTexture.upload();
        this.textureDirty = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 左右キーでシーン切替
        if (keyCode == 263) {   // LEFT
            openScene(this.index - 1);
            return true;
        }
        if (keyCode == 262) {   // RIGHT
            openScene(this.index + 1);
            return true;
        }
        if (keyCode == 82) {    // R
            this.tick = 0;
            return true;
        }
        if (keyCode == 87) {    // W（長押しすると開くキー）：押しっぱなしでは閉じない
            return true;
        }
        if (keyCode == 256) {   // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openScene(int newIndex) {
        List<PonderScene> scenes = PonderScene.scenes();
        if (scenes.isEmpty()) {
            return;
        }
        int target = Mth.clamp(newIndex, 0, scenes.size() - 1);
        Minecraft.getInstance().setScreen(new PonderScreen(scenes.get(target), target));
    }

    @Override
    public void removed() {
        super.removed();
        if (this.dynamicTexture != null) {
            Minecraft.getInstance().getTextureManager().release(this.textureId);
            this.dynamicTexture.close();
            this.dynamicTexture = null;
            this.textureId = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 未使用フィールド警告避け（将来の巻き戻し用）。 */
    public boolean isTextureDirty() {
        return this.textureDirty;
    }
}
