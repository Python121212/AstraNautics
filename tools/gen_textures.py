#!/usr/bin/env python3
"""Generate the 16x16 block textures for AstraNautics (pure Python, no Pillow).

Writes (v3.2.0: the physics assembler block is gone, the switch is the only block):
  textures/block/physics_switch_off.png         switch, OFF
  textures/block/physics_switch_on.png          switch, ON

Run from the repository root:
    python3 tools/gen_textures.py
"""

from __future__ import annotations

import math
import os
import struct
import zlib

SIZE = 16
OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "astranautics", "textures", "block",
)

Color = tuple[int, int, int]
Pixel = tuple[int, int, int, int]
Canvas = list[list[Color]]


# --------------------------------------------------------------------------- png
def write_png(path: str, canvas: Canvas) -> None:
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("4B", r, g, b, 255) for (r, g, b) in row)
        for row in canvas
    )

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


# ------------------------------------------------------------------------ helpers
def clamp(value: float) -> int:
    return max(0, min(255, int(round(value))))


def mix(a: Color, b: Color, t: float) -> Color:
    t = max(0.0, min(1.0, t))
    return (clamp(a[0] + (b[0] - a[0]) * t),
            clamp(a[1] + (b[1] - a[1]) * t),
            clamp(a[2] + (b[2] - a[2]) * t))


def blank(color: Color) -> Canvas:
    return [[color for _ in range(SIZE)] for _ in range(SIZE)]


def put(canvas: Canvas, x: int, y: int, color: Color) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        canvas[y][x] = color


def rect(canvas: Canvas, x0: int, y0: int, x1: int, y1: int, color: Color) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(canvas, x, y, color)


def frame(canvas: Canvas, color: Color, inset: int = 0) -> None:
    rect(canvas, inset, inset, SIZE - 1 - inset, inset, color)
    rect(canvas, inset, SIZE - 1 - inset, SIZE - 1 - inset, SIZE - 1 - inset, color)
    rect(canvas, inset, inset, inset, SIZE - 1 - inset, color)
    rect(canvas, SIZE - 1 - inset, inset, SIZE - 1 - inset, SIZE - 1 - inset, color)


def disc(canvas: Canvas, cx: float, cy: float, radius: float, color: Color, blend: float = 1.0) -> None:
    for y in range(SIZE):
        for x in range(SIZE):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= radius:
                canvas[y][x] = mix(canvas[y][x], color, blend)


def ring(canvas: Canvas, cx: float, cy: float, radius: float, width: float,
         color: Color, blend: float = 1.0) -> None:
    for y in range(SIZE):
        for x in range(SIZE):
            distance = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if abs(distance - radius) <= width * 0.5:
                canvas[y][x] = mix(canvas[y][x], color, blend)


def rivets(canvas: Canvas, color: Color, light: Color) -> None:
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        canvas[y][x] = color
        canvas[y][x + (1 if x < 8 else -1)] = mix(color, light, 0.35)


def noise(canvas: Canvas, seed: int, strength: float) -> None:
    for y in range(SIZE):
        for x in range(SIZE):
            value = ((x * 31 + y * 17 + seed * 13) % 13) / 13.0
            edge = 1.0 - value * strength
            r, g, b = canvas[y][x]
            canvas[y][x] = (clamp(r * edge), clamp(g * edge), clamp(b * edge))


# ------------------------------------------------------------------------ switch
def switch(on: bool) -> Canvas:
    plate = (66, 70, 78)
    plate_dark = (38, 40, 46)
    plate_light = (104, 110, 120)
    metal = (166, 172, 184)
    led_off = (108, 32, 32)
    led_on = (96, 255, 148)
    led_glow = (206, 255, 226)

    canvas = blank(plate)
    for y in range(SIZE):
        for x in range(SIZE):
            brushed = ((x * 7 + y * 3) % 9) / 9.0
            canvas[y][x] = mix(plate, plate_light, 0.12 + 0.18 * brushed)

    noise(canvas, 3, 0.16)

    frame(canvas, (22, 24, 28))
    frame(canvas, plate_dark, 1)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, plate)
    frame(canvas, plate_light, 2)

    # recessed switch channel
    rect(canvas, 4, 3, 6, 12, plate_dark)
    rect(canvas, 5, 3, 5, 12, (18, 18, 22))
    rect(canvas, 3, 3, 3, 12, plate_light)

    # lever knob (top = ON, bottom = OFF)
    knob_y = 4 if on else 11
    rect(canvas, 4, knob_y - 1, 6, knob_y + 1, metal)
    put(canvas, 4, knob_y - 1, mix(metal, (255, 255, 255), 0.4))
    put(canvas, 6, knob_y + 1, mix(metal, (60, 62, 70), 0.5))

    # indicator lamp
    disc(canvas, 11.5, 8.0, 2.6, plate_dark, 0.8)
    disc(canvas, 11.5, 8.0, 2.1, led_on if on else led_off, 0.95)
    disc(canvas, 11.5, 8.0, 1.1, led_glow if on else mix(led_off, (255, 180, 180), 0.35), 0.85)
    if on:
        disc(canvas, 11.5, 8.0, 3.6, led_glow, 0.16)

    # tiny label notch under the lamp
    rect(canvas, 10, 12, 13, 12, mix(plate_dark, (0, 0, 0), 0.3))

    return canvas


def main() -> int:
    write_png(os.path.join(OUT_DIR, "physics_switch_off.png"), switch(on=False))
    write_png(os.path.join(OUT_DIR, "physics_switch_on.png"), switch(on=True))
    print(f"wrote 2 textures to {os.path.relpath(OUT_DIR)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
