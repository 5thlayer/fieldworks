---
status: accepted
---

# A patch lies on ground a Harvester can work

A patch lays one ore block on each column's ground, so it follows the land. In the Overworld nothing
bounded that, and a patch could start on a river bank and run up a mountainside
([#48](https://github.com/5thlayer/fieldworks/issues/48)). Only the Nether had a limit, a column more
than 8 blocks above or below the centre's floor being left out (ADR 0007).

So a patch and an oil field generate only on ground a Harvester can work: in every 3×3 square of the
disc's columns, the highest and lowest ground differ by at most 3 blocks. A square at the edge is
checked on the disc's columns it holds. The ground is where the ore would lie, a pond's floor
included. One square past the limit and the start generates nothing. There is no retry elsewhere and
no trimming. The span is one figure, the same in both dimensions and for oil fields, and it replaces
the Nether's 8 from the centre, so neither dimension caps a patch's height from its centre. The figure
is 3 because a Harvester whose reach is 3 or more then works every column under its footprint,
wherever it stands on the patch (ADR 0008).

The check is made once, when the start is chosen, on the generator's base terrain, and the ore lies
on the ground that check judged. In the Overworld that is the topmost terrain block, as before. A
Nether column can hold floors over floors, and carvers open caves in the roof's rock and through the
floor itself, so the topmost open floor once they have run can lie tens of blocks from the base
terrain's. A Nether column's ore therefore lies on its base terrain's floor, and only where that floor
is still open; elsewhere the column takes none, as it takes none on a lava sea. A saved patch keeps
the rule it was made with, and one saved before this decision keeps the Nether's 8 from the
topmost open floor.

## Considered Options

- **A step between neighbouring columns.** A step of 3 between each pair of neighbours still lets one
  footprint span 6 blocks, and it says nothing in a Harvester's terms.
- **Trimming the columns past the limit**, as the Nether did. On hilly ground a patch would come out
  ragged or split, kept beside a slope it can't be worked on. Rejecting the start keeps every patch
  one place to build a Harvester on.
- **A cap from the centre's height.** The 3×3 span already rejects a patch that jumps between cave
  levels, and a gentle ramp across a whole patch is still worked by a Harvester.
- **Leaving mountain and hill biomes out of patch land.** Too blunt: it drops flat plateaus and
  meadows, and keeps a steep slope in a forest.

## Consequences

- A seed no longer generates the patches and oil fields it did before, so the change ships as breaking
  (ADR 0001).
- Hilly regions have fewer patches, since a rejected start is not tried again elsewhere.
- In the Overworld, caves or lakes carved after the start is chosen can leave the odd column further
  off than the limit.
- A Nether patch over a cave the carvers opened leaves those columns bare, so it can hold fewer blocks
  than its piece counted, and its total is shared among the blocks it was counted with.
