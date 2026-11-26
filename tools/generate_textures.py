#!/usr/bin/env python3
"""Physicalizer ブロックの 16x16 テクスチャを生成するスクリプト。

MOD 側テクスチャのみを出力します（`images/` には等角図プレビュー 1 枚だけを置きます）:
- src/main/resources/assets/astranautics/textures/block/physicalizer.png
- src/main/resources/assets/astranautics/textures/block/physics_core.png
- src/main/resources/assets/astranautics/textures/block/lever_atlas.png
- src/main/resources/assets/astranautics/textures/item/physicalizer.png
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

img.save(os.path.join(tex_dir, "physicalizer.png"))

print("generated:", os.path.join(tex_dir, "physicalizer.png"))


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

core.save(os.path.join(tex_dir, "physics_core.png"))

print("generated:", os.path.join(tex_dir, "physics_core.png"))


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

block_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/block")
os.makedirs(block_dir, exist_ok=True)
for state, name in ((True, "lever_on"), (False, "lever_off")):
    make_lever(state).save(os.path.join(block_dir, name + ".png"))

# ブロック側テクスチャ（物理化ブロックの上に表示するレバー）


# アイテムアイコンは block/physicalizer.png（下で生成）を使う

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




# ----------------------------------------------------------------------
# 3D レバー用アトラス（1 枚の PNG に台座・持ち手 ON/OFF をまとめる）
#   レイアウト（16px セル × 4 列 × 2 行 = 64x32）
#     (0,0) 台座 / (1,0) 持ち手ON / (2,0) 持ち手OFF / (3,0) 予備
#   3D モデルはこの 1 枚の UV を切り替えて「組み立て」ます。
# ----------------------------------------------------------------------
ATLAS_COLS = 4
ATLAS_ROWS = 2


def make_wood_top():
    """台座の上面用：ごく薄い木目だけ（点状の継ぎ目は入れない）。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            # ゆるやかな横方向の木目（最大 3 段階）
            wave = ((y * 3 + (x // 5)) % 9) // 3
            v = 176 - wave * 4 - int(y * 0.7)
            r = min(255, v + 20)
            g = min(255, int(v * 0.74))
            b = min(255, int(v * 0.44))
            img.putpixel((x, y), (r, g, b, 255))
    return img


def make_wood_side():
    """台座の側面用：縦方向の木目（横縞を出さない）。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    for x in range(SIZE):
        # 縦の濃淡（ゆるやかな周期）
        wave = ((x * 7) % 16) / 16.0
        base = 150 + int(22 * wave) - int(6 * ((x // 2) % 3))
        for y in range(SIZE):
            v = base - int(y * 0.55)
            r = min(255, v + 16)
            g = min(255, int(v * 0.72))
            b = min(255, int(v * 0.42))
            img.putpixel((x, y), (r, g, b, 255))
    # 下端を暗くして接地感を出す
    for y in range(SIZE - 3, SIZE):
        for x in range(SIZE):
            r, g, b, a = img.getpixel((x, y))
            img.putpixel((x, y), (int(r * 0.7), int(g * 0.7), int(b * 0.7), a))
    return img


def make_handle(color_main, color_hi, color_dark, accent=(245, 255, 255, 255)):
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            d = abs(x - 7.5) / 7.5
            if d > 0.75:
                c = color_dark
            elif d > 0.35:
                c = color_main
            else:
                c = color_hi
            img.putpixel((x, y), c + (255,))
    for (x, y) in ((7, 1), (8, 1), (7, 2)):
        img.putpixel((x, y), accent)
    return img


def make_lever_atlas():
    """1 枚のアトラス PNG（64x32）に台座と持ち手をまとめる。"""
    atlas = Image.new("RGBA", (SIZE * ATLAS_COLS, SIZE * ATLAS_ROWS), (0, 0, 0, 0))
    atlas.paste(make_wood_top(), (0, 0))
    atlas.paste(make_wood_side(), (SIZE * 3, 0))
    atlas.paste(make_handle((86, 214, 222), (186, 250, 252), (26, 96, 110)), (SIZE, 0))
    atlas.paste(make_handle((150, 156, 162), (200, 208, 214), (86, 92, 98),
                            accent=(230, 236, 240, 255)), (SIZE * 2, 0))
    return atlas


atlas = make_lever_atlas()
atlas.save(os.path.join(block_dir, "lever_atlas.png"))
print("generated:", os.path.join(block_dir, "lever_atlas.png"),
      f"({atlas.width}x{atlas.height})")


# ----------------------------------------------------------------------
# 接着剤（Adhesive）のアイテムテクスチャ
#   ボンドのような「滴る接着剤の筒」＋光るシアンの帯
# ----------------------------------------------------------------------
def make_adhesive_item():
    px = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]

    def p(x, y, c):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            px[y][x] = c

    tube = (46, 60, 70, 255)
    tube_hi = (92, 112, 124, 255)
    tube_dark = (26, 36, 44, 255)
    cap = (214, 172, 96, 255)
    glow = (86, 214, 222, 255)
    glow_hi = (186, 250, 252, 255)
    drop = (140, 232, 240, 255)

    # 筒（やや斜めのボトル）
    for y in range(3, 11):
        for x in range(5, 11):
            p(x, y, tube)
    # ハイライト（左）
    for y in range(3, 11):
        p(5, y, tube_hi)
    # 影（右）
    for y in range(3, 11):
        p(10, y, tube_dark)
    # 上端のキャップ（真鍮）
    for x in range(5, 11):
        p(x, 2, cap)
    for x in range(6, 10):
        p(x, 1, cap)
    # シアンの帯（光る接着剤の中身が見える窓）
    for y in range(5, 7):
        for x in range(7, 10):
            p(x, y, glow)
    p(7, 5, glow_hi)
    # 注ぎ口
    for x in range(7, 10):
        p(x, 11, tube_dark)
    p(8, 12, tube)
    p(8, 13, tube_dark)
    # 滴り落ちる接着剤
    p(8, 14, drop)
    p(8, 15, drop)
    p(7, 14, glow)

    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            img.putpixel((x, y), px[y][x])
    return img


adhesive = make_adhesive_item()
adhesive.save(os.path.join(item_dir, "adhesive.png"))
print("generated:", os.path.join(item_dir, "adhesive.png"))


# ----------------------------------------------------------------------
# スラスター / 舵輪 / エンジン部品（16x16 ピクセルアート・陰影つき）
#   16px なので「面取り・リベット・グラデーション・発光」で立体感を出す
# ----------------------------------------------------------------------
import math


def _shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3] if len(c) > 3 else 255)


def _metal_base(top_light=1.0):
    """金属の下地：左上から光が当たる面取り＋リベット。"""
    steel = (104, 114, 124, 255)
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            # 左上から右下への僅かな勾配
            g = (x + y) / 30.0
            c = _shade(steel, 1.12 - g * 0.35)
            # 面取り（外周 1px）
            if x == 0 or y == 0:
                c = _shade(steel, 1.45)
            elif x == SIZE - 1 or y == SIZE - 1:
                c = _shade(steel, 0.55)
            elif x == SIZE - 2 or y == SIZE - 2:
                c = _shade(steel, 0.78)
            # 細かい傷（斜めの筋）
            if (x * 3 + y * 5) % 23 == 0:
                c = _shade(c, 0.86)
            img.putpixel((x, y), c)
    # 四隅のリベット
    for bx, by in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((bx, by), _shade(steel, 1.6))
        img.putpixel((bx + 1, by), _shade(steel, 1.25))
        img.putpixel((bx, by + 1), _shade(steel, 0.8))
    return img


def _plate_seams(img, seams):
    """金属板の継ぎ目（暗い線＋下に 1px の光沢）。"""
    for (x0, y0, x1, y1) in seams:
        for t in range(0, max(1, abs(x1 - x0) + abs(y1 - y0)) + 1):
            x = x0 + (1 if x1 > x0 else -1 if x1 < x0 else 0) * t
            y = y0 + (1 if y1 > y0 else -1 if y1 < y0 else 0) * t
            if 0 <= x < SIZE and 0 <= y < SIZE:
                img.putpixel((x, y), (40, 46, 52, 255))
                if y + 1 < SIZE:
                    img.putpixel((x, y + 1), (168, 178, 188, 255))
    return img


def make_thruster_textures():
    tex_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/block")

    flame_out = (150, 226, 240, 255)
    flame_core = (236, 252, 255, 255)
    glow = (86, 214, 222, 255)

    # ---- front：大きな噴射口（同心円のノズル）----
    front = _metal_base()
    _plate_seams(front, [(0, 5, 15, 5), (0, 10, 15, 10)])
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 2.0:
                front.putpixel((x, y), flame_core)
            elif d <= 3.0:
                front.putpixel((x, y), flame_out)
            elif d <= 4.0:
                front.putpixel((x, y), glow)
            elif d <= 4.6:
                front.putpixel((x, y), (36, 40, 46, 255))
            elif d <= 5.6:
                front.putpixel((x, y), (152, 162, 172, 255))
    # ノズルの光が漏れる演出
    for (x, y) in ((7, 1), (8, 1), (7, 2)):
        front.putpixel((x, y), (120, 226, 238, 255))
    front.save(os.path.join(tex_dir, "thruster_front.png"))

    # ---- top：グリル（排熱スリット）----
    top = _metal_base()
    for y in range(3, 13):
        for x in range(3, 13):
            dark = (y - 3) % 3 == 0
            top.putpixel((x, y), (44, 50, 56, 255) if dark else (128, 138, 148, 255))
    # スリットの中の熱
    for x in range(5, 11):
        top.putpixel((x, 7), (150, 96, 60, 255))
    _plate_seams(top, [(1, 1, 14, 1)])
    top.save(os.path.join(tex_dir, "thruster_top.png"))

    # ---- side：冷却ジャケット（横縞＋配管）----
    side = _metal_base()
    for y in range(2, 14):
        if y % 3 == 0:
            for x in range(2, 14):
                side.putpixel((x, y), (52, 58, 66, 255))
                if y + 1 < SIZE:
                    side.putpixel((x, y + 1), (150, 160, 170, 255))
    # 縦の配管
    for y in range(2, 14):
        side.putpixel((4, y), (176, 132, 70, 255))
        side.putpixel((11, y), (176, 132, 70, 255))
    side.save(os.path.join(tex_dir, "thruster_side.png"))

    # ---- 舵輪：木の台座＋真鍮の操舵輪 ----
    wood = (126, 88, 46, 255)
    wood_hi = (168, 122, 68, 255)
    wood_dark = (82, 54, 26, 255)
    brass = (214, 172, 96, 255)
    brass_hi = (246, 216, 150, 255)
    brass_dark = (140, 104, 48, 255)

    def wood_base():
        img = Image.new("RGBA", (SIZE, SIZE))
        for y in range(SIZE):
            for x in range(SIZE):
                # 縦の木目
                band = (x * 5 + y // 3) % 7
                c = wood if band < 4 else (wood_hi if band == 4 else wood_dark)
                if x == 0 or y == 0:
                    c = wood_hi
                elif x == SIZE - 1 or y == SIZE - 1:
                    c = wood_dark
                img.putpixel((x, y), c)
        return img

    helm_top = wood_base()
    # 台座の縁（真鍮の縁取り）
    for i in range(SIZE):
        helm_top.putpixel((i, 0), brass_hi)
        helm_top.putpixel((0, i), brass_hi)
        helm_top.putpixel((i, SIZE - 1), brass_dark)
        helm_top.putpixel((SIZE - 1, i), brass_dark)
    # 操舵輪：外リング（細く）＋8 本スポーク＋ハブ
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.9 <= d <= 6.9:
                helm_top.putpixel((x, y), brass_hi)
            elif 5.1 <= d < 5.9:
                helm_top.putpixel((x, y), brass)
            elif d < 5.1 and d > 2.2:
                helm_top.putpixel((x, y), (70, 46, 22, 255))
    for k in range(8):
        ang = k * math.pi / 4.0
        for r in range(1, 6):
            x = int(round(7.5 + math.cos(ang) * r))
            y = int(round(7.5 + math.sin(ang) * r))
            if 0 <= x < SIZE and 0 <= y < SIZE:
                helm_top.putpixel((x, y), brass if (x + y) % 3 else brass_hi)
    disc_top = ((7, 7), (8, 7), (7, 8), (8, 8))
    for (x, y) in disc_top:
        helm_top.putpixel((x, y), brass_hi)
    helm_top.save(os.path.join(tex_dir, "ship_helm_top.png"))

    helm_side = wood_base()
    # 木の板の継ぎ目＋鉄の帯
    for y in (5, 10):
        for x in range(SIZE):
            helm_side.putpixel((x, y), wood_dark)
            helm_side.putpixel((x, y + 1), wood_hi)
    for y in range(7, 9):
        for x in range(SIZE):
            helm_side.putpixel((x, y), brass if x % 5 else brass_dark)
    helm_side.save(os.path.join(tex_dir, "ship_helm_side.png"))

    helm_front = wood_base()
    # 前面：手前側の輪（弧）＋支柱
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 9.0)
            if 5.2 <= d <= 6.4 and y <= 9:
                helm_front.putpixel((x, y), brass_hi if y < 8 else brass)
    for x in (6, 9):
        for y in range(9, 13):
            helm_front.putpixel((x, y), brass_dark)
    for y in range(2, 5):
        helm_front.putpixel((7, y), brass)
        helm_front.putpixel((8, y), brass_dark)
    helm_front.save(os.path.join(tex_dir, "ship_helm_front.png"))

    print("generated: thruster_* / ship_helm_* textures (improved)")


make_thruster_textures()


# ----------------------------------------------------------------------
# エンジン部品（改良版：金属ベース＋モチーフ＋発光）
# ----------------------------------------------------------------------
def make_engine_part_textures():
    tex_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/block")
    brass = (208, 168, 96, 255)
    brass_hi = (246, 216, 150, 255)
    copper = (188, 116, 68, 255)
    cyan = (86, 214, 222, 255)
    orange = (246, 152, 74, 255)
    red = (204, 74, 58, 255)
    white = (244, 252, 255, 255)
    blue = (70, 132, 206, 255)

    def base():
        return _metal_base()

    def disc(img, cx, cy, r, color, edge=None):
        for y in range(SIZE):
            for x in range(SIZE):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    img.putpixel((x, y), color)
                elif edge is not None and d <= r + 1.0:
                    img.putpixel((x, y), edge)

    def ring(img, cx, cy, r0, r1, color):
        for y in range(SIZE):
            for x in range(SIZE):
                d = math.hypot(x - cx, y - cy)
                if r0 <= d <= r1:
                    img.putpixel((x, y), color)

    parts = {}

    # 燃焼室
    top = base(); side = base()
    ring(top, 7.5, 7.5, 3.0, 4.2, (40, 44, 50, 255))
    disc(top, 7.5, 7.5, 2.9, orange)
    disc(top, 7.5, 7.5, 1.9, (255, 198, 110, 255))
    disc(top, 7.5, 7.5, 1.0, white)
    for y in range(3, 13):
        for x in range(5, 11):
            band = (y // 2) % 2
            side.putpixel((x, y), orange if band else red)
    for y in range(3, 13):
        side.putpixel((4, y), (40, 44, 50, 255))
        side.putpixel((11, y), (40, 44, 50, 255))
    _plate_seams(side, [(2, 6, 13, 6)])
    parts["combustion_chamber"] = (top, side)

    # ノズル（末広がり）
    top = base(); side = base()
    ring(top, 7.5, 7.5, 2.4, 3.4, brass)
    ring(top, 7.5, 7.5, 3.4, 5.6, (58, 64, 72, 255))
    disc(top, 7.5, 7.5, 1.6, (26, 30, 34, 255))
    disc(top, 7.5, 7.5, 0.9, glow_color := (120, 200, 220, 255))
    for y in range(2, 14):
        w = 1 + (y - 2) // 2
        for x in range(8 - w, 8 + w):
            side.putpixel((x, y), (58, 64, 72, 255) if (x + y) % 3 else brass)
        side.putpixel((max(0, 8 - w - 1), y), (30, 34, 40, 255))
        side.putpixel((min(15, 8 + w), y), (120, 128, 138, 255))
    parts["nozzle"] = (top, side)

    # 吸気口
    top = base(); side = base()
    for i in range(2, 14, 3):
        for x in range(2, 14):
            top.putpixel((x, i), (38, 42, 48, 255))
            if i + 1 < SIZE:
                top.putpixel((x, i + 1), (150, 160, 170, 255))
        for y in range(2, 14):
            top.putpixel((i, y), (38, 42, 48, 255))
    ring(top, 7.5, 7.5, 1.4, 2.6, cyan)
    disc(top, 7.5, 7.5, 1.3, (26, 90, 104, 255))
    for y in range(3, 13):
        for x in range(4, 12):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 3.4:
                side.putpixel((x, y), cyan if d <= 1.6 else (44, 120, 136, 255))
    _plate_seams(side, [(2, 2, 13, 2), (2, 13, 13, 13)])
    parts["intake"] = (top, side)

    # タービン（羽根車）
    top = base(); side = base()
    disc(top, 7.5, 7.5, 5.2, (36, 40, 46, 255))
    for i in range(9):
        ang = i * math.pi / 4.5
        for r in range(1, 6):
            x = int(round(7.5 + math.cos(ang) * r))
            y = int(round(7.5 + math.sin(ang) * r))
            if 0 <= x < SIZE and 0 <= y < SIZE:
                top.putpixel((x, y), brass if (i % 2 == 0) else brass_hi)
    disc(top, 7.5, 7.5, 1.3, white)
    for y in range(4, 12):
        for x in range(4, 12):
            side.putpixel((x, y), brass if (x + y) % 2 else copper)
    ring(side, 7.5, 7.5, 3.2, 4.4, (40, 44, 50, 255))
    parts["turbine"] = (top, side)

    # 冷却フィン
    top = base(); side = base()
    for i in range(1, 15):
        for x in range(SIZE):
            if i % 2 == 0:
                top.putpixel((x, i), (150, 208, 232, 255) if i % 4 else (196, 238, 250, 255))
    for y in range(2, 14):
        for x in range(2, 14):
            side.putpixel((x, y), (168, 218, 240, 255) if y % 3 else (58, 66, 74, 255))
    _plate_seams(side, [(1, 1, 14, 1)])
    parts["cooling_fin"] = (top, side)

    # 酸化剤タンク（青）
    top = base(); side = base()
    ring(top, 7.5, 7.5, 4.2, 5.4, brass)
    disc(top, 7.5, 7.5, 4.1, blue)
    disc(top, 6.0, 6.0, 1.6, (156, 214, 250, 255))
    for y in range(2, 14):
        for x in range(3, 13):
            c = blue
            if y in (2, 3, 12, 13):
                c = brass
            elif x == 3 or x == 12:
                c = (40, 78, 132, 255)
            side.putpixel((x, y), c)
    for x in range(5, 11):
        side.putpixel((x, 5), (156, 214, 250, 255))
    parts["oxidizer_tank"] = (top, side)

    # 燃料タンク（橙）
    top = base(); side = base()
    ring(top, 7.5, 7.5, 4.2, 5.4, brass)
    disc(top, 7.5, 7.5, 4.1, (196, 108, 48, 255))
    disc(top, 6.0, 6.0, 1.6, (252, 206, 148, 255))
    for y in range(2, 14):
        for x in range(3, 13):
            c = (196, 108, 48, 255)
            if y in (2, 3, 12, 13):
                c = brass
            elif x == 3 or x == 12:
                c = (118, 60, 26, 255)
            side.putpixel((x, y), c)
    for x in range(5, 11):
        side.putpixel((x, 5), (252, 206, 148, 255))
    parts["fuel_tank"] = (top, side)

    for name, (top_img, side_img) in parts.items():
        top_img.save(os.path.join(tex_dir, f"engine_{name}_top.png"))
        side_img.save(os.path.join(tex_dir, f"engine_{name}_side.png"))
    print("generated: engine part textures (improved, %d parts)" % len(parts))


make_engine_part_textures()





# ----------------------------------------------------------------------
# 接着剤（3D モデル用アトラス）
#   1 枚の PNG（64x32）に 4 セルをまとめ、UV を切り替えて組み立てる
#     (0,0) キャップ上面 / (1,0) 金属の帯 / (2,0) ボトル側面 / (3,0) 接着剤の帯
# ----------------------------------------------------------------------
def make_adhesive_atlas():
    CELL = SIZE  # 16
    atlas = Image.new("RGBA", (CELL * 4, CELL * 2), (0, 0, 0, 0))

    # --- (0,0) 真鍮のキャップ上面 ---
    cap = Image.new("RGBA", (CELL, CELL))
    cap_hi = (246, 216, 150, 255)
    cap_mid = (206, 166, 96, 255)
    cap_low = (146, 108, 48, 255)
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 6.6:
                c = (60, 44, 20, 255)
            elif d > 5.2:
                c = cap_low
            elif d > 2.6:
                c = cap_mid
            else:
                c = cap_hi
            cap.putpixel((x, y), c)
    # 十字の溝
    for i in range(3, 13):
        cap.putpixel((i, 7), cap_low)
        cap.putpixel((7, i), cap_low)
    atlas.paste(cap, (0, 0))

    # --- (1,0) 金属の帯（バンド） ---
    band = Image.new("RGBA", (CELL, CELL))
    for y in range(CELL):
        for x in range(CELL):
            v = (186, 148, 84, 255)
            if y in (0, 1, 14, 15):
                v = (120, 92, 44, 255)
            elif y in (7, 8):
                v = (238, 206, 140, 255)
            elif (x * 3 + y) % 13 == 0:
                v = (150, 118, 62, 255)
            band.putpixel((x, y), v)
    # ボルト
    for bx in (3, 12):
        band.putpixel((bx, 4), (222, 230, 236, 255))
        band.putpixel((bx, 11), (84, 92, 100, 255))
    atlas.paste(band, (CELL, 0))

    # --- (2,0) ボトルの側面（樹脂＋シアンの窓） ---
    bottle = Image.new("RGBA", (CELL, CELL))
    resin_hi = (92, 118, 132, 255)
    resin = (54, 74, 86, 255)
    resin_low = (30, 44, 54, 255)
    glow = (86, 214, 222, 255)
    glow_hi = (200, 250, 255, 255)
    for y in range(CELL):
        for x in range(CELL):
            # 円柱の陰影（左が明るい）
            f = 1.0 - abs(x - 3.0) / 13.0
            c = resin_hi if f > 0.82 else (resin if f > 0.5 else resin_low)
            bottle.putpixel((x, y), c)
    # 残量が見える小さなシアンの窓（縦長のスリット）
    for y in range(7, 13):
        for x in range(6, 10):
            d = math.hypot(x - 7.5, y - 9.5)
            bottle.putpixel((x, y), glow if d < 1.9 else (40, 104, 118, 255))
    bottle.putpixel((7, 9), glow_hi)
    bottle.putpixel((8, 8), glow_hi)
    # ラベルの筋
    for x in range(2, 14):
        bottle.putpixel((x, 3), (214, 172, 96, 255))
        bottle.putpixel((x, 4), (150, 108, 48, 255))
    atlas.paste(bottle, (CELL * 2, 0))

    # --- (3,0) 接着剤の帯（ノズル／滴り） ---
    nozzle = Image.new("RGBA", (CELL, CELL))
    for y in range(CELL):
        for x in range(CELL):
            v = (120, 232, 240, 255) if (x + y) % 5 else (200, 250, 255, 255)
            nozzle.putpixel((x, y), v)
    # 滴り
    for y in range(4, 16):
        w = 1 + (y // 6)
        for x in range(8 - w, 8 + w):
            nozzle.putpixel((x, y), (86, 214, 222, 255))
    atlas.paste(nozzle, (CELL * 3, 0))

    item_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/item")
    atlas.save(os.path.join(item_dir, "adhesive_atlas.png"))
    print("generated: adhesive_atlas.png (%dx%d)" % (atlas.width, atlas.height))


make_adhesive_atlas()
