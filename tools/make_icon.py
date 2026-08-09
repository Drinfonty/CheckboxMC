#!/usr/bin/env python3
"""Generates the Checkbox mod icon.

Drawn on a 32x32 grid and scaled up with nearest-neighbour, so the result is crisp
pixel art at any size rather than a blurry upscale - which is what sits well next to
Minecraft's own art in a mod list.

Usage: python3 tools/make_icon.py
"""

from PIL import Image, ImageDraw

SIZE = 32          # working grid
SCALE = 8          # 32 * 8 = 256px, the size mod menus want
OUT = "common/src/main/resources/assets/checkbox/icon.png"

# A slightly desaturated palette; pure #00FF00 reads as neon next to Minecraft's textures.
BOX_LIGHT = (226, 226, 226, 255)
BOX_DARK = (140, 140, 140, 255)
BOX_FILL = (58, 62, 68, 255)
SHADOW = (24, 26, 30, 255)
CHECK = (94, 214, 94, 255)
CHECK_DARK = (46, 138, 52, 255)


def box(draw, x0, y0, x1, y1, colour):
    draw.rectangle([x0, y0, x1, y1], fill=colour)


def stroke(draw, points, colour, width):
    """A chunky polyline drawn as squares, so it stays on the pixel grid."""
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        steps = max(abs(bx - ax), abs(by - ay))
        for i in range(steps + 1):
            x = round(ax + (bx - ax) * i / steps)
            y = round(ay + (by - ay) * i / steps)
            box(draw, x, y, x + width - 1, y + width - 1, colour)


def main():
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Everything sits inside a 3px margin. The tick is the widest element, so its reach
    # sets the composition: let it run to the canvas edge and the icon looks cropped.
    box(draw, 5, 9, 24, 28, SHADOW)

    # The checkbox: a filled square with a light top-left and darker bottom-right edge,
    # the same bevel Minecraft's own widgets use.
    box(draw, 3, 7, 22, 26, BOX_DARK)
    box(draw, 3, 7, 21, 25, BOX_LIGHT)
    box(draw, 5, 9, 20, 24, BOX_FILL)

    # The tick, overflowing the box's top-right so the icon is not a plain square. Its
    # end stops short of the canvas edge: the offset shadow copy reaches a pixel further
    # than the tick itself, which is what made it look cropped.
    tick = [(8, 17), (13, 22), (24, 3)]
    stroke(draw, [(x + 1, y + 1) for x, y in tick], CHECK_DARK, 4)
    stroke(draw, tick, CHECK, 4)

    image.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST).save(OUT)
    print(f"wrote {OUT} ({SIZE * SCALE}x{SIZE * SCALE})")


if __name__ == "__main__":
    main()
