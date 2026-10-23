#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Build the ore blocks' stage sprites, the item sprites, tar's, and the Harvesters' sprites and models.

A block shows one of eight sprites for what is left in it. The ratios at which a block changes
stage are in fieldworks/ore/amounts.json; what each stage draws is an even step: stage `i` of `n`
keeps `(n - i) / n` of the full sprite's ore, from the outside in, so a thinning block keeps its
core and each stage is a subset of the one before.

Coal, copper, iron, sulfur, uranium, diamond, gold, glowstone and quartz (his saltpeter ore, white
like it) wear Malcolm Riley's unused textures (CC BY 4.0, credited in NOTICE), committed unmodified
under data/art/. Redstone is his lapis ore turned red and netherite his anthracite ore turned a dark
brown, since the collection has neither. Stone is generated: tan boulders standing on the
ground, from a fixed seed. The items are his too: sulfur and raw uranium as they are, and the uranium
ingot his lead ingot in the green of the uranium ore's crystals. Tar's block and item are drawn here:
Fieldworks' own stand-ins until real art is drawn.

The Harvesters wear his sprites too, one to each block of the footprint, as Craftworks' Assemblers
do: his iron duct for the body, the tier's stripe round the top and across the roof (light grey for
tier 1, yellow for 2, the Assemblers' colours by rank), and a gearbox at the roof's centre. The
front, the output face, has the duct intake at its foot and the tier's default power at eye height
(ADR 0005): the smelter's front let into the duct for tier 1, as Craftworks' Refiner front is let
into its plating, and the copper coil for tier 2, made symmetric as Wireworks' Steam Turbine wears
it. The Harvesters' block models are written here as well, so --check also catches a hand edit to
one.

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
BLOCK_OUT = os.path.join(TEXTURES, "block")
ORE_OUT = os.path.join(BLOCK_OUT, "ore")
ITEM_OUT = os.path.join(TEXTURES, "item")
HARVESTER_OUT = os.path.join(BLOCK_OUT, "harvester")
MODELS = os.path.join(ROOT, "src", "main", "resources", "assets", "fieldworks", "models", "block")
BLOCKSTATES = os.path.join(ROOT, "src", "main", "resources", "assets", "fieldworks", "blockstates")
HARVESTER_SPRITE = "fieldworks:block/harvester/"
ART = os.path.join(ROOT, "data", "art")

SOURCED = {"coal": "ore_slade_coal.png", "copper": "ore_stone_copper_2.png",
           "iron": "ore_slade_iron.png", "sulfur": "ore_stone_brimstone.png",
           "uranium": "ore_stone_soul.png", "diamond": "ore_slade_diamond.png",
           "gold": "ore_slade_gold.png", "glowstone": "ore_slade_glowstone.png",
           "quartz": "ore_stone_saltpeter.png"}

# Ores the collection has no sprite for, turned from another: the source, the hue the ore takes
# (0 to 1 round the colour wheel), the least saturation its pixels are given, and the brightness
# range its pixels are mapped into. A pixel is ore by the same test as in ORE_PIXEL, on the source.
RECOLOURED = {
    "redstone": ("ore_slade_lapis.png", 0.0, 0.0, (0.0, 1.0)),
    "netherite": ("ore_stone_anthracite.png", 0.06, 0.5, (0.3, 0.55)),
}

# The items wear his sprites too. The uranium ingot's is recoloured to the uranium ore's green.
ITEM_ART = {"sulfur": "material_crystal_sulfur_fancy.png", "raw_uranium": "material_crystal_greenish.png",
            "uranium_ingot": "ingot_lead.png"}

# The Harvesters' sprites, written from his unchanged, by name under textures/block/harvester/.
HARVESTER_ART = {
    "duct": "industrial_metal_duct_flat_1.png",
    "vent": "industrial_metal_duct_vent.png",
    "intake": "industrial_metal_duct_intake.png",
    "gearbox": "device_gearbox_ends.png",
    "brace": "space-plating-square.png",
    "band_1": "space-plating-stripe-light-gray-horizontal-undamaged.png",
    "band_1_vertical": "space-plating-stripe-light-gray-vertical-undamaged.png",
    "band_2": "space-plating-stripe-yellow-horizontal-undamaged.png",
    "band_2_vertical": "space-plating-stripe-yellow-vertical-undamaged.png",
    # The drill heads' housing and the drill legs, drawn by the Harvester's renderer; the bit wears the gearbox.
    "drill_body": "steel_block.png",
    "drill_leg": "metal_pipes_copper.png",
}
# Each tier's default power, shown at eye height on its front: tier 1 burns fuel, tier 2 takes FE.
# Its keys are the tiers a model is written for.
HARVESTER_POWER = {1: "firebox", 2: "coil"}

