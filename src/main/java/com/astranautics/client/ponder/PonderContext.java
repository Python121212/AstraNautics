package com.astranautics.client.ponder;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder シーンの共通コンテキスト（テクスチャ解決・イージング・カーソル表示）。
 */
public final class PonderContext {

    /** シーンに登場するアイテム（ホットバーに表示）。 */
    public enum ItemKind {
        NONE, PHYSICALIZER, ADHESIVE
    }

    /** マウスカーソルの状態（Ponder 風のアイコン表示）。 */
    public enum Cursor {
        HIDDEN, RIGHT_CLICK, RIGHT_HOLD, LEFT_CLICK
    }

    private final Minecraft minecraft;

    public PonderContext(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /* ------------------------------------------------------------------ */
    /* イージング                                                           */
    /* ------------------------------------------------------------------ */

    public static double clamp01(double t) {
        return t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
    }

    /** 0..1 の進捗を返す（start..end tick）。 */
    public static double progress(int tick, int start, int end) {
        if (end <= start) {
            return tick >= end ? 1.0D : 0.0D;
        }
        return clamp01((double) (tick - start) / (end - start));
    }

    /** なめらかな加減速（ease-in-out）。 */
    public static double smooth(double t) {
        double c = clamp01(t);
        return c * c * (3.0D - 2.0D * c);
    }

    /** ポンと落ちる動き（重力っぽい ease-in）。 */
    public static double fallCurve(double t) {
        double c = clamp01(t);
        return c * c;
    }

    /** 上に跳ねる動き（ease-out）。 */
    public static double easeOut(double t) {
        double c = clamp01(t);
        return 1.0D - (1.0D - c) * (1.0D - c);
    }

    /** 弾む（バウンド付き）。 */
    public static double bounce(double t) {
        double c = clamp01(t);
        return 1.0D - Math.abs(Math.cos(c * Math.PI * 1.5D)) * (1.0D - c) * 1.4D * (1.0D - c);
    }

    /* ------------------------------------------------------------------ */
    /* テクスチャ                                                           */
    /* ------------------------------------------------------------------ */

    /** ブロックの描画テクスチャ（ブロックモデルは使わず、16x16 を貼る）。 */
    public IsoCanvas.Sampler blockTexture(ResourceLocation id) {
        return TextureCache.block(this.minecraft, id);
    }

    /** 任意のリソース（アイテムアイコン等）。 */
    public IsoCanvas.Sampler texture(ResourceLocation id) {
        return TextureCache.block(this.minecraft, id);
    }

    /* ------------------------------------------------------------------ */
    /* カーソル（Ponder 風）                                                */
    /* ------------------------------------------------------------------ */

    /**
     * マウスカーソルを描く（押されているボタンを光らせる）。
     *
     * @param sx     スクリーン X（キャンバス座標）
     * @param sy     スクリーン Y（キャンバス座標）
     * @param press  押下の強さ（0..1、0 で非表示）
     */
    public void drawCursor(IsoCanvas canvas, double sx, double sy, Cursor cursor, double press) {
        if (cursor == Cursor.HIDDEN || press <= 0.01D) {
            return;
        }
        int x = (int) Math.round(sx);
        int y = (int) Math.round(sy);

        int bodyBorder = 0xFFB8F4FA;
        int body = 0xFF16242C;
        int bodyFill = 0xFF20343F;

        // マウス本体（角丸風の矩形）
        int w = 14;
        int h = 22;
        int alpha = (int) (255 * Math.min(1.0D, press * 1.6D));
        fillRounded(canvas, x, y, w, h, (alpha << 24) | (body & 0xFFFFFF));
        outline(canvas, x, y, w, h, (alpha << 24) | (bodyBorder & 0xFFFFFF));
        // 中央の区切り線
        vline(canvas, x + w / 2, y + 1, y + h / 2, (alpha << 24) | 0x0D1A22);
        hline(canvas, x + 1, x + w - 1, y + h / 3, (alpha << 24) | 0x0D1A22);

        // 押されているボタンの光
        int glowColor = (int) (alpha * (0.35D + 0.65D * press)) << 24 | 0x5FE0EA;
        switch (cursor) {
            case LEFT_CLICK -> fillRect(canvas, x + 1, y + 1, w / 2 - 1, h / 3 - 1, glowColor);
            case RIGHT_CLICK, RIGHT_HOLD -> fillRect(canvas, x + w / 2 + 1, y + 1, w / 2 - 1, h / 3 - 1, glowColor);
            default -> {
            }
        }

        // 押下の波紋
        if (press > 0.05D) {
            int ringAlpha = (int) (110 * (1.0D - press * 0.5D));
            double radius = 12.0D + press * 10.0D;
            for (int i = 0; i < 40; i++) {
                double angle = i / 40.0D * Math.PI * 2.0D;
                int px = (int) (x + w / 2.0D + Math.cos(angle) * radius);
                int py = (int) (y + h / 2.0D + Math.sin(angle) * radius * 0.9D);
                canvas.disc(px, py, 0.9D, (ringAlpha << 24) | 0x7FE8F0);
            }
        }

        // 左クリックの「マーク」（ターゲット風の十字）
        if (cursor == Cursor.LEFT_CLICK) {
            int markAlpha = (int) (255 * press);
            int color = (markAlpha << 24) | 0xFFE9A0;
            hline(canvas, x - 6, x - 2, y + h / 2, color);
            hline(canvas, x + w + 2, x + w + 6, y + h / 2, color);
            vline(canvas, x + w / 2, y - 6, y - 2, color);
            vline(canvas, x + w / 2, y + h + 2, y + h + 6, color);
        }
    }

    /* 小さな描画ヘルパー（IsoCanvas は put が private なので disc/fill を使う） */

    private static void fillRect(IsoCanvas canvas, int x, int y, int w, int h, int argb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                canvas.disc(xx + 0.5D, yy + 0.5D, 0.6D, argb);
            }
        }
    }

    private static void fillRounded(IsoCanvas canvas, int x, int y, int w, int h, int argb) {
        fillRect(canvas, x + 1, y + 1, w - 2, h - 2, argb);
        fillRect(canvas, x + 2, y, w - 4, h, argb);
        fillRect(canvas, x, y + 2, w, h - 4, argb);
    }

    private static void outline(IsoCanvas canvas, int x, int y, int w, int h, int argb) {
        hline(canvas, x, x + w - 1, y, argb);
        hline(canvas, x, x + w - 1, y + h - 1, argb);
        vline(canvas, x, y, y + h - 1, argb);
        vline(canvas, x + w - 1, y, y + h - 1, argb);
    }

    private static void hline(IsoCanvas canvas, int x0, int x1, int y, int argb) {
        for (int x = x0; x <= x1; x++) {
            canvas.disc(x + 0.5D, y + 0.5D, 0.6D, argb);
        }
    }

    private static void vline(IsoCanvas canvas, int x, int y0, int y1, int argb) {
        for (int y = y0; y <= y1; y++) {
            canvas.disc(x + 0.5D, y + 0.5D, 0.6D, argb);
        }
    }
}
