#!/usr/bin/env python3
"""Physicalizer の等角図（アイソメトリック）プレビューを 1 枚だけ生成する。

MOD 側テクスチャ（assets/astranautics/textures/block/*.png）を読み込み、
レバー（台座＋持ち手）を 3D の等角投影で「組み立てた状態」に描いて
`images/physicalizer.png` に保存する。images/ にはこの 1 枚しか置かない。

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

# 等角図のパラメータ
SCALE = 160         # 1 ブロック = 160 px
W, H = 320, 320     # 出力キャンバス
COS30, SIN30 = 0.8660254, 0.5

# 3D 空間内での光源方向（左上前方）→ 面の明るさ
SHADE = {
    "top": 1.00,     # 上面
    "front": 0.80,   # z+ 面（左手前）
    "side": 0.58,    # x+ 面（右手前）
}


def project(x, y, z):
    """等角投影（x 右, y 上, z 手前）。"""
    px = (x - z) * COS30
    py = (x + z) * SIN30 - y
    return px, py


def load_tex(name):
    return Image.open(os.path.join(TEX_BLOCK, name)).convert("RGBA")


def texel(tex, tu, tv):
    """UV(0..1) からテクセル色を取得（バイリニアではなくニアレスト）。"""
    w, h = tex.size
    x = min(w - 1, max(0, int(tu * w)))
    y = min(h - 1, max(0, int(tv * h)))
    return tex.getpixel((x, y))


def shade(color, factor):
    r, g, b, a = color
    return (int(r * factor), int(g * factor), int(b * factor), a)


def draw_face(img, tex, origin, u_axis, v_axis, shade_key, uv=(0.0, 1.0, 1.0, 0.0), steps=22):
    """平面上の四角形をテクスチャで塗る（等角投影・ニアレスト補間）。"""
    u0, v0, u1, v1 = uv
    for i in range(steps + 1):
        for j in range(steps + 1):
            fu = i / steps
            fv = j / steps
            # 画面上の位置（連続座標）
            x = (origin[0] + u_axis[0] * fu + v_axis[0] * fv)
            z = (origin[2] + u_axis[2] * fu + v_axis[2] * fv)
            y = (origin[1] + u_axis[1] * fu + v_axis[1] * fv)
            px, py = project(x, y, z)
            sx = int(round(px * SCALE + W / 2))
            sy = int(round(py * SCALE + H / 2))
            color = texel(tex, u0 + (u1 - u0) * fu, v0 + (v1 - v0) * fv)
            if color[3] == 0:
                continue
            color = shade(color, SHADE[shade_key])
            size = 3
            for dx in range(-size // 2, size // 2 + 1):
                for dy in range(-size // 2, size // 2 + 1):
                    xx, yy = sx + dx, sy + dy
                    if 0 <= xx < W and 0 <= yy < H:
                        img.putpixel((xx, yy), color)


def draw_box(img, tex, cx, cy, cz, hx, hy, hz, uvs=None):
    """中心 (cx,cy,cz)、半径 (hx,hy,hz) の直方体を等角投影で描く。

    見えるのは「z+ 面（左手前）」「x+ 面（右手前）」「上面」の 3 面だけ。
    """
    uvs = uvs or {}

    # z+ 面（左手前）
    draw_face(img, tex,
              (cx - hx, cy + hy, cz + hz),
              (2 * hx, 0, 0), (0, -2 * hy, 0),
              "front", uvs.get("front", (0, 1, 1, 0)))
    # x+ 面（右手前）
    draw_face(img, tex,
              (cx + hx, cy + hy, cz + hz),
              (0, 0, -2 * hz), (0, -2 * hy, 0),
              "side", uvs.get("side", (0, 1, 1, 0)))
    # 上面
    draw_face(img, tex,
              (cx - hx, cy + hy, cz - hz),
              (2 * hx, 0, 0), (0, 0, 2 * hz),
              "top", uvs.get("top", (0, 1, 1, 0)))


def box_uv(u0, v0, u1, v1):
    return {"top": (u0, v0, u1, v1), "front": (u0, v0, u1, v1), "side": (u0, v0, u1, v1)}


def main():
    atlas = load_tex("lever_atlas.png")
    w, h = atlas.size
    # アトラスのセル（4 列 x 2 行）からパーツを切り出す
    def cell(col, row):
        cw, ch = w // 4, h // 2
        return atlas.crop((col * cw, row * ch, (col + 1) * cw, (row + 1) * ch))

    base_tex = cell(0, 0)          # 台座
    handle_on_tex = cell(1, 0)     # 持ち手 ON

    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # 台座（0.48 x 0.16 x 0.48）
    base_top = 0.16
    draw_box(canvas, base_tex, 0, base_top / 2, 0, 0.24, base_top / 2, 0.24)

    # 持ち手（ON：上に伸びる棒）
    shaft_h = 0.42
    draw_box(canvas, handle_on_tex, 0, base_top + shaft_h / 2, 0, 0.05, shaft_h / 2, 0.05)

    # ノブ（握り）：棒の先端に載る立方体
    knob_h = 0.14
    draw_box(canvas, handle_on_tex, 0, base_top + shaft_h + knob_h / 2, 0,
             0.12, knob_h / 2, 0.12)

    canvas.save(OUT)
    print("generated:", OUT)


if __name__ == "__main__":
    main()
