# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Temporary cover and icon, until an in-game screenshot or an artist's art replaces them. Run from
# the repo root with Pillow installed.
import math
import random

from PIL import Image, ImageDraw, ImageFont

T = 'src/main/resources/assets/fieldworks/textures/block/ore/'
BOLD = '/usr/share/fonts/noto/NotoSans-Black.ttf'
LIGHT = '/usr/share/fonts/noto/NotoSans-Bold.ttf'


def tile(resource, stage, px):
    return Image.open(f'{T}{resource}_stage{stage}.png').convert('RGBA').resize((px, px), Image.NEAREST)


def field(W, H, px, seed):
    """Ore patches on bare stone: each patch richest at its centre, as a mined field looks."""
    rng = random.Random(seed)
    img = Image.new('RGBA', (W, H))
    patches = [('iron', 0.18, 0.35), ('copper', 0.42, 0.75), ('coal', 0.62, 0.25),
               ('uranium', 0.85, 0.6), ('stone', 0.05, 0.85), ('sulfur', 0.98, 0.15)]
    cols, rows = W // px + 1, H // px + 1
    for cy in range(rows):
        for cx in range(cols):
            best, stage = None, 7
            for res, fx, fy in patches:
                d = math.hypot(cx - fx * cols, cy - fy * rows) / (0.22 * cols)
                d += rng.uniform(-0.12, 0.12)
                if d < 1 and (best is None or d < best[1]):
                    best = (res, d)
            if best:
                res, d = best
                stage = min(7, int(d * 8))
            else:
                res = 'stone'
            img.paste(tile(res, stage, px), (cx * px, cy * px))
    return img


def title(img, size):
    W, H = img.size
    band = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(band)
    bh = int(size * 1.7)
    d.rectangle([0, (H - bh) // 2, W, (H + bh) // 2], fill=(16, 17, 22, 215))
    img = Image.alpha_composite(img, band)
    d = ImageDraw.Draw(img)
    f1, f2 = ImageFont.truetype(BOLD, size), ImageFont.truetype(LIGHT, int(size * 0.62))
    a, w = 'FIELD', 'works'
    b1, b2 = d.textbbox((0, 0), a, font=f1), d.textbbox((0, 0), w, font=f2)
    w1, w2, h1, h2 = b1[2] - b1[0], b2[2] - b2[0], b1[3] - b1[1], b2[3] - b2[1]
    px, gap, s = int(size * 0.16), int(size * 0.1), max(3, size // 20)
    bw = w2 + 2 * px
    x = (W - (w1 + gap + bw)) // 2
    y = (H - h1) // 2 - b1[1]
    d.text((x + s - b1[0], y + s), a, font=f1, fill=(0, 0, 0))
    d.text((x - b1[0], y), a, font=f1, fill=(214, 128, 72))
    bx, bt = x + w1 + gap, (H - h1) // 2
    d.rounded_rectangle([bx + s, bt + s, bx + bw + s, bt + h1 + s], radius=px, fill=(0, 0, 0))
    d.rounded_rectangle([bx, bt, bx + bw, bt + h1], radius=px, fill=(240, 240, 236))
    d.text((bx + px - b2[0], bt + (h1 - h2) // 2 - b2[1]), w, font=f2, fill=(24, 26, 32))
    return img


title(field(1280, 640, 64, 5), 140).convert('RGB').save('publish/fieldworks-cover.png')
tile('copper', 0, 512).convert('RGB').save('publish/fieldworks-icon.png')
