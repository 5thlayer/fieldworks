---
status: accepted
---

# A Drilling Fluid is crafted by the bucket

Drilling mud, the Overworld's Drilling Fluid, was brewed: water bottles and a clay ball or a clay block in a brewing
stand, which burns blaze powder, gave 250 mB mud bottles, 750 mB a brew in 400 ticks. Magma slurry, the Nether's, is
crafted from a lava bucket and soul soil. The Overworld's fluid needed a trip to the Nether first, and cost more, more
time and more containers than the Nether's own.

So each Drilling Fluid is crafted by the bucket from its base fluid's bucket and one cheap item: a water bucket and a
clay ball make a drilling mud bucket, as a lava bucket and soul soil make a magma slurry bucket. The craft leaves
nothing in the grid, since the bucket becomes the result. Drilling mud is no longer brewed, and its bottle is gone:
a Drilling Fluid comes in a bucket only. Where Craftworks is present, an Assembler also makes each from fluids at the
same rate, beside the bucket craft (fieldworks#51).

## Considered Options

- **Keeping the brewing beside the craft.** It would be strictly worse, blaze powder and bottles for less mud, and
  keeps an EMI plugin alive only to draw it.
- **Keeping the bottle with no recipe.** Nothing would fill it but other mods' machines, for a container no other
  Drilling Fluid has.
- **A clay block for the craft.** Four clay balls a bucket would make mud cost more than slurry for no reason the
  Overworld gives.

## Consequences

- Mud bottles in existing saves disappear on update, so the change ships as breaking (ADR 0001).
