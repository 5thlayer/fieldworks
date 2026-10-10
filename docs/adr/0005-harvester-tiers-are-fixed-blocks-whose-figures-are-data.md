---
status: accepted
---

# Harvester Tiers are fixed blocks whose figures are data

Fieldworks registers a fixed set of Harvester blocks, `fieldworks:harvester_1` and
`fieldworks:harvester_2`, and everything about a tier other than its existence is data: its rank, its
speed multiplier, the power it accepts (burn time, FE, or both) and its FE buffer, in one file per
tier. Names come from lang. A Pack can retune, rename or re-power a tier without a jar, but cannot add
a third one.

The ids are numbered rather than named for their power because the power is data: a Pack that makes
`harvester_1` run on FE would otherwise ship a block called `burner_harvester` that burns nothing.

## Considered Options

- **One block, with the tier as data.** A single `fieldworks:harvester` whose tier is an item component
  saved in the block entity, with tiers in a datapack registry, would let a Pack add any number of
  tiers. Blocks are registered before datapacks load, so the look could not come from blockstates: it
  would need a renderer choosing a model by tier id. Not worth that cost while no Pack wants a third
  tier.

## Consequences

- The block ids are saved into worlds, so renumbering or merging them breaks saves (ADR 0001).
- A Harvest Recipe's minimum tier names a tier id and compares ranks, so a recipe written for
  `harvester_1` runs on every tier ranked at or above it.
