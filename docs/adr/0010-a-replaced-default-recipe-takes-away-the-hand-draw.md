---
status: accepted
---

# A replaced Default recipe takes away the hand draw

ADR 0007 put uranium, diamond and netherite behind the Electric Harvester and a Drilling Fluid, through written
Harvest Recipes that replace the default. The gate held only for the Harvester: ADR 0002's hand draw still took a
unit with an iron pickaxe (a diamond one for netherite), so a player could skip the tech step with a pickaxe.

So an ore block can be drawn by hand only while its Default recipe stands. A written recipe with
`replaces_default` takes away the hand draw as well as the fluid-free Harvester draw, and the two rules can never
disagree. A pack that drops `fieldworks:diamond_from_ore` makes diamond hand-minable again. A pack that gives gold a
replacing recipe makes gold Harvester-only. The rule follows the block, so ore placed outside a patch is held to it
too.

A refused break is cancelled like one with too weak a tool. It takes nothing and the block stays. The player is
told on the action bar what the replacing recipe needs: its minimum Harvester Tier, and its Drilling Fluid by the
fluid tag's own name ("Needs the Electric Harvester and drilling mud"). Jade shows the same line under the amount
and drops its pickaxe icon. A break by another mod's fake player counts as one by hand. A creative break still
removes the block.

This supersedes ADR 0002's "Patch ore is mined by hand" clause for any resource whose Default recipe is replaced;
the rest of that clause stands.

## Considered Options

- **A fixed list or a block tag of Harvester-only resources.** It would be a second gate beside the recipes,
  free to disagree with them.
- **Letting fake players through.** Any other mod's block breaker would get past the Electric Harvester and the
  Drilling Fluid.
- **Naming a recipe's fluid by the first fluid in its tag.** The name would change when a pack adds a fluid to
  the tag.
- **Dropping the uranium, diamond and netherite ores from the `needs_*_tool` tags.** They still set the break
  speed, and a pack that drops a replacing recipe wants them back.

## Consequences

- Uranium, diamond and netherite patches can no longer be mined by hand, so the change ships as breaking
  (ADR 0001).
