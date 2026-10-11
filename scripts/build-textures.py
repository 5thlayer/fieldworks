#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Build the ore blocks' stage sprites and the three item sprites.

A block shows one of eight sprites for what is left in it. The ratios at which a block changes
stage are in fieldworks/ore/amounts.json; what each stage draws is an even step: stage `i` of `n`
keeps `(n - i) / n` of the full sprite's ore, from the outside in, so a thinning block keeps its
core and each stage is a subset of the one before.

Coal, copper, iron, sulfur and uranium wear Malcolm Riley's unused textures (CC BY 4.0, credited in
NOTICE), committed unmodified under data/art/. Stone is generated: tan boulders standing on the
ground, from a fixed seed. The items are his too: sulfur and raw uranium as they are, and the uranium
ingot his lead ingot in the green of the uranium ore's crystals.

    scripts/build-textures.py
    scripts/build-textures.py --check    # regenerate and diff
"""
import argparse
import colorsys
import json
import os
import random
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
AMOUNTS = os.path.join(ROOT, "src", "main", "resources", "fieldworks", "ore", "amounts.json")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "fieldworks", "textures")
ORE_OUT = os.path.join(TEXTURES, "block", "ore")
ITEM_OUT = os.path.join(TEXTURES, "item")
ART = os.path.join(ROOT, "data", "art")

SOURCED = {"coal": "ore_slade_coal.png", "copper": "ore_stone_copper_2.png",
           "iron": "ore_slade_iron.png", "sulfur": "ore_stone_brimstone.png",
           "uranium": "ore_stone_soul.png"}

# The items wear his sprites too. The uranium ingot's is recoloured to the uranium ore's green.
ITEM_ART = {"sulfur": "material_crystal_sulfur_fancy.png", "raw_uranium": "material_crystal_greenish.png",
            "uranium_ingot": "ingot_lead.png"}

# A source pixel is ore above the HSV saturation or below the value. The value catches a dark ore
# and a crystal's shadow, which must go with the crystal or a spent block keeps a dark hole. Coal
# and iron sit on slade, a dark stone, so their value is tighter; coal has no colour at all.
# Brimstone's nuggets sit in a yellow haze with a pale, faintly tinted rim, and both are ore: left
# behind, the haze yellows a spent block and the rim, copied into a hole, leaves a pale ghost. Its
# grey matrix is at most 2% saturated, the rim 3 to 5%.
ORE_PIXEL = {
    "coal": (1.1, 0.2),
    "copper": (0.25, 0.0),
    "iron": (0.25, 0.2),
    "sulfur": (0.025, 0.0),
    "uranium": (0.25, 0.3),
}

STONE = (122, 122, 122)

# Generated ore: the boulder's highlight, body and shadow. The shadow is darker than any matrix
# pixel, so each boulder stands up.
BOULDERS = {
    "stone": ((176, 156, 109), (155, 135, 88), (72, 64, 50)),
}
# One per stage, so each stage takes exactly one boulder.
BOULDER_COUNT = 8
BOULDER_SHAPES = [
    [(0, 0), (1, 0), (0, 1), (1, 1)],
    [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1)],
    [(0, 0), (1, 0), (0, 1), (1, 1), (1, 2)],
    [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)],
    [(0, 0), (1, 0), (2, 0), (1, 1), (2, 1)],
    [(0, 0), (1, 0), (0, 1)],
]

# One seed, so a rerun does not churn the binaries for no reason.
SEED = 20260905


def png(pixels):
    """A 16x16 RGBA PNG, as bytes."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, body):
        data = tag + body
        return struct.pack(">I", len(body)) + data + struct.pack(">I", zlib.crc32(data) & 0xFFFFFFFF)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def read_png(path):
    """A 16x16 8-bit RGBA PNG's pixels, as rows of [r, g, b, a]."""
    data = open(path, "rb").read()
    width, height, depth, kind = struct.unpack(">IIBB", data[16:26])
    if (width, height, depth, kind) != (16, 16, 8, 6):
        sys.exit(f"{path} is not a 16x16 8-bit RGBA PNG")
    idat, i = b"", 8
    while i < len(data):
        (length,) = struct.unpack(">I", data[i:i + 4])
        if data[i + 4:i + 8] == b"IDAT":
            idat += data[i + 8:i + 8 + length]
        i += 12 + length
    raw, stride, rows, prev = zlib.decompress(idat), 64, [], bytearray(64)
    for y in range(16):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b, c = prev[x], prev[x - 4] if x >= 4 else 0
            if kind == 1:
                line[x] = (line[x] + a) & 255
            elif kind == 2:
                line[x] = (line[x] + b) & 255
            elif kind == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([list(line[x * 4:x * 4 + 4]) for x in range(16)])
        prev = line
    return rows


