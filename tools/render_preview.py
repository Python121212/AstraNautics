#!/usr/bin/env python3
"""Physicalizer の等角図（アイソメトリック）プレビューを 1 枚だけ生成する。

MOD 側テクスチャ（assets/astranautics/textures/block/*.png）を読み込み、
レバー（木の台座＋金属ブラケット＋テーパーシャフト＋ノブ）を
等角投影で「組み立てた状態」に描いて `images/physicalizer.png` に保存する。

使い方:
    python3 -m venv .venv && .venv/bin/pip install Pillow
    .venv/bin/python tools/generate_textures.py   # 素材テクスチャを生成
    .venv/bin/python tools/render_preview.py     # 等角図 1 枚を生成
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_BLOCK = os.path.join(
    ROOT, "src/main/resources/assets/astranautics/textures/block")
OUT = os.path.join(ROOT, "images/physicalizer.png")

SCALE = 196          # 1 ブロック = 196 px
W, H = 340, 400      # 出力キャンバス
COS30, SIN30 = 0.8660254, 0.5

# 面ごとの明度（上面が最も明るい）
SHADE = {
    "top": 1.00,
    "front": 0.82,   # z+ 面（左手前）
    "side": 0.60,    # x+ 面（右手前）
}

OUTLINE = (28, 20, 12, 150)


def project(x, y, z):
    """等角投影（x 右, y 上, z 手前）。"""
    return (x - z) * COS30, (x + z) * SIN30 - y


def to_screen(x, y, z):
    px, py = project(x, y, z)
    return px * SCALE + W / 2, py * SCALE + H * 0.50


def load_tex(name):
    return Image.open(os.path.join(TEX_BLOCK, name)).convert("RGBA")


def texel(tex, tu, tv):
    w, h = tex.size
    x = min(w - 1, max(0, int(tu * w)))
    y = min(h - 1, max(0, int(tv * h)))
    return tex.getpixel((x, y))


def shade(color, factor):
    r, g, b, a = color
    if a == 0:
        return color
    return (int(r * factor), int(g * factor), int(b * factor), a)


def draw_face(img, tex, corners, shade_key, uv=(0.0, 1.0, 1.0, 0.0), steps=110):
    """四隅 (x,y,z) で囲まれた面をテクスチャで塗る。

    corners = (origin, u_vec, v_vec) の形で受け取り、内部を格子状にサンプリング。
    """
    origin, u_vec, v_vec = corners
    u0, v0, u1, v1 = uv
    for i in range(steps + 1):
        for j in range(steps + 1):
            fu = i / steps
            fv = j / steps
            x = origin[0] + u_vec[0] * fu + v_vec[0] * fv
            y = origin[1] + u_vec[1] * fu + v_vec[1] * fv
            z = origin[2] + u_vec[2] * fu + v_vec[2] * fv
            sx, sy = to_screen(x, y, z)
            color = texel(tex, u0 + (u1 - u0) * fu, v0 + (v1 - v0) * fv)
            if color[3] == 0:
                continue
            color = shade(color, SHADE[shade_key])
            ix, iy = int(round(sx)), int(round(sy))
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = ix + dx, iy + dy
                    if 0 <= xx < W and 0 <= yy < H:
                        img.putpixel((xx, yy), color)


def outline_quad(img, p0, p1, p2, p3, width=1):
    """面の輪郭線（立体感を出す）。"""
    d = ImageDraw.Draw(img)
    pts = [to_screen(*p) for p in (p0, p1, p2, p3)]
    d.line([*pts, pts[0]], fill=OUTLINE, width=width, joint="curve")


def draw_box(img, tex, cx, cy, cz, hx, hy, hz, uv=(0.0, 1.0, 1.0, 0.0), outline=True,
             side_tex=None):
    """中心 (cx,cy,cz)、半径 (hx,hy,hz) の直方体を等角投影で描く。

    見えるのは z+ 面（左手前）・x+ 面（右手前）・上面の 3 面。
    """
    x0, x1 = cx - hx, cx + hx
    y0, y1 = cy - hy, cy + hy
    z0, z1 = cz - hz, cz + hz

    side = side_tex if side_tex is not None else tex
    # z+ 面（左手前）
    draw_face(img, side, ((x0, y1, z1), (2 * hx, 0, 0), (0, -2 * hy, 0)), "front", uv)
    if outline:
        outline_quad(img, (x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1))

    # x+ 面（右手前）
    draw_face(img, side, ((x1, y1, z1), (0, 0, -2 * hz), (0, -2 * hy, 0)), "side", uv)
    if outline:
        outline_quad(img, (x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1))

    # 上面
    draw_face(img, tex, ((x0, y1, z0), (2 * hx, 0, 0), (0, 0, 2 * hz)), "top", uv)
    if outline:
        outline_quad(img, (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1))


def main():
    atlas = load_tex("lever_atlas.png")
    w, h = atlas.size
    cw, ch = w // 4, h // 2

    SMOOTH = 6  # 等角図では拡大するので、その分だけ滑らかに補間したテクスチャを使う

    def cell(col, row=0):
        tile = atlas.crop((col * cw, row * ch, (col + 1) * cw, (row + 1) * ch))
        return tile.resize((cw * SMOOTH, ch * SMOOTH), Image.NEAREST)

    wood = cell(0)        # 台座の上面（木目・板の継ぎ目）
    wood_side = cell(3)   # 台座の側面（縦木目）

    # 金属パーツは「縦のハイライト」を強調して金属感を出す
    def metalize(tile):
        px = tile.load()
        w2, h2 = tile.size
        for yy in range(h2):
            for xx in range(w2):
                r, g, b, a = px[xx, yy]
                # 中心に近いほど明るく、端ほど暗く
                t = abs(xx / max(1, w2 - 1) - 0.5) * 2.0
                f = 1.28 - 0.55 * t * t
                px[xx, yy] = (min(255, int(r * f)), min(255, int(g * f)),
                              min(255, int(b * f)), a)
        return tile

    metal_on = metalize(cell(1))
    metal_off = metalize(cell(2))

    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # ---- 台座（木）：2 段ベース ----
    draw_box(canvas, wood, 0, 0.045, 0, 0.30, 0.045, 0.30, side_tex=wood_side)
    draw_box(canvas, wood, 0, 0.112, 0, 0.20, 0.022, 0.20, side_tex=wood_side)

    # ---- 金属ブラケット（ピボットを挟む 2 枚）----
    for sx in (-1, 1):
        draw_box(canvas, metal_off, sx * 0.095, 0.190, 0, 0.024, 0.056, 0.075)
    # ピボット軸（ブラケットの間）
    draw_box(canvas, metal_off, 0, 0.190, 0, 0.066, 0.040, 0.040)

    # ---- シャフト（テーパー：下が太く上が細い）----
    y = 0.215
    for half, height in ((0.055, 0.13), (0.045, 0.13), (0.036, 0.12)):
        draw_box(canvas, metal_on, 0, y + height / 2, 0, half, height / 2, half)
        # 継ぎ目リング（メタルパーツの刻み）
        draw_box(canvas, metal_off, 0, y + height, 0, half + 0.007, 0.010, half + 0.007)
        y += height

    # ---- ノブ（球体風に 3 段積む）----
    y += 0.012
    for half, height in ((0.080, 0.050), (0.110, 0.080), (0.075, 0.052), (0.045, 0.028)):
        draw_box(canvas, metal_on, 0, y + height / 2, 0, half, height / 2, half)
        y += height

    # ---- 上部のハイライト（光沢）----
    d = ImageDraw.Draw(canvas)
    hx, hy = to_screen(-0.045, y - 0.012, -0.045)
    d.ellipse([hx - 7, hy - 4, hx + 7, hy + 4], fill=(240, 255, 255, 190))

    canvas.save(OUT)
    print("generated:", OUT)


if __name__ == "__main__":
    main()
