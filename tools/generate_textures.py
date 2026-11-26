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
