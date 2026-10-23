---
status: accepted
---

# Patches are frozen data, mined one unit per break, and wells decay to a floor

Fieldworks holds the world's resources (FactoryWorks ADR-0127). This records how its patches, oil
wells and crude oil work, ported from FactoryWorks Core at commit `fb05f50`.

ADR 0007 supersedes its resource list (twelve resources, each with a dimension and a minimum
distance in place of the one 150-block hole) and the drilling mud bonus on wells: a well's floor is
final.

**Decision.**

- **The numbers are Fieldworks' own, frozen.** A patch's size law, its edge noise and each
  resource's density are in `fieldworks/ore/amounts.json`, and crude's in `fieldworks/oil/crude.json`.
  They were taken from Factorio's data once and are not regenerated; no Factorio file is read at
  runtime.
- **Patches are discs and nothing else.** There are no starting patches. A disc of radius from the
  law is placed at the centre of a chunk the structure set picks, nothing generates within 150 blocks
  of the origin, and a column off the land is dropped. A block's amount is the patch total divided by
  the blocks placed, rounded down and floored at one, so a patch never holds more than its total. The
  total grows with distance from the origin, linearly and without a cap past 1600 blocks.
- **Sulfur has no source patch.** Factorio has none, so its numbers are Fieldworks': uranium's
  spacing and spots per square kilometre, with a density and spot size of its own. They are tunable
  data like the rest.
- **Each resource has a generation switch** in `fieldworks-common.toml`, read when a patch is
  generated, and oil fields have one of their own (`generate.oil_field`).
- **Patch ore is mined by hand** with vanilla pickaxes at vanilla hardness (3.0, stone 1.5) and
  vanilla tiers, set by the `needs_*_tool` tags: stone for iron, copper and sulfur, iron for uranium,
  none for coal and stone. A break is cancelled; it takes one unit from the block and pays one item,
  and the last unit's break turns the block into stone. A break with too weak a tool takes nothing.
  A creative break removes the block. The same draw serves a machine.
- **An ore block no patch placed starts at its resource's `default_amount`**, in `amounts.json`: the
  amount of a 1000-block patch of size factor 1 at 1600 blocks out (iron 8000, copper and coal 6400,
  stone 3200, sulfur 2400, uranium 1440). A block placed in creative, by `/setblock` or re-placed
  would otherwise start at zero and give nothing. Voidworks' displacement produces Enriched ore blocks
  outside any patch (`docs/spec/enriched-ore.md` in factoryworks), and they need a nonzero start.
- **A block holds no amount.** It derives from its patch, and the difference from it is a sparse
  chunk attachment. The blockstate carries the stage, recomputed from the amount.
- **An oil well holds an amount and is never broken.** A cycle yields `10 × amount / 300000` mB
  of crude, at most 1000, and takes 10 off the amount. The amount stops falling at the higher of
  60000 and 20% of the well's starting amount, so it never reaches zero.
- **An oil field is crude's disc shape with a well on 1/96 of its columns**, wells at least 3 blocks
  apart, none on an ore column, none within 150 blocks of the origin and none off the land. A well's
  amount is `(cone / 0.0208 + 220000) × max((1000 + d) / 2600, 1)` for the column's distance `d`, with no
  cap, so a far well starts above 100%.
- **Crude oil is a fluid with a block and no bucket**, in `c:crude_oil`. (The bucket is superseded
  by ADR 0004: crude gets one.) The Harvester that pumps a
  well is a later ticket (#1).
- **A well exposes no fluid capability until the Harvester.** The yield per cycle and the decay step
  are the Harvester's to drive, and a capability now would let any other mod's pump or pipe drain a
  well at a rate and tier nothing here has decided. The capability arrives with the Harvester that
  defines how a cycle is taken.

**Considered: starting patches.** The Showcase's spawn area carried them. A Module cannot assume a
spawn area, and a disc past 150 blocks gives the same play without one.

**Considered: a block entity per ore block.** A patch is thousands of blocks, and a block entity
apiece is not affordable.

**Considered: letting a hand break destroy the block.** It would let a player spend one break on a
block holding hundreds of units.