# The smelter's rim, where its brick shows: rows down to RIM_TOP, and the outer RIM_SIDE columns on each
# side down to RIM_UPPER, the bottom of the firebox; below it the grate's iron reaches the edge.
RIM_TOP = 1
RIM_SIDE = 2
RIM_UPPER = 7
# How much redder than blue a pixel is to be the smelter's brick, against the grey of its cold iron.
BRICK_RED = 18
# Half the width of the dark band down the copper coil side's left edge.
COIL_BAND_HALF = 2

# The bounds Minecraft allows an element's from and to (CuboidModelElement): a block and a half either
# way from the one block the model is placed on.
LOW, HIGH = -16, 32

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
    "diamond": (0.3, 0.0),
    "gold": (0.4, 0.0),
    "glowstone": (0.3, 0.0),
    "redstone": (0.3, 0.0),
    # Anthracite's ore is dark as coal's is; it is turned brown, and so tested on its brown.
    "netherite": (0.25, 0.0),
    # Saltpeter's ore is white, and its matrix grey: a pixel brighter than the third number is ore.
    "quartz": (1.1, 0.0, 0.64),
}

ORE_SOURCE_TEST = {"netherite": (1.1, 0.2)}

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

TAR = (24, 20, 18, 255)
TAR_DARK = (12, 10, 9, 255)
TAR_SHEEN = (70, 62, 56, 255)
CLEAR = (0, 0, 0, 0)


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


def recoloured_art(resource):
    """A borrowed ore sprite with its ore pixels turned to another colour, the stone left as it was."""
    name, hue, saturation, (low, high) = RECOLOURED[resource]
    source = art(name)
    test_saturation, test_value = ORE_SOURCE_TEST.get(resource, ORE_PIXEL[resource][:2])
    pixels = []
    for row in source:
        out = []
        for p in row:
            h, s, v = to_hsv(p)
            if s > test_saturation or v < test_value:
                r, g, b = colorsys.hsv_to_rgb(hue, max(s, saturation), low + (high - low) * v)
                p = [round(r * 255), round(g * 255), round(b * 255), p[3]]
            out.append(p)
        pixels.append(out)
    return pixels


def source_art(resource):
    return recoloured_art(resource) if resource in RECOLOURED else art(SOURCED[resource])


def sourced_sprites(resource, ratios):
    """One sprite per stage from a borrowed full sprite: the ore shrinks toward its centre."""
    source = source_art(resource)
    saturation, value, *bright = ORE_PIXEL[resource]
    cells = [(x, y) for y in range(16) for x in range(16)]
    hsv = {c: to_hsv(source[c[1]][c[0]]) for c in cells}
    ore = [c for c in cells if hsv[c][1] > saturation or hsv[c][2] < value or (bright and hsv[c][2] > bright[0])]
    stone = [c for c in cells if c not in ore]
    cx = sum(x for x, _ in ore) / len(ore)
    cy = sum(y for _, y in ore) / len(ore)
    # The most ore-like pixel near the centroid is the last to go, so the final stage still reads
    # as ore rather than as a shadow: the brightest coloured pixel, or for coal the darkest.
    coloured = [c for c in ore if hsv[c][1] > saturation or (bright and hsv[c][2] > bright[0])]
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


def tar_block():
    """Near-black tar with darker pits and a few brown-grey glints of sheen."""
    pixels = [[TAR] * 16 for _ in range(16)]
    for x, y in ((2, 3), (3, 3), (9, 1), (12, 6), (13, 6), (5, 9), (6, 10), (10, 12), (1, 13), (14, 14)):
        pixels[y][x] = TAR_DARK
    for x, y in ((4, 5), (11, 3), (7, 13), (13, 10), (2, 11)):
        pixels[y][x] = TAR_SHEEN
    return pixels


