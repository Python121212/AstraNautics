#!/usr/bin/env python3
"""アイテム／ブロックの等角図プレビューを 1 枚ずつ生成する。

`images/` には「1 アイテムにつき 1 枚」だけを置きます（状態違いは作らない）。

- images/physicalizer.png … 物理化装置 × 石ブロック
- images/adhesive.png     … 接着剤 × 3 ブロック（接着の様子）

使い方:
    python3 -m venv /tmp/v && /tmp/v/bin/pip install Pillow
    /tmp/v/bin/python tools/generate_textures.py
    /tmp/v/bin/python tools/render_preview.py
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_BLOCK = os.path.join(ROOT, "src/main/resources/assets/astranautics/textures/block")
TEX_ITEM = os.path.join(ROOT, "src/main/resources/assets/astranautics/textures/item")
OUT_DIR = os.path.join(ROOT, "images")

SCALE = 168
ORIGIN_Y = 0.74
W, H = 380, 470
COS30, SIN30 = 0.8660254, 0.5

SHADE = {"top": 1.00, "front": 0.80, "side": 0.56}
OUTLINE = (26, 20, 14, 150)


def project(x, y, z):
    return (x - z) * COS30, (x + z) * SIN30 - y


def to_screen(x, y, z, cx=None, cy=None):
    px, py = project(x, y, z)
    return px * SCALE + (W / 2 if cx is None else cx), \
        py * SCALE + (H * ORIGIN_Y if cy is None else cy)


def load(path):
    return Image.open(path).convert("RGBA")


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


def draw_face(img, tex, corners, shade_key, uv=(0.0, 1.0, 1.0, 0.0), steps=100):
    origin, u_vec, v_vec = corners
    u0, v0, u1, v1 = uv
    for i in range(steps + 1):
        for j in range(steps + 1):
            fu, fv = i / steps, j / steps
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


def outline_quad(img, p0, p1, p2, p3, width=1, color=OUTLINE):
    d = ImageDraw.Draw(img)
    pts = [to_screen(*p) for p in (p0, p1, p2, p3)]
    d.line([*pts, pts[0]], fill=color, width=width, joint="curve")


def draw_box(img, tex, cx, cy, cz, hx, hy, hz, uv=(0.0, 1.0, 1.0, 0.0), outline=True,
             side_tex=None, steps=100):
    x0, x1 = cx - hx, cx + hx
    y0, y1 = cy - hy, cy + hy
    z0, z1 = cz - hz, cz + hz
    side = side_tex if side_tex is not None else tex

    draw_face(img, side, ((x0, y1, z1), (2 * hx, 0, 0), (0, -2 * hy, 0)), "front", uv, steps)
    if outline:
        outline_quad(img, (x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1))

    draw_face(img, side, ((x1, y1, z1), (0, 0, -2 * hz), (0, -2 * hy, 0)), "side", uv, steps)
    if outline:
        outline_quad(img, (x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1))

    draw_face(img, tex, ((x0, y1, z0), (2 * hx, 0, 0), (0, 0, 2 * hz)), "top", uv, steps)
    if outline:
        outline_quad(img, (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1))


def paste_item(canvas, item, cx, cy, scale=1.0):
    """アイテムアイコン（16x16）を等角図の上に貼る。"""
    size = int(16 * 6 * scale)
    big = item.resize((size, size), Image.NEAREST)
    canvas.alpha_composite(big, (int(cx - size / 2), int(cy - size / 2)))


# ----------------------------------------------------------------------
# 1. 物理化装置（石ブロック＋装置＋レバー ON）
# ----------------------------------------------------------------------
def render_physicalizer():
    atlas = load(os.path.join(TEX_BLOCK, "lever_atlas.png"))
    w, h = atlas.size
    cw, ch = w // 4, h // 2

    def cell(col, row=0):
        tile = atlas.crop((col * cw, row * ch, (col + 1) * cw, (row + 1) * ch))
        return tile.resize((cw * 8, ch * 8), Image.NEAREST)

    wood, wood_side = cell(0), cell(3)
    metal_on, metal_off = cell(1), cell(2)
    stone_tex = make_stone()

    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # 石ブロック（1 辺 1.0）
    draw_box(canvas, stone_tex, 0, 0.5, 0, 0.5, 0.5, 0.5)

    # シアンの発光（ON）：台座の後ろに薄く敷く
    d0 = ImageDraw.Draw(canvas)
    gx, gy = to_screen(0, 1.16, 0)
    d0.ellipse([gx - 44, gy - 24, gx + 44, gy + 24], fill=(95, 224, 234, 26))

    # ---- 装置の台座（木の 2 段）----
    draw_box(canvas, wood, 0, 1.04, 0, 0.30, 0.04, 0.30, side_tex=wood_side)
    draw_box(canvas, wood, 0, 1.10, 0, 0.20, 0.025, 0.20, side_tex=wood_side)

    # ---- 金属ブラケット（左右）----
    for sx in (-1, 1):
        draw_box(canvas, metal_off, sx * 0.10, 1.19, 0, 0.026, 0.06, 0.078)
    # ピボット
    draw_box(canvas, metal_off, 0, 1.19, 0, 0.070, 0.042, 0.042)

    # ---- シャフト（ON：立ち上がり。テーパー 3 段）----
    y = 1.225
    for half, height in ((0.058, 0.14), (0.047, 0.13), (0.037, 0.12)):
        draw_box(canvas, metal_on, 0, y + height / 2, 0, half, height / 2, half)
        draw_box(canvas, metal_off, 0, y + height, 0, half + 0.007, 0.010, half + 0.007)
        y += height

    # ---- ノブ（4 段）----
    y += 0.012
    for half, height in ((0.085, 0.052), (0.115, 0.080), (0.078, 0.054), (0.046, 0.028)):
        draw_box(canvas, metal_on, 0, y + height / 2, 0, half, height / 2, half)
        y += height

    # 光沢（ノブ上面）
    d = ImageDraw.Draw(canvas)
    hx, hy = to_screen(-0.05, y - 0.005, -0.05)
    d.ellipse([hx - 7, hy - 4, hx + 7, hy + 4], fill=(240, 255, 255, 215))
    # レバー頂点のきらめき
    tx, ty = to_screen(0, y + 0.01, 0)
    d.ellipse([tx - 5, ty - 5, tx + 5, ty + 5], fill=(186, 250, 252, 120))

    canvas.save(os.path.join(OUT_DIR, "physicalizer.png"))
    print("generated:", os.path.join(OUT_DIR, "physicalizer.png"))


# ----------------------------------------------------------------------
# 2. 接着剤（3 ブロック＋接着ライン＋範囲枠）
# ----------------------------------------------------------------------
def render_adhesive():
    global SCALE, ORIGIN_Y, H
    SCALE, ORIGIN_Y, H = 126, 0.60, 400
    adhesive_icon = load(os.path.join(TEX_ITEM, "adhesive.png"))
    stone = make_stone()
    planks = make_planks()

    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)

    # 3 ブロックを少し離して配置（接着前の状態が分かるように）
    offsets = [(-0.78, 0.0), (0.0, 0.0), (0.78, 0.0)]
    textures = [stone, planks, stone]
    half = 0.36

    for (ox, oz), tex in zip(offsets, textures):
        draw_box(canvas, tex, ox, 0.5, oz, half, half, half, steps=80)
        # 選択枠（黄色）
        x0, x1 = ox - half - 0.02, ox + half + 0.02
        y0, y1 = 0.5 - half - 0.02, 0.5 + half + 0.02
        z0, z1 = oz - half - 0.02, oz + half + 0.02
        corners = [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1),
                   (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
        edges = [(0, 1), (1, 2), (2, 3), (3, 0), (4, 5), (5, 6), (6, 7), (7, 4),
                 (0, 4), (1, 5), (2, 6), (3, 7)]
        for a, b in edges:
            pa, pb = to_screen(*corners[a]), to_screen(*corners[b])
            d.line([pa, pb], fill=(255, 226, 120, 210), width=2)

    # 接着ライン（ブロック同士をつなぐ光の帯）：ブロックの後に描く
    for i in range(len(offsets) - 1):
        x0 = offsets[i][0] + half
        x1 = offsets[i + 1][0] - half
        p0 = to_screen(x0, 0.5, 0.0)
        p1 = to_screen(x1, 0.5, 0.0)
        d.line([p0, p1], fill=(110, 240, 170, 90), width=13)
        d.line([p0, p1], fill=(160, 252, 200, 235), width=6)
        # 中央の光点
        mx, my = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
        d.ellipse([mx - 6, my - 4, mx + 6, my + 4], fill=(220, 255, 235, 240))

    # 接着剤のアイテム（右上）
    paste_item(canvas, adhesive_icon, W * 0.80, H * 0.20, scale=1.15)

    canvas.save(os.path.join(OUT_DIR, "adhesive.png"))
    print("generated:", os.path.join(OUT_DIR, "adhesive.png"))


# ----------------------------------------------------------------------
# 素材テクスチャ（バニラ風の簡易ノイズ／木目）
# ----------------------------------------------------------------------
def make_stone():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = ((x * 13 + y * 7) % 6) + ((x * x + y * 3) % 4)
            v = 118 + n * 6
            img.putpixel((x, y), (v, v, v, 255))
    return img


def make_planks():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            wave = ((x * 5 + y) % 7)
            v = wave - 3
            img.putpixel((x, y), (min(255, 168 + v * 6), min(255, 132 + v * 5),
                                  min(255, 82 + v * 4), 255))
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    render_physicalizer()
    render_adhesive()
    # 一時ファイルを残さない


if __name__ == "__main__":
    main()
