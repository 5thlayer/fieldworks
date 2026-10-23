---
status: accepted
---

# Patches keep a clearance, and the rarer start wins

Each resource's patches, and the oil fields, come from structure sets of their own, so each kind keeps its own
spacing and none keeps away from the others. Two patches of different resources could generate edge to edge, or
an oil field could start beside a patch, and on the map and under a Harvester they read as one.

So every patch and every oil field keeps a **clearance**: a gap of open land between its edge and the edge of
anything of another kind. That means a patch of another resource, or an oil field round a patch, or a patch round
an oil field. It is one figure, `clearance` in the Patch numbers, 16 blocks to start with, measured in the
dimension's own blocks; the Nether's eightfold distance does not apply. Patches of one resource, and oil fields
among themselves, keep only their spacing.

Chunks generate in any order, so where two starts would be too close a fixed rule picks the one that stays: the
rarer one, the kind whose structure set has the larger spacing, with a fixed order of kinds between equals. The
other generates nothing. A neighbour counts only if it would generate:
- its generation switch is on;
- it lies past its minimum distance;
- and it is not itself pushed out by a rarer neighbour.

Whether it would find land is not checked, since that is known only once its disc is shaped. It applies to chunks
generated from this decision on; patches already in a world stay.

## Considered Options

- **Vanilla's `exclusion_zone`.** A placement can keep clear of one other structure set only, and there are a
  dozen kinds to keep apart.
- **One structure set holding every kind as a weighted entry.** Its spread would keep every start apart, but it
  changes how often each resource appears and how its spacing works. That reopens the densities ADR 0002 and
  ADR 0007 set.
- **A distance between centres.** Patches range from a few blocks to 32 in radius. A distance large enough to
  keep two large discs apart would hold small ones far apart for nothing.
- **Counting every potential start.** Simpler, but a patch switched off, or inside its minimum distance, would
  still push others away and thin out patches for no visible reason.

## Consequences

- A seed no longer generates the patches and oil fields it did before, so the change ships as breaking
  (ADR 0001).
- A neighbour whose centre has no land under it still clears the land round it.