def tar_item():
    """A lump of tar on a clear ground: a rounded blob, darker at its rim, with a glint at its top left."""
    pixels = [[CLEAR] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, (y - 8.5) * 1.25
            distance = dx * dx + dy * dy
            if distance <= 36:
                pixels[y][x] = TAR_DARK if distance > 26 else TAR
    for x, y in ((5, 5), (6, 5), (5, 6)):
        pixels[y][x] = TAR_SHEEN
    return pixels


def firebox():
    """The smelter's front let into the duct: the brick showing round its rim is painted over with the
    duct, so the iron firebox and grate sit in the Harvester's casing."""
    smelter, duct = art("device_smelter_front.png"), art(HARVESTER_ART["duct"])
    for y in range(16):
        for x in range(16):
            r, g, b, _ = smelter[y][x]
            on_rim = y <= RIM_TOP or (y <= RIM_UPPER and (x < RIM_SIDE or x >= 16 - RIM_SIDE))
            if on_rim and r > g and r - b >= BRICK_RED:
                smelter[y][x] = duct[y][x]
    return smelter


def coil():
    """The copper coil's side turned half its dark band over, so the band's darker half runs down the
    left edge, and then its left half mirrored onto its right: the same dark half band down either
    edge and the coil's shine in the middle."""
    turned = [row[COIL_BAND_HALF:] + row[:COIL_BAND_HALF] for row in art("copper_coil_side.png")]
    return [row[:8] + row[7::-1] for row in turned]


def harvester_sprites():
    """Each Harvester sprite, by name."""
    sprites = {name: art(source) for name, source in HARVESTER_ART.items()}
    sprites["firebox"] = firebox()
    sprites["coil"] = coil()
    return sprites


def face(texture):
    return {"uv": [0, 0, 16, 16], "texture": texture}


def shift(low, high):
    """The whole blocks to translate by, so that low..high, less that, is inside the bounds."""
    for blocks in (0, -1, 1, -2, 2):
        if low - 16 * blocks >= LOW and high - 16 * blocks <= HIGH:
            return blocks
    sys.exit(f"no translation brings {low}..{high} inside {LOW}..{HIGH}")


def casing_faces(x, y, z):
    """The faces of the casing block at column x, z and row y (0 the bottom) of a Harvester facing north:
    only its outward ones. The duct all round the lower two rows and underneath; the tier's band round the
    top row and down the roof's centre line, with braced corners, vents at its east and west edges and
    the gearbox at its centre; on the front, the intake at the foot and the tier's power above it."""
    faces = {}
    if y == 0:
        faces["down"] = face("#duct")
    if y == 2:
        faces["up"] = face("#gearbox" if (x, z) == (0, 0) else "#band_vertical" if x == 0
                           else "#vent" if z == 0 else "#brace")
    for outward, name in ((x == -1, "west"), (x == 1, "east"), (z == -1, "north"), (z == 1, "south")):
        if outward:
            faces[name] = face("#band" if y == 2 else "#duct")
    if (x, z) == (0, -1) and y < 2:
        faces["north"] = face("#intake" if y == 0 else "#power")
    return faces


def harvester_textures(tier):
    textures = {name: HARVESTER_SPRITE + name for name in HARVESTER_ART
                if not name.startswith(("band_", "drill_"))}
    textures.update(band=HARVESTER_SPRITE + f"band_{tier}", band_vertical=HARVESTER_SPRITE + f"band_{tier}_vertical",
                    power=HARVESTER_SPRITE + HARVESTER_POWER[tier])
    return textures


def harvester_model(tier, lowest):
    """A Harvester's 3x3x3 casing, facing north, from row `lowest` up: one element per footprint block.
    The top row is above the bounds, so the model is a NeoForge composite whose children are translated
    by whole blocks when it bakes, before the blockstate turns it to each facing. The origin draws it
    from row 1, its bottom row being nine blocks that each draw their own (see foot_model); the item
    draws it whole from row 0."""
    children = {}
    for x in (-1, 0, 1):
        for y in (0, 1, 2):
            if y < lowest:
                continue
            for z in (-1, 0, 1):
                faces = casing_faces(x, y, z)
                # The block at the footprint's heart shows nothing, and Minecraft refuses an element
                # with no faces.
                if not faces:
                    continue
                low, high = [16 * x, 16 * y, 16 * z], [16 * x + 16, 16 * y + 16, 16 * z + 16]
                moved = tuple(shift(low[axis], high[axis]) for axis in range(3))
                children.setdefault(moved, []).append({
                    "from": [low[axis] - 16 * moved[axis] for axis in range(3)],
                    "to": [high[axis] - 16 * moved[axis] for axis in range(3)],
                    "faces": faces,
                })
    textures = harvester_textures(tier)
    return {
        "ambientocclusion": False,
        "textures": dict(textures, particle=HARVESTER_SPRITE + "duct"),
        "loader": "neoforge:composite",
        "children": {
            "part_%d_%d_%d" % moved: {"textures": dict(textures), "transform": {"translation": list(moved)},
                                      "elements": elements}
            for moved, elements in sorted(children.items())
        },
    }


def column_of(x, z):
    """The Harvester's column at offset x, z from its origin block, facing north, in the order its cursor
    counts in (the Java side's DrillParts): dx then dz, from -1 to 1."""
    return (x + 1) * 3 + (z + 1)


def foot_model(tier, column):
    """The bottom block of one column, drawn on its own: its casing, and nothing where a drill head stands
    there instead (the blockstate leaves it out; the renderer draws the head)."""
    x, z = column // 3 - 1, column % 3 - 1
    textures = harvester_textures(tier)
    return {
        "ambientocclusion": False,
        "textures": {"duct": textures["duct"], "intake": textures["intake"], "particle": HARVESTER_SPRITE + "duct"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces(x, 0, z)}],
    }