def art(name):
    """One of the borrowed sprites under data/art/."""
    return read_png(os.path.join(ART, name))


def to_hsv(pixel):
    return colorsys.rgb_to_hsv(*(v / 255 for v in pixel[:3]))


def sourced_sprites(resource, ratios):
    """One sprite per stage from a borrowed full sprite: the ore shrinks toward its centre."""
    source = art(SOURCED[resource])
    saturation, value = ORE_PIXEL[resource]
    cells = [(x, y) for y in range(16) for x in range(16)]
    hsv = {c: to_hsv(source[c[1]][c[0]]) for c in cells}
    ore = [c for c in cells if hsv[c][1] > saturation or hsv[c][2] < value]
    stone = [c for c in cells if c not in ore]
    cx = sum(x for x, _ in ore) / len(ore)
    cy = sum(y for _, y in ore) / len(ore)
    # The most ore-like pixel near the centroid is the last to go, so the final stage still reads
    # as ore rather than as a shadow: the brightest coloured pixel, or for coal the darkest.
    coloured = [c for c in ore if hsv[c][1] > saturation]
    strength = (lambda c: hsv[c][2]) if coloured else (lambda c: 1 - hsv[c][2])
    centre = max(coloured or ore,
                 key=lambda c: (strength(c) - 0.1 * ((c[0] - cx) ** 2 + (c[1] - cy) ** 2) ** 0.5, -c[1], -c[0]))
    ore.sort(key=lambda c: ((c[0] - centre[0]) ** 2 + (c[1] - centre[1]) ** 2, c[1], c[0]))

    # The median of the nearest stone pixels by brightness, not the nearest one, so a filled hole
    # does not copy a highlight and read as a bright speck.
    def fill(c):
        near = sorted(stone, key=lambda s: ((s[0] - c[0]) ** 2 + (s[1] - c[1]) ** 2, s[1], s[0]))[:5]
        near.sort(key=lambda s: (hsv[s][2], s[1], s[0]))
        return source[near[2][1]][near[2][0]]

    out = []
    for stage in range(len(ratios)):
        pixels = [[p[:] for p in row] for row in source]
        for x, y in ore[kept(len(ore), stage, len(ratios)):]:
            pixels[y][x] = fill((x, y))
        out.append(png(pixels))
    return out


