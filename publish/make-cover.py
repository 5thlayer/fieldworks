# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Temporary cover and icon, in the suite's style (Pipeworks' and Craftworks' make-cover.py), until an
# in-game screenshot or an artist's art replaces them. Run from the repo root with Pillow installed.
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

T = 'src/main/resources/assets/fieldworks/textures/block/ore/'
BG = (24, 26, 32)
ACCENT = (214, 128, 72)  # the copper ore's own colour
FADE = 70  # 0 leaves the patches at full strength, 255 hides them
PATCHES = [('iron', 0.18, 0.35), ('copper', 0.42, 0.75), ('coal', 0.62, 0.25),
           ('uranium', 0.85, 0.6), ('stone', 0.05, 0.85), ('sulfur', 0.98, 0.15)]


def tile(resource, stage, px):
    return Image.open(f'{T}{resource}_stage{stage}.png').convert('RGBA').resize((px, px), Image.NEAREST)


def field(W, H, px, seed):
    # ore patches on bare stone, each richest at its centre, as a mined field looks
    rng = random.Random(seed)
    img = Image.new('RGBA', (W, H))
    cols, rows = W // px + 1, H // px + 1
    for cy in range(rows):
        for cx in range(cols):
            best = None
            for res, fx, fy in PATCHES:
                d = math.hypot(cx - fx * cols, cy - fy * rows) / (0.22 * max(cols, rows))
                d += rng.uniform(-0.12, 0.12)
                if d < 1 and (best is None or d < best[1]):
                    best = (res, d)
            res, stage = (best[0], min(7, int(best[1] * 8))) if best else ('stone', 7)
            img.paste(tile(res, stage, px), (cx * px, cy * px))
    return img


def font(names, size):
    for n in names:
        if os.path.exists(n):
            return ImageFont.truetype(n, size)
    raise SystemExit('needs one of ' + ', '.join(names))


def make(W, H, out, title_size, px):
    img = field(W, H, px, 5)
    img = Image.alpha_composite(img, Image.new('RGBA', (W, H), BG + (FADE,)))
    cy, bh = H // 2, int(title_size * 1.6)
    band = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(band).rectangle([0, cy - bh // 2, W, cy + bh // 2], fill=(16, 17, 22, 225))
    img = Image.alpha_composite(img, band)
    d = ImageDraw.Draw(img)
    lt = max(3, title_size // 25)
    d.rectangle([0, cy - bh // 2, W, cy - bh // 2 + lt], fill=ACCENT)
    d.rectangle([0, cy + bh // 2 - lt, W, cy + bh // 2], fill=ACCENT)
    f1 = font(['/System/Library/Fonts/Supplemental/Impact.ttf', '/usr/share/fonts/TTF/impact.ttf',
               os.path.expanduser('~/.local/share/fonts/msfonts/impact.ttf')], title_size)
    f2 = font(['/System/Library/Fonts/Supplemental/Arial Black.ttf', '/usr/share/fonts/TTF/ariblk.ttf',
               os.path.expanduser('~/.local/share/fonts/msfonts/ariblk.ttf')], int(title_size * 0.62))
    a, w = 'FIELD', 'works'
    b1, b2 = d.textbbox((0, 0), a, font=f1), d.textbbox((0, 0), w, font=f2)
    w1, w2, h1, h2 = b1[2] - b1[0], b2[2] - b2[0], b1[3] - b1[1], b2[3] - b2[1]
    pad, gap, sh = int(title_size * 0.16), int(title_size * 0.1), max(3, title_size // 20)
    bw = w2 + 2 * pad
    x = (W - (w1 + gap + bw)) // 2
    y = cy - h1 // 2 - b1[1]
    d.text((x + sh - b1[0], y + sh), a, font=f1, fill=(0, 0, 0))
    d.text((x - b1[0], y), a, font=f1, fill=ACCENT)
    bx, bt = x + w1 + gap, cy - h1 // 2
    d.rounded_rectangle([bx + sh, bt + sh, bx + bw + sh, bt + h1 + sh], radius=pad, fill=(0, 0, 0))
    d.rounded_rectangle([bx, bt, bx + bw, bt + h1], radius=pad, fill=(240, 240, 236))
    d.text((bx + pad - b2[0], bt + (h1 - h2) // 2 - b2[1]), w, font=f2, fill=BG)
    img.convert('RGB').save(out)


make(1280, 640, 'publish/fieldworks-cover.png', 150, 64)
make(512, 512, 'publish/fieldworks-icon.png', 80, 48)
