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
# スラスター（金属のノズル＋噴射口）と舵輪（木の操舵輪）
# ----------------------------------------------------------------------
def make_thruster_textures():
    """スラスターの top / front / side を作る。"""
    metal = (92, 100, 108, 255)
    metal_hi = (146, 156, 164, 255)
    metal_dark = (48, 54, 60, 255)
    glow = (120, 226, 240, 255)
    glow_hi = (206, 250, 254, 255)

    def base(nozzle):
        img = Image.new("RGBA", (SIZE, SIZE))
        for y in range(SIZE):
            for x in range(SIZE):
                v = metal
                if x == 0 or y == 0:
                    v = metal_hi
                elif x == SIZE - 1 or y == SIZE - 1:
                    v = metal_dark
                elif (x * 7 + y * 11) % 17 == 0:
                    v = metal_dark
                img.putpixel((x, y), v)
        # 外周のボルト
        for bx, by in ((2, 2), (13, 2), (2, 13), (13, 13)):
            img.putpixel((bx, by), metal_hi)
            img.putpixel((bx + 1, by), metal_dark)
        if nozzle:
            # 中央の噴射口（円形に光る）
            for y in range(SIZE):
                for x in range(SIZE):
                    dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                    if dist <= 1.6:
                        img.putpixel((x, y), glow_hi)
                    elif dist <= 2.8:
                        img.putpixel((x, y), glow)
                    elif dist <= 3.6:
                        img.putpixel((x, y), metal_dark)
        return img

    tex_dir = os.path.join(root, "src/main/resources/assets/astranautics/textures/block")
    base(False).save(os.path.join(tex_dir, "thruster_side.png"))
    base(True).save(os.path.join(tex_dir, "thruster_front.png"))

    # 上面：中央に排気グリル
    top = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            v = metal_hi if (y % 3 == 0) else metal
            if x == 0 or y == 0:
                v = metal_hi
            elif x == SIZE - 1 or y == SIZE - 1:
                v = metal_dark
            top.putpixel((x, y), v)
    for x in range(4, 12):
        top.putpixel((x, 7), metal_dark)
        top.putpixel((x, 8), metal_dark)
    top.save(os.path.join(tex_dir, "thruster_top.png"))

    # ---- 舵輪 ----
    wood = (132, 92, 48, 255)
    wood_hi = (176, 128, 72, 255)
    wood_dark = (88, 58, 28, 255)
    brass = (208, 168, 96, 255)

    def helm_base():
        img = Image.new("RGBA", (SIZE, SIZE))
        for y in range(SIZE):
            for x in range(SIZE):
                if x == 0 or y == 0:
                    v = wood_hi
                elif x == SIZE - 1 or y == SIZE - 1:
                    v = wood_dark
                else:
                    v = wood if ((y * 3 + x // 4) % 5) else wood_dark
                img.putpixel((x, y), v)
        return img

    side = helm_base()
    for y in range(6, 10):
        for x in range(2, 14):
            side.putpixel((x, y), brass if y in (6, 9) else wood_dark)
    side.save(os.path.join(tex_dir, "ship_helm_side.png"))

    top = helm_base()
    # 操舵輪（リング＋スポーク＋ハブ）
    cx = cy = 7.5
    for y in range(SIZE):
        for x in range(SIZE):
            r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 5.2 <= r <= 6.4:
                top.putpixel((x, y), brass)
            elif r <= 1.2:
                top.putpixel((x, y), brass)
    for i in range(-5, 6):
        top.putpixel((int(cx + i), int(cy)), brass)
        top.putpixel((int(cx), int(cy + i)), brass)
    top.save(os.path.join(tex_dir, "ship_helm_top.png"))

    front = helm_base()
    for y in range(4, 12):
        for x in range(4, 12):
            front.putpixel((x, y), brass if (x + y) % 3 else wood_dark)
    front.save(os.path.join(tex_dir, "ship_helm_front.png"))

    print("generated: thruster_* / ship_helm_* textures")


make_thruster_textures()
