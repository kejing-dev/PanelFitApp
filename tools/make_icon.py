"""Renders the launcher icon (res/drawable/ic_launcher_foreground.xml on @color/ic_launcher_background) to a 512 px PNG
for the factory (apps/panel-fit/icon.png). Same shapes as the vector, viewport 108 -> 512.
Usage: python tools/make_icon.py <out.png>"""
import sys

from PIL import Image, ImageDraw

BG = (0x4B, 0x5D, 0x8C)
WHITE = (255, 255, 255)
S = 512 / 108.0
SS = 4  # supersampling


def box(d, x0, y0, x1, y1, fill, r=0.0):
    k = S * SS
    if r > 0:
        d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], radius=r * k, fill=fill)
    else:
        d.rectangle([x0 * k, y0 * k, x1 * k, y1 * k], fill=fill)


def main(out):
    size = 512 * SS
    img = Image.new("RGBA", (size, size), BG + (255,))
    back = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    box(ImageDraw.Draw(back), 27, 28, 71, 62, WHITE + (128,), 4)
    img = Image.alpha_composite(img, back)
    d = ImageDraw.Draw(img)
    box(d, 37, 38, 81, 72, WHITE + (255,), 4)
    box(d, 43, 44, 71, 49, BG + (255,))
    box(d, 43, 53, 64, 56.6, BG + (255,))
    box(d, 43, 60.4, 58, 63, BG + (255,))
    box(d, 58, 76, 84, 84, WHITE + (255,))
    for x, h in ((61, 4), (65.5, 2.6), (70, 4), (74.5, 2.6), (79, 4)):
        box(d, x, 76, x + 1.4, 76 + h, BG + (255,))
    img.resize((512, 512), Image.LANCZOS).convert("RGB").save(out)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "icon.png")
