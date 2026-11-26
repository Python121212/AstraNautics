#!/usr/bin/env python3
"""Physicalizer ブロックの 16x16 テクスチャを生成するスクリプト。

- images/physicalizer_block.png           … 生成した PNG（原本）
- src/main/resources/assets/astranautics/textures/block/physicalizer.png … MOD 用テクスチャ
- images/physicalizer_block_preview.png   … 8倍拡大のプレビュー（確認用）
"""
import os

from PIL import Image

PALETTE = {
    ".": (0, 0, 0, 0),
    "s": (74, 50, 26, 255),     # 真鍮の影
    "b": (150, 106, 48, 255),   # 真鍮
    "h": (214, 172, 96, 255),   # 真鍮ハイライト
    "d": (22, 38, 46, 255),     # 内側の暗い金属
    "e": (34, 60, 72, 255),     # 内側の縁
    "t": (38, 126, 140, 255),   # 青緑
    "c": (86, 214, 222, 255),   # シアン
    "C": (176, 250, 252, 255),  # 明るいシアン
    "W": (240, 255, 255, 255),  # 光の芯
    "k": (12, 22, 28, 255),     # 最暗部
}

SIZE = 16
rows = [["b"] * SIZE for _ in range(SIZE)]


def put(x, y, ch):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        rows[y][x] = ch


# --- 外枠（真鍮）とハイライト -------------------------------------------
for i in range(SIZE):
    put(i, 0, "h" if 1 <= i <= SIZE - 2 else "b")   # 上端はハイライト
    put(i, SIZE - 1, "s")                            # 下端は影
    put(0, i, "h" if 1 <= i <= SIZE - 2 else "b")    # 左端はハイライト
    put(SIZE - 1, i, "s")                            # 右端は影

# 四隅のボルト
for bx, by in ((1, 1), (2, 1), (1, 2), (13, 1), (14, 1), (14, 2),
               (1, 13), (1, 14), (2, 14), (14, 13), (13, 14), (14, 14)):
    put(bx, by, "b" if (bx + by) % 2 else "h")

# --- 内側の凹み ---------------------------------------------------------
for y in range(2, 14):
    for x in range(2, 14):
        put(x, y, "e")
# 内側の壁の陰影
for i in range(3, 13):
    put(i, 2, "d")
    put(2, i, "d")
    put(i, 13, "d")
    put(13, i, "d")
for y in range(3, 13):
    for x in range(3, 13):
        put(x, y, "d")

# --- 中央のコア（円形に光る） -------------------------------------------
cx, cy = 7.5, 7.5
for y in range(SIZE):
    for x in range(SIZE):
        if not (2 <= x <= 13 and 2 <= y <= 13):
            continue
        dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        # 左上からの光を意識して少しだけ中心をずらす
        if dist <= 1.2:
            put(x, y, "W")
        elif dist <= 2.3:
            put(x, y, "C")
        elif dist <= 3.3:
            put(x, y, "c")
        elif dist <= 4.0:
            put(x, y, "t")

# --- コアから伸びる配線（上下左右） --------------------------------------
for i in range(2, 5):
    put(7, i, "t" if i != 3 else "c")
    put(8, SIZE - 1 - i, "t" if i != 3 else "c")
for i in range(2, 5):
    put(i, 7, "t" if i != 3 else "c")
    put(SIZE - 1 - i, 8, "t" if i != 3 else "c")

# 配線の根本を少し明るく
for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
    put(x, y, "C")

# コア左上のきらめき
put(5, 5, "W")
put(10, 10, "k")  # 右下に落ちる影

img = Image.new("RGBA", (SIZE, SIZE))
for y in range(SIZE):
    for x in range(SIZE):
        img.putpixel((x, y), PALETTE[rows[y][x]])

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.makedirs(os.path.join(root, "images"), exist_ok=True)
tex_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/block")
os.makedirs(tex_dir, exist_ok=True)

img.save(os.path.join(root, "images/physicalizer_block.png"))
img.save(os.path.join(tex_dir, "physicalizer.png"))
img.resize((SIZE * 8, SIZE * 8), Image.NEAREST).save(
    os.path.join(root, "images/physicalizer_block_preview.png")
)
print("generated:", os.path.join(root, "images/physicalizer_block.png"))


# ----------------------------------------------------------------------
# 物理コア（空中で物理化したときに出現するブロック）
# ----------------------------------------------------------------------
CORE_PALETTE = {
    "k": (10, 22, 30, 255),
    "d": (20, 52, 64, 255),
    "t": (34, 118, 132, 255),
    "c": (86, 214, 222, 255),
    "C": (170, 248, 250, 255),
    "W": (240, 255, 255, 255),
}

core = Image.new("RGBA", (SIZE, SIZE))
for y in range(SIZE):
    for x in range(SIZE):
        # 上下左右の縁を暗く、中央を明るくした結晶風のグラデーション
        dx = abs(x - 7.5)
        dy = abs(y - 7.5)
        dist = (dx * dx + dy * dy) ** 0.5
        if dist > 7.0:
            ch = "k"
        elif dist > 5.4:
            ch = "d"
        elif dist > 3.6:
            ch = "t"
        elif dist > 2.0:
            ch = "c"
        elif dist > 0.9:
            ch = "C"
        else:
            ch = "W"
        # 菱形のハイライト
        if (x + y) % 9 == 0 and 2 <= x <= 13 and 2 <= y <= 13 and dist <= 5.4:
            ch = "C" if ch in ("t", "d") else ch
        core.putpixel((x, y), CORE_PALETTE[ch])

