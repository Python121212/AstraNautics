#!/usr/bin/env python3
"""Generate the 16x16 block textures for AstraNautics (pure Python, no Pillow).

Writes:
  textures/block/physics_assembler.png          anchor, physics OFF (dormant)
  textures/block/physics_assembler_active.png   anchor, physics ON (lit rings)
  textures/block/physics_switch_off.png      toggle switch, OFF
  textures/block/physics_switch_on.png       toggle switch, ON

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


def write_png(path: str, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("4B", *px) for px in row) for row in pixels
    )

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def clamp(value: float) -> int:
    return max(0, min(255, int(round(value))))


def mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))  # type: ignore[return-value]


def anchor_texture(active: bool) -> list[list[tuple[int, int, int, int]]]:
    """Dark metal cube with concentric rings and a glowing core."""
    base = (26, 20, 38)
    edge = (46, 34, 62)
    ring_cold = (92, 74, 128)
    ring_hot = (128, 246, 255)
    core_hot = (206, 250, 255)

    pixels = []
    center = (SIZE - 1) / 2.0
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            dx, dy = x - center, y - center
            dist = math.hypot(dx, dy)
            shade = 1.0 - 0.35 * (dist / center) if dist <= center else 0.65
            color = mix(base, edge, 0.15 + 0.85 * (1.0 - shade))

            # bevel border
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                color = mix(color, (18, 14, 26), 0.7)
            elif x in (1, SIZE - 2) or y in (1, SIZE - 2):
                color = mix(color, (60, 46, 82), 0.35)

            # concentric rings
            for ring_radius in (3.2, 5.4, 7.4):
                if abs(dist - ring_radius) < 0.55:
                    t = 0.85 if active else 0.55
                    color = mix(color, ring_hot if active else ring_cold, t)

            # core
            if dist < 1.8:
                color = mix(color, core_hot if active else ring_cold, 0.95 if active else 0.5)
            elif dist < 2.6:
                color = mix(color, ring_hot if active else ring_cold, 0.5)

            # faint star speckles
            if (x * 7 + y * 13) % 29 == 0:
                color = mix(color, (200, 210, 255), 0.25 if active else 0.12)

            row.append((color[0], color[1], color[2], 255))
        pixels.append(row)
    return pixels


def switch_texture(on: bool) -> list[tuple[list[tuple[int, int, int, int]]]]:
    """Brushed metal plate with a round indicator lamp and a lever slot."""
    plate = (74, 78, 86)
    plate_dark = (44, 47, 53)
    plate_light = (104, 109, 119)
    lamp_off = (94, 34, 34)
    lamp_on = (86, 255, 150)
    lamp_glow = (198, 255, 222)

    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            # brushed metal base
            noise = ((x * 31 + y * 17) % 11) / 11.0
            color = mix(plate, plate_light, 0.20 * noise)
            color = mix(color, plate_dark, 0.15 * (y / SIZE))

            # frame
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                color = mix(plate_dark, (24, 25, 28), 0.6)
            elif x in (1, SIZE - 2) or y in (1, SIZE - 2):
                color = mix(color, plate_light, 0.35)

            # vertical lever slot in the middle-left
            if 5 <= x <= 6 and 3 <= y <= 12:
                color = mix(plate_dark, (16, 16, 20), 0.8)
            if x == 7 and 3 <= y <= 12:
                color = mix(color, plate_light, 0.25)

            # lever knob: up when ON, down when OFF
            knob_y = 4 if on else 11
            if abs(x - 5.5) <= 1.6 and abs(y - knob_y) <= 1.6:
                color = (176, 180, 190) if on else (126, 130, 140)

            # indicator lamp on the right
            dist = math.hypot(x - 11.5, y - 8.0)
            if dist < 2.9:
                color = mix(color, lamp_on if on else lamp_off, 0.9)
            elif dist < 3.6:
                color = mix(color, lamp_glow if on else (60, 24, 24), 0.5)

            row.append((color[0], color[1], color[2], 255))
        pixels.append(row)
    return pixels


def main() -> int:
    write_png(os.path.join(OUT_DIR, "physics_assembler.png"), anchor_texture(active=False))
    write_png(os.path.join(OUT_DIR, "physics_assembler_active.png"), anchor_texture(active=True))
    write_png(os.path.join(OUT_DIR, "physics_switch_off.png"), switch_texture(on=False))
    write_png(os.path.join(OUT_DIR, "physics_switch_on.png"), switch_texture(on=True))
    print(f"wrote 4 textures to {os.path.relpath(OUT_DIR)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
