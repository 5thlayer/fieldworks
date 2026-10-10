# Changelog

Written for Consumers: what a mod building against Fieldworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- Groundworks is now required, from 0.5.5 up to but not including 0.6, and is not nested: a player installs it beside Fieldworks. libworks-runtime 0.1.0 (mod id `libworks`) rides inside the jar by jar-in-jar (#5).
- The Burner Harvester, `fieldworks:harvester_1`, a 3×3×3 Groundworks footprint, draws the ore blocks in the layer under it one unit per operation, in turn, as a hand break does. It burns any fuel a hopper feeds it, one burn tick per tick of work, and pushes what it draws into the item handler at its front; with nowhere to put it, it stops and drops nothing. Its rank, speed and power come from `data/fieldworks/harvester_tier/harvester_1.json`, which a datapack can override; a missing or invalid file leaves it idle and the load logs why (#5).
- Harvest Recipes are data: a `fieldworks:harvesting` recipe in `data/<ns>/recipe/` names a `source` (a block, a block tag or a list of blocks), a `result` item, a `duration` in ticks at speed ×1, which a tier's `speed` divides, an optional `minimum_tier` (a Harvester block id, compared by rank) and `replaces_default`. With no Held recipe the Harvester runs only Default recipes, and skips an ore block whose Default recipe a recipe replaces; it works the rest of its footprint. A recipe the load refuses is logged with its id and why. Nothing sets a Held recipe from the game yet (#8).
- A Harvester over an oil well pumps it: one operation is one cycle of the well's yield and decay, into an 8,000 mB product tank, and the well never falls below its floor. While any well is under the footprint it works only the wells, in turn, and leaves the ore. A full tank stops it (#9).
- Crude oil has a bucket, `fieldworks:crude_oil_bucket`, a plain `BucketItem` with NeoForge's fluid item capability. Empty buckets a hopper feeds the Harvester come out of its output face filled with crude (#9).
- The Harvester exposes NeoForge's fluid capability on every face: a pipe can drain its crude and can fill nothing. It also pushes crude into the fluid handler at its output face, and pulls nothing from it. The oil well itself still exposes no fluid capability (#9).
- The oil field marker on the FTB Chunks map draws the crude oil bucket in place of the vanilla bucket (#9).
- The Electric Harvester, `fieldworks:harvester_2`, ships at rank 2 and speed ×2 on FE alone: 40 FE per tick of work from a 50,000 FE buffer, taken through NeoForge's energy capability on every face of its footprint, which gives none back. A tier file's `power` may name `fuel`, `fe_per_tick` or both, with `fe_buffer`; a tier that takes both pays FE first and lights fuel only when the buffer is short of a tick's cost, and a fuel item already burning finishes before FE is spent again. A tier exposes only the power it declares: one that takes no FE has no energy capability, and one that takes no fuel accepts none. Default English names are "Burner Harvester" and "Electric Harvester", from lang (#7).

## 0.1.0

- Ore patches of iron, copper, coal, stone, uranium and sulfur generate in the overworld's land beyond 150 blocks of the origin, each behind a switch in `fieldworks-common.toml`. Their ore blocks, `fieldworks:<resource>_ore`, are in `c:ores` and are mined by hand, one unit per break (#2).
- Oil fields place `fieldworks:oil_well` blocks whose crude decays to a floor and never runs out, behind a switch in `fieldworks-common.toml`. Crude oil, `fieldworks:crude_oil`, is in `c:crude_oil` (#2).
- `fieldworks:sulfur` is in `c:dusts/sulfur`, and `fieldworks:uranium_ingot`, smelted from `fieldworks:raw_uranium`, in `c:ingots/uranium` (#2).
- Every player's FTB Chunks map shows one marker per found patch, with what is left (#2).
- An ore block placed outside any patch starts at a default amount per resource, `default_amount` in `fieldworks/ore/amounts.json` (#2).
- Jade shows how much ore is left in an ore block (#2).
