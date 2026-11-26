package com.astranautics.client.ponder;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Ponder 風 UI 用の超軽量等角投影ラスタライザ。
 *
 * <p>Minecraft の GUI には多角形描画が無いため、いったん {@link NativeImage} に
 * ピクセル単位で描いてからテクスチャとして貼ります（1 フレーム分のみ生成し、
 * 表示中は使い回し）。</p>
 */
public final class IsoCanvas {

    private static final double COS30 = 0.8660254D;
    private static final double SIN30 = 0.5D;

    public final int width;
    public final int height;
    /** ARGB ピクセル（行優先）。 */
    public final int[] pixels;

    /** カメラ（等角投影の原点オフセット、ピクセル）。 */
    private double originX;
    private double originY;
    /** 1 ブロックあたりのピクセル数。 */
    private double scale;

    public IsoCanvas(int width, int height, double scale, double originX, double originY) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        this.scale = scale;
        this.originX = originX;
        this.originY = originY;
    }

    public void setView(double scale, double originX, double originY) {
        this.scale = scale;
        this.originX = originX;
        this.originY = originY;
    }

    /* ------------------------------------------------------------------ */
    /* 基本操作                                                             */
    /* ------------------------------------------------------------------ */

    public void clear(int argb) {
        java.util.Arrays.fill(this.pixels, argb);
    }

    private void put(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
            return;
        }
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            return;
        }
        int index = y * this.width + x;
        if (alpha >= 255) {
            this.pixels[index] = argb;
            return;
        }
        // 簡易アルファ合成
        int dst = this.pixels[index];
        int da = (dst >>> 24) & 0xFF;
        if (da == 0) {
            this.pixels[index] = argb;
            return;
        }
        double sa = alpha / 255.0D;
        int r = (int) (((argb >>> 16) & 0xFF) * sa + ((dst >>> 16) & 0xFF) * (1 - sa));
        int g = (int) (((argb >>> 8) & 0xFF) * sa + ((dst >>> 8) & 0xFF) * (1 - sa));
        int b = (int) ((argb & 0xFF) * sa + (dst & 0xFF) * (1 - sa));
        int a = Math.max(da, alpha);
        this.pixels[index] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** ワールド座標 → スクリーン座標。 */
    public double[] project(double x, double y, double z) {
        double sx = (x - z) * COS30 * this.scale + this.originX;
        double sy = ((x + z) * SIN30 - y) * this.scale + this.originY;
        return new double[]{sx, sy};
    }

    /* ------------------------------------------------------------------ */
    /* テクスチャ（16x16 を 2 次元配列で持つ）                              */
    /* ------------------------------------------------------------------ */

    /** ブロックテクスチャのサンプラ。 */
    @FunctionalInterface
    public interface Sampler {
        int colorAt(double u, double v);
    }

    /** NativeImage からテクスチャを作る（16x16 等）。 */
    public static Sampler fromImage(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] data = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = image.getPixelRGBA(x, y);
                // NativeImage は ABGR 順で保持している
                int a = (abgr >>> 24) & 0xFF;
                int b = (abgr >>> 16) & 0xFF;
                int g = (abgr >>> 8) & 0xFF;
                int r = abgr & 0xFF;
                data[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return (u, v) -> {
            int x = (int) Math.floor(clamp01(u) * (w - 1));
            int y = (int) Math.floor(clamp01(v) * (h - 1));
            return data[y * w + x];
        };
    }

    /** 単色のサンプラ。 */
    public static Sampler solid(int argb) {
        return (u, v) -> argb;
    }

    private static double clamp01(double t) {
        return t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
    }

    /* ------------------------------------------------------------------ */
    /* 立方体                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 立方体を描く（見える 3 面：上面・z+ 面・x+ 面）。
     *
     * @param cx cy cz 中心（ブロック単位）
     * @param size     一辺（ブロック単位）
     */
    public void cube(double cx, double cy, double cz, double size,
                     Sampler top, Sampler sideZ, Sampler sideX,
                     double shadeTop, double shadeZ, double shadeX) {
        double h = size / 2.0D;
        double x0 = cx - h;
        double x1 = cx + h;
        double y0 = cy - h;
        double y1 = cy + h;
        double z0 = cz - h;
        double z1 = cz + h;

        // 上面（y1）
        face(new double[]{x0, y1, z0}, new double[]{size, 0, 0}, new double[]{0, 0, size},
                top, shadeTop);
        // z+ 面（手前左）
        face(new double[]{x0, y1, z1}, new double[]{size, 0, 0}, new double[]{0, -size, 0},
                sideZ, shadeZ);
        // x+ 面（手前右）
        face(new double[]{x1, y1, z1}, new double[]{0, 0, -size}, new double[]{0, -size, 0},
                sideX, shadeX);
    }

    /** 立方体の輪郭を描く（選択ハイライトなど）。 */
    public void cubeOutline(double cx, double cy, double cz, double size, int argb) {
        double h = size / 2.0D;
        double[][] corners = new double[8][];
        int index = 0;
        for (int iy = 0; iy < 2; iy++) {
            for (int ix = 0; ix < 2; ix++) {
                for (int iz = 0; iz < 2; iz++) {
                    corners[index++] = new double[]{
                            cx + (ix == 0 ? -h : h), cy + (iy == 0 ? -h : h), cz + (iz == 0 ? -h : h)};
                }
            }
        }
        // 12 本の辺
        int[][] edges = {
                {0, 1}, {2, 3}, {4, 5}, {6, 7},
                {0, 2}, {1, 3}, {4, 6}, {5, 7},
                {0, 4}, {1, 5}, {2, 6}, {3, 7},
        };
        for (int[] edge : edges) {
            line(corners[edge[0]], corners[edge[1]], argb);
        }
    }

    /** 太い輪郭（選択状態を目立たせる）。 */
    public void cubeOutlineThick(double cx, double cy, double cz, double size, int argb, double thickness) {
        int layers = Math.max(1, (int) Math.round(thickness));
        for (int i = 0; i < layers; i++) {
            double offset = (i - layers / 2.0D) * 0.006D;
            cubeOutline(cx + offset, cy + offset, cz + offset, size, argb);
        }
    }

    /** ワールド内の線分。 */
    public void line(double[] from, double[] to, int argb) {
        double[] a = project(from[0], from[1], from[2]);
        double[] b = project(to[0], to[1], to[2]);
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        int steps = (int) Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            put((int) Math.round(a[0] + dx * t), (int) Math.round(a[1] + dy * t), argb);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 面のラスタライズ                                                     */
    /* ------------------------------------------------------------------ */

    private void face(double[] origin, double[] u, double[] v, Sampler sampler, double shade) {
        double[] o = project(origin[0], origin[1], origin[2]);
        double[] pu = project(origin[0] + u[0], origin[1] + u[1], origin[2] + u[2]);
        double[] pv = project(origin[0] + v[0], origin[1] + v[1], origin[2] + v[2]);

        double ux = pu[0] - o[0];
        double uy = pu[1] - o[1];
        double vx = pv[0] - o[0];
        double vy = pv[1] - o[1];
        double det = ux * vy - uy * vx;
        if (Math.abs(det) < 1.0E-6D) {
            return;
        }
        double invDet = 1.0D / det;

        int minX = (int) Math.floor(Math.min(o[0], Math.min(pu[0], pv[0])) - 1);
        int maxX = (int) Math.ceil(Math.max(o[0], Math.max(pu[0], pv[0])) + 1);
        int minY = (int) Math.floor(Math.min(o[1], Math.min(pu[1], pv[1])) - 1);
        int maxY = (int) Math.ceil(Math.max(o[1], Math.max(pu[1], pv[1])) + 1);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5D - o[0];
                double py = y + 0.5D - o[1];
                double a = (px * vy - py * vx) * invDet;
                double b = (ux * py - uy * px) * invDet;
                if (a < 0.0D || a > 1.0D || b < 0.0D || b > 1.0D) {
                    continue;
                }
                int argb = sampler.colorAt(a, b);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                put(x, y, shade(argb, shade));
            }
        }
    }

    public static int shade(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = clamp255((int) (((argb >>> 16) & 0xFF) * factor));
        int g = clamp255((int) (((argb >>> 8) & 0xFF) * factor));
        int b = clamp255((int) ((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp255(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    /* ------------------------------------------------------------------ */
    /* 影・円                                                               */
    /* ------------------------------------------------------------------ */

    /** 床に落ちる柔らかい楕円の影。 */
    public void shadow(double cx, double cy, double cz, double radius, double alpha) {
        double[] center = project(cx, cy, cz);
        double rx = radius * this.scale * COS30 * 1.25D;
        double ry = radius * this.scale * SIN30 * 1.25D;
        int minX = (int) (center[0] - rx) - 1;
        int maxX = (int) (center[0] + rx) + 1;
        int minY = (int) (center[1] - ry) - 1;
        int maxY = (int) (center[1] + ry) + 1;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = (x - center[0]) / Math.max(1.0D, rx);
                double dy = (y - center[1]) / Math.max(1.0D, ry);
                double d = dx * dx + dy * dy;
                if (d > 1.0D) {
                    continue;
                }
                double a = alpha * (1.0D - d) * (1.0D - d);
                put(x, y, ((int) (a * 255) << 24) | 0x101820);
            }
        }
    }

    /** 円（パーティクル・光点）。 */
    public void disc(double sx, double sy, double radius, int argb) {
        int minX = (int) (sx - radius) - 1;
        int maxX = (int) (sx + radius) + 1;
        int minY = (int) (sy - radius) - 1;
        int maxY = (int) (sy + radius) + 1;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = x + 0.5D - sx;
                double dy = y + 0.5D - sy;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > radius) {
                    continue;
                }
                int a = (argb >>> 24) & 0xFF;
                if (d > radius - 1.0D) {
                    a = (int) (a * (radius - d));
                }
                put(x, y, (clamp255(a) << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    /** ワールド座標の点に光点を打つ。 */
    public void glow(double x, double y, double z, double radius, int argb) {
        double[] p = project(x, y, z);
        disc(p[0], p[1], radius, argb);
    }

    /* ------------------------------------------------------------------ */
    /* 背景                                                                 */
    /* ------------------------------------------------------------------ */

    /** 背景のグラデーション＋薄いグリッド。 */
    public void background(int topColor, int bottomColor) {
        for (int y = 0; y < this.height; y++) {
            double t = (double) y / Math.max(1, this.height - 1);
            int argb = lerpColor(topColor, bottomColor, t);
            for (int x = 0; x < this.width; x++) {
                this.pixels[y * this.width + x] = argb;
            }
        }
        // 床のグリッド（等角投影の線を薄く）
        int gridColor = 0x14FFFFFF;
        for (int i = -6; i <= 6; i++) {
            double[] a = project(i, 0, -6);
            double[] b = project(i, 0, 6);
            int steps = (int) Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int s = 0; s <= steps; s++) {
                double t = steps == 0 ? 0 : (double) s / steps;
                put((int) Math.round(a[0] + (b[0] - a[0]) * t),
                        (int) Math.round(a[1] + (b[1] - a[1]) * t), gridColor);
            }
            double[] c = project(-6, 0, i);
            double[] d = project(6, 0, i);
            steps = (int) Math.max(Math.abs(d[0] - c[0]), Math.abs(d[1] - c[1]));
            for (int s = 0; s <= steps; s++) {
                double t = steps == 0 ? 0 : (double) s / steps;
                put((int) Math.round(c[0] + (d[0] - c[0]) * t),
                        (int) Math.round(c[1] + (d[1] - c[1]) * t), gridColor);
            }
        }
    }

    public static int lerpColor(int from, int to, double t) {
        int a = (int) (((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) (((from >>> 16) & 0xFF) * (1 - t) + ((to >>> 16) & 0xFF) * t);
        int g = (int) (((from >>> 8) & 0xFF) * (1 - t) + ((to >>> 8) & 0xFF) * t);
        int b = (int) ((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
