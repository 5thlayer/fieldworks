# Changelog

Written for Consumers: what a mod building against Fieldworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

## 0.1.0

- Ore patches of iron, copper, coal, stone, uranium and sulfur generate in the overworld's land beyond 150 blocks of the origin, each behind a switch in `fieldworks-common.toml`. Their ore blocks, `fieldworks:<resource>_ore`, are in `c:ores` and are mined by hand, one unit per break (#2).
- Oil fields place `fieldworks:oil_well` blocks whose crude decays to a floor and never runs out, behind a switch in `fieldworks-common.toml`. Crude oil, `fieldworks:crude_oil`, is in `c:crude_oil` (#2).
- `fieldworks:sulfur` is in `c:dusts/sulfur`, and `fieldworks:uranium_ingot`, smelted from `fieldworks:raw_uranium`, in `c:ingots/uranium` (#2).
- Every player's FTB Chunks map shows one marker per found patch, with what is left (#2).
- An ore block placed outside any patch starts at a default amount per resource, `default_amount` in `fieldworks/ore/amounts.json` (#2).
- Jade shows how much ore is left in an ore block (#2).
