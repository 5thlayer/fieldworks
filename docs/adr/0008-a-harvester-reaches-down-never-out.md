---
status: accepted
---

# A Harvester reaches down, never out

A patch is one ore block per column, laid on each column's topmost terrain block, so it follows the
land. On uneven ground some of the nine columns under a Harvester hold their ore a block or two below
the layer it stands on, and a Harvester that works only the block directly under it strands them.

So each column under the footprint looks down through air, replaceable blocks and fluids, up to the
Harvester Tier's **reach** (`reach` in `data/fieldworks/harvester_tier/`, 0 when absent, which is
the behaviour before this decision). The first other block within reach is the column's source. Reach
is counted from the Harvester, so a column opened by a broken harvestable block looks further down
but never past reach. Reach has no cap: a Pack sets it as it likes.

A **drill head** shows each column at work, over its source. Where the source is directly under the
Harvester, that column's bottom block becomes the drill head; otherwise the Harvester places a drill
head block in the cell above the source and draws legs up to itself. A placed drill head takes a
replaceable block's or a fluid's place as Beltworks' wedge does, waterlogging in water and destroying
lava, collides with nothing, holds its cell, and dismantles the Harvester when broken. A column with no
source shows none. Drill heads follow the world whether or not the Harvester is working.

The Harvester's renderer draws the drill heads and legs from geometry laid out in plain Java, as
Beltworks lays out its feeder's arm, textured from the collection art (ADR 0006). The bottom layer of
the Harvester's model moves onto its nine bottom blocks, so a bottom block can be drawn as a drill
head instead. A placed drill head block draws nothing itself.

## Considered Options

- **Reach out, past the footprint.** A wider square of columns would let Harvesters tile a patch
  without stranding the ore between them. Rejected: neighbouring Harvesters would contest the same ore
  blocks, the footprint would no longer say what a Harvester works, and the stranding that reach fixes
  is the land's height, not the footprint's width.
- **A vertical tolerance with no visible part.** Reach without a drill head would draw ore from a block
  the Harvester is not visibly working.
- **A cap on reach.** Considered while leg length looked like a bounded blockstate. With the legs drawn
  by a renderer nothing needs one, and how deep a tier digs is a Pack's to decide.

## Consequences

- The drill head is a new block id saved into worlds, so renaming or removing it breaks saves
  (ADR 0001).
- Every Harvester's look changes, even on flat ground with reach 0: its bottom blocks over a source
  are drill heads.