def harvester_models():
    """Each Harvester model, by name: the casing above the bottom row on the origin, the whole casing for
    its item, a bottom block's casing for each of the nine columns, and a part block's, which draws nothing
    and lends its breaking particles the duct."""
    models = {}
    for tier in HARVESTER_POWER:
        models[f"harvester_{tier}"] = harvester_model(tier, 1)
        models[f"harvester_{tier}_full"] = harvester_model(tier, 0)
        models[f"harvester_{tier}_part"] = {"textures": {"particle": HARVESTER_SPRITE + "duct"}}
        for column in range(9):
            models[f"harvester_{tier}_foot_{column}"] = foot_model(tier, column)
    return models


# A blockstate turns a model to each facing the way the origin's is turned: north is the model as written.
FACING_TURNS = {"north": 0, "east": 90, "south": 180, "west": 270}


def turned(model, turn):
    apply = {"model": "fieldworks:block/" + model}
    if turn:
        apply["y"] = turn
    return apply


def harvester_blockstates():
    """Each Harvester block's blockstate, by name. The origin draws the casing above its bottom row, and
    its own bottom block unless it is a drill head; a part draws its own bottom block, unless it is a
    drill head, if it stands in the bottom row, and otherwise nothing (the origin's model has it)."""
    states = {}
    for tier in HARVESTER_POWER:
        origin = []
        part = [{"apply": {"model": f"fieldworks:block/harvester_{tier}_part"}}]
        for facing, turn in FACING_TURNS.items():
            origin.append({"when": {"facing": facing}, "apply": turned(f"harvester_{tier}", turn)})
            origin.append({"when": {"facing": facing, "drill": "false"},
                           "apply": turned(f"harvester_{tier}_foot_{column_of(0, 0)}", turn)})
            number = 0
            for lx in (-1, 0, 1):
                for lz in (-1, 0, 1):
                    if (lx, lz) == (0, 0):
                        continue
                    number += 1
                    # A part's offset is in the origin's frame, x forward and z to the side; facing
                    # north that is the world's dz = lx and dx = lz (Groundworks' FootprintShape).
                    part.append({"when": {"facing": facing, "part": str(number), "drill": "false"},
                                 "apply": turned(f"harvester_{tier}_foot_{column_of(lz, lx)}", turn)})
        states[f"harvester_{tier}"] = {"multipart": origin}
        states[f"harvester_{tier}_part"] = {"multipart": part}
    return states


def build():
    with open(AMOUNTS, encoding="utf-8") as handle:
        by_key = json.load(handle)["resources"]
    files = {}
    for resource in sorted(by_key):
        ratios = by_key[resource]["stage_ratios"]
        if len(ratios) < 2:
            sys.exit(f"{resource} carries {len(ratios)} stage ratios; there is nothing to render")
        images = (sourced_sprites(resource, ratios) if resource in SOURCED or resource in RECOLOURED
                  else boulder_sprites(resource, ratios))
        for stage, image in enumerate(images):
            files[os.path.join(ORE_OUT, f"{resource}_stage{stage}.png")] = image
    for name, pixels in item_sprites().items():
        files[os.path.join(ITEM_OUT, f"{name}.png")] = png(pixels)
    files[os.path.join(BLOCK_OUT, "tar.png")] = png(tar_block())
    files[os.path.join(ITEM_OUT, "tar.png")] = png(tar_item())
    for name, pixels in harvester_sprites().items():
        files[os.path.join(HARVESTER_OUT, f"{name}.png")] = png(pixels)
    for name, model in harvester_models().items():
        files[os.path.join(MODELS, f"{name}.json")] = (json.dumps(model, indent=2) + "\n").encode()
    for name, state in harvester_blockstates().items():
        files[os.path.join(BLOCKSTATES, f"{name}.json")] = (json.dumps(state, indent=2) + "\n").encode()
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
        print(f"ok   {len(files)} sprites and models match the generator")
        return 0

    for path, image in sorted(files.items()):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "wb").write(image)
    print(f"wrote {len(files)} sprites and models")
    return 0


if __name__ == "__main__":
    sys.exit(main())