def kept(full, stage, stages):
    """How many of `full` ore pieces stage `stage` of `stages` keeps. Never zero, so a block still
    holding ore never draws as bare stone."""
    return -(-full * (stages - stage) // stages)


def shade(colour, delta):
    return [max(0, min(255, value + delta)) for value in colour] + [255]


def boulder_sprites(resource, ratios):
    """One sprite per stage: boulders lit from the upper left on the grey matrix, going whole and
    from the outside in."""
    light, body_colour, shadow_colour = BOULDERS[resource]
    rng = random.Random(f"{SEED}:{resource}")
    ground = [[shade(STONE, rng.randint(-9, 9)) for _ in range(16)] for _ in range(16)]
    taken, boulders = set(), []
    while len(boulders) < BOULDER_COUNT:
        shape = rng.choice(BOULDER_SHAPES)
        ox, oy = rng.randrange(15), rng.randrange(15)
        body = {(ox + dx, oy + dy) for dx, dy in shape}
        shadow = {(x + dx, y + dy) for x, y in body for dx, dy in ((1, 0), (0, 1), (1, 1))} - body
        footprint = body | shadow
        if any(not (0 <= x < 16 and 0 <= y < 16) for x, y in footprint):
            continue
        if any((x + dx, y + dy) in taken for x, y in footprint for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
            continue
        taken |= footprint
        drawn = {c: shade(shadow_colour, rng.randint(-6, 6)) for c in shadow}
        for x, y in body:
            lit = (x - 1, y) not in body and (x, y - 1) not in body
            drawn[(x, y)] = shade(light if lit else body_colour, rng.randint(-6, 6))
        cx = sum(x for x, _ in body) / len(body)
        cy = sum(y for _, y in body) / len(body)
        boulders.append(((cx - 7.5) ** 2 + (cy - 7.5) ** 2, drawn))
    boulders.sort(key=lambda b: b[0])

    out = []
    for stage in range(len(ratios)):
        pixels = [row[:] for row in ground]
        for _, drawn in boulders[:kept(len(boulders), stage, len(ratios))]:
            for (x, y), colour in drawn.items():
                pixels[y][x] = colour
        out.append(png(pixels))
    return out


def uranium_green():
    """The mean hue and saturation of the uranium ore's crystals: its coloured ore pixels."""
    saturation, _ = ORE_PIXEL["uranium"]
    crystals = [hsv for hsv in (to_hsv(p) for row in art(SOURCED["uranium"]) for p in row) if hsv[1] > saturation]
    return (sum(h for h, _, _ in crystals) / len(crystals), sum(s for _, s, _ in crystals) / len(crystals))


def colourised(rows, hue, saturation):
    """The sprite in one colour: each pixel takes the hue and saturation, and keeps its brightness
    and alpha."""
    def pixel(p):
        return [round(c * 255) for c in colorsys.hsv_to_rgb(hue, saturation, max(p[:3]) / 255)] + [p[3]]

    return [[pixel(p) for p in row] for row in rows]


def item_sprites():
    """Each item's sprite, by item name."""
    sprites = {name: art(source) for name, source in ITEM_ART.items()}
    sprites["uranium_ingot"] = colourised(sprites["uranium_ingot"], *uranium_green())
    return sprites


def build():
    with open(AMOUNTS, encoding="utf-8") as handle:
        by_key = json.load(handle)["resources"]
    files = {}
    for resource in sorted(by_key):
        ratios = by_key[resource]["stage_ratios"]
        if len(ratios) < 2:
            sys.exit(f"{resource} carries {len(ratios)} stage ratios; there is nothing to render")
        images = (sourced_sprites(resource, ratios) if resource in SOURCED
                  else boulder_sprites(resource, ratios))
        for stage, image in enumerate(images):
            files[os.path.join(ORE_OUT, f"{resource}_stage{stage}.png")] = image
    for name, pixels in item_sprites().items():
        files[os.path.join(ITEM_OUT, f"{name}.png")] = png(pixels)
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="regenerate and compare, without writing")
    args = parser.parse_args()

    files = build()
    if args.check:
        stale = [path for path, image in sorted(files.items())
                 if not os.path.exists(path) or open(path, "rb").read() != image]
        for path in stale:
            print(f"FAIL: {os.path.relpath(path, ROOT)} is missing or stale")
        if stale:
            return 1
        print(f"ok   {len(files)} sprites match amounts.json's stage ratios")
        return 0

    for path, image in sorted(files.items()):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "wb").write(image)
    print(f"wrote {len(files)} sprites")
    return 0


if __name__ == "__main__":
    sys.exit(main())