core.save(os.path.join(root, "images/physics_core.png"))
core.save(os.path.join(tex_dir, "physics_core.png"))
core.resize((SIZE * 8, SIZE * 8), Image.NEAREST).save(
    os.path.join(root, "images/physics_core_preview.png")
)
print("generated:", os.path.join(root, "images/physics_core.png"))


# ----------------------------------------------------------------------
# レバー（物理演算 ON：上 / OFF：下）の状態別 PNG
# ----------------------------------------------------------------------
LEVER_PALETTE = {
    ".": (0, 0, 0, 0),
    "s": (66, 44, 22, 255),      # 真鍮の影
    "b": (148, 104, 46, 255),    # 真鍮
    "h": (216, 176, 100, 255),   # ハイライト
    "d": (24, 40, 48, 255),      # 台座の暗い面
    "e": (36, 62, 74, 255),
    "t": (38, 126, 140, 255),    # 青緑
    "c": (86, 214, 222, 255),    # ON のシアン
    "C": (186, 250, 252, 255),
    "g": (120, 132, 138, 255),   # OFF の灰色
    "G": (176, 188, 194, 255),
    "k": (10, 20, 26, 255),
}


def make_lever(on: bool):
    """レバーの 16x16 テクスチャを作る。on=True で上（ON）、False で下（OFF）。"""
    px = [["." for _ in range(SIZE)] for _ in range(SIZE)]

    def p(x, y, ch):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            px[y][x] = ch

    # 台座（下 5 行の金属ベース）
    for y in range(11, 16):
        for x in range(1, 15):
            p(x, y, "b")
    for x in range(1, 15):
        p(x, 11, "h")
        p(x, 15, "s")
    for y in range(12, 15):
        p(1, y, "h")
        p(14, y, "s")
    # 台座の溝
    for x in range(3, 13):
        p(x, 13, "d")
    # ボルト
    for x in (2, 13):
        p(x, 12, "h")
        p(x, 14, "s")

    # 軸（支点）
    p(7, 10, "e"); p(8, 10, "e")
    p(7, 11, "d"); p(8, 11, "d")

    # レバー本体（棒）：ON なら上へ、OFF なら右へ倒す
    if on:
        # 右上がりに伸びる棒（2px 幅）
        shaft = [(8, 9), (9, 8), (9, 7), (10, 6), (10, 5), (11, 4)]
        knob = (11, 3)
        glow, glow_hi = "c", "C"
    else:
        # 右へ倒れた棒（2px 幅）
        shaft = [(9, 9), (10, 9), (11, 9), (12, 9)]
        knob = (13, 9)
        glow, glow_hi = "g", "G"

    for (x, y) in shaft:
        p(x, y, "h")
        p(x, y + 1, "s")

    # ノブ（丸い握り）
    kx, ky = knob
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if abs(dx) + abs(dy) == 2:
                continue
            p(kx + dx, ky + dy, glow)
    p(kx, ky, glow_hi)
    p(kx - 1, ky - 1, glow_hi)

    # 状態ランプ（台座中央）
    p(6, 12, glow if on else "g")
    p(7, 12, glow_hi if on else "G")
    p(8, 12, glow if on else "g")

    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            img.putpixel((x, y), LEVER_PALETTE[px[y][x]])
    return img


item_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/item")
os.makedirs(item_dir, exist_ok=True)

for state, name in ((True, "lever_on"), (False, "lever_off")):
    lever = make_lever(state)
    lever.save(os.path.join(root, f"images/{name}.png"))
    lever.resize((SIZE * 8, SIZE * 8), Image.NEAREST).save(
        os.path.join(root, f"images/{name}_preview.png")
    )
    print("generated:", os.path.join(root, f"images/{name}.png"))

# ブロック側テクスチャ（物理化ブロックの上に表示するレバー）
block_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/block")
os.makedirs(block_dir, exist_ok=True)
make_lever(True).save(os.path.join(block_dir, "lever_on.png"))
make_lever(False).save(os.path.join(block_dir, "lever_off.png"))

# アイテム用の 16x16（物理化装置のアイコンは ON＝上向き）
make_lever(True).save(os.path.join(item_dir, "physicalizer.png"))

# 「物理化されたブロック」のイメージ（石ブロック + レバー合成）
def make_physicalized_icon():
    img = Image.new("RGBA", (SIZE, SIZE))
    stone_dark = (120, 120, 120, 255)
    stone = (140, 140, 140, 255)
    stone_light = (162, 162, 162, 255)
    for y in range(SIZE):
        for x in range(SIZE):
            n = ((x * 7 + y * 13) % 5)
            img.putpixel((x, y), stone_light if n == 0 else (stone if n < 3 else stone_dark))
    # 左上に光沢
    for i in range(3, 7):
        img.putpixel((i, 2), stone_light)
    # 上に小さくレバー（ON）を合成
    lever = make_lever(True).resize((8, 8), Image.NEAREST)
    img.alpha_composite(lever, (4, 0))
    return img


phys_icon = make_physicalized_icon()
phys_icon.save(os.path.join(root, "images/physicalized.png"))
phys_icon.resize((SIZE * 8, SIZE * 8), Image.NEAREST).save(
    os.path.join(root, "images/physicalized_preview.png")
)
print("generated:", os.path.join(root, "images/physicalized.png"))

# 参考: 2 状態を並べたシート（README 用・拡大）
sheet = Image.new("RGBA", (SIZE * 2 + 2, SIZE), (0, 0, 0, 0))
sheet.paste(make_lever(True), (0, 0))
sheet.paste(make_lever(False), (SIZE + 2, 0))
sheet.resize(((SIZE * 2 + 2) * 8, SIZE * 8), Image.NEAREST).save(
    os.path.join(root, "images/lever_on_off_preview.png")
)
print("generated:", os.path.join(root, "images/lever_on_off_preview.png"))
