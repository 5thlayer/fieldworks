# Changelog

Written for Consumers: what a mod building against Fieldworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- Groundworks is now required, from 0.5.5 up to but not including 0.6, and is not nested: a player installs it beside Fieldworks. libworks-runtime 0.1.0 (mod id `libworks`) rides inside the jar by jar-in-jar (#5).
- The Burner Harvester, `fieldworks:harvester_1`, a 3×3×3 Groundworks footprint, draws the ore blocks in the layer under it one unit per operation, in turn, as a hand break does. It burns any fuel a hopper feeds it, one burn tick per tick of work, and pushes what it draws into the item handler at its front; with nowhere to put it, it stops and drops nothing. Its rank, speed and power come from `data/fieldworks/harvester_tier/harvester_1.json`, which a datapack can override; a missing or invalid file leaves it idle and the load logs why (#5).
- Harvest Recipes are data: a `fieldworks:harvesting` recipe in `data/<ns>/recipe/` names a `source` (a block, a block tag or a list of blocks), a `result` item, a `duration` in ticks at speed ×1, which a tier's `speed` divides, an optional `minimum_tier` (a Harvester block id, compared by rank) and `replaces_default`. With no Held recipe the Harvester runs only Default recipes, and skips an ore block whose Default recipe a recipe replaces; it works the rest of its footprint. A recipe the load refuses is logged with its id and why. Nothing sets a Held recipe from the game yet (#8).

## 0.1.0

- Ore patches of iron, copper, coal, stone, uranium and sulfur generate in the overworld's land beyond 150 blocks of the origin, each behind a switch in `fieldworks-common.toml`. Their ore blocks, `fieldworks:<resource>_ore`, are in `c:ores` and are mined by hand, one unit per break (#2).
- Oil fields place `fieldworks:oil_well` blocks whose crude decays to a floor and never runs out, behind a switch in `fieldworks-common.toml`. Crude oil, `fieldworks:crude_oil`, is in `c:crude_oil` (#2).
- `fieldworks:sulfur` is in `c:dusts/sulfur`, and `fieldworks:uranium_ingot`, smelted from `fieldworks:raw_uranium`, in `c:ingots/uranium` (#2).
- Every player's FTB Chunks map shows one marker per found patch, with what is left (#2).
- An ore block placed outside any patch starts at a default amount per resource, `default_amount` in `fieldworks/ore/amounts.json` (#2).
- Jade shows how much ore is left in an ore block (#2).
