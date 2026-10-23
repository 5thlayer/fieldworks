# Fieldworks

The resources of the world: ore patches of every mineral a Module's recipes use in bulk, in the Overworld
and the Nether, oil wells and the crude oil under them, and one map marker per patch. It plays alone in vanilla.

## Language

The Module's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Fieldworks**:
This Module.
_Avoid_: Library, Binding

**Consumer**:
A mod that builds against this Module, such as another Module.
_Avoid_: client (collides with the game's client side), dependent, integration

**Gallery pack**:
The datapack in this Module's jar, "Fieldworks Gallery", off until a player turns it on, whose
functions build a working scene of each README feature, each with a sign naming it
that builds the scene again when clicked. Functions only (libworks ADR 0010).
_Avoid_: demo pack, showcase pack, gallery datapack

### Resources

**Patch**:
A disc of ore blocks of one resource, generated in new land beyond the spawn area and beyond its resource's
minimum distance. It holds a total that its blocks share equally, and it is found, marked and mined as a whole. Its size law and ragged
edge are shared with the oil field, which lays its wells over the same kind of disc.
_Avoid_: deposit, vein, outfield, field (an oil field is a different thing)

**Ore block**:
One block of a patch. It holds an amount, shows it as one of eight stages, and gives up one unit per
break until the last, when it becomes stone, or netherrack in the Nether.
_Avoid_: ore node, tile

**Generation switch**:
The config entry that stops new patches of one resource from generating, one for each of the twelve. Patches already in the
world stay.
_Avoid_: toggle, spawn rate

**Resource**:
One of the twelve minerals a patch holds, bound to exactly one dimension: iron, copper, coal, stone, sulfur,
uranium, redstone, gold and diamond in the Overworld; quartz, glowstone and netherite in the Nether. Its
dimension and **minimum distance**, the distance from origin below which none of its patches generate, are in
the Patch numbers. Distance is in Overworld-equivalent blocks: in the Nether a block counts eight times over.
_Avoid_: ore type, material

**Oil well**:
A block that holds an amount of crude and cannot be broken. Its amount decays by a fixed step per
cycle to a floor and never to zero.
_Avoid_: oil spring, pumpjack site

**Oil field**:
A group of oil wells placed together on land, spaced apart and never on a patch's ore.

**Crude oil**:
The fluid an oil well yields, in the `c:crude_oil` tag, carried in its own bucket.

**Tar**:
What a crude source block sets as where it touches lava, on any side. The lava stays, and flowing
crude never sets. Tar is made no other way. A tar block broken or pushed by a piston gives one tar, an
item in `c:tar`, which is how oil reaches another mod's plastic.
_Avoid_: bitumen, asphalt, pitch

### Markers

**Marker**:
A patch's icon on a map: its resource and what is left, one per patch, shown to every player once the
patch is found. An oil field's marker shows its summed yield and is never removed.
_Avoid_: waypoint, ping

**Found**:
A patch is found when the chunk holding its centre is first sent to any player. A found patch stays
found.
_Avoid_: charted, discovered

### Harvesting

**Draw**:
Taking one unit from an ore block: a break by hand, or one Harvester operation.
_Avoid_: mine, deplete (for a single draw)

**Harvestable block**:
A block with no amount that the Harvester may break for its normal loot, leaving air: anything in `fieldworks:harvestable`.
_Avoid_: minable, ore (alone)

**Harvester**:
The machine that works the layer under its footprint: it draws ore blocks, breaks harvestable blocks, or pumps oil wells.
_Avoid_: drill, rig, miner, pumpjack

**Harvester Tier**:
One rung of Harvester, with its rank, its speed, and the power it accepts: burn time, FE, or both.
_Avoid_: level, mark, burner/electric (as tier names)

**Harvest Recipe**:
What one Harvester operation takes and yields: a source, an optional Drilling Fluid, a result and a duration.
_Avoid_: mining recipe, drill recipe

**Default recipe**:
The fluid-free Harvest Recipe every source has without one being written: a draw of an ore block, the loot of a harvestable block, crude from an oil well. A written recipe with `replaces_default` takes it away: uranium, diamond, netherite and the oil well have one, which needs the Electric Harvester.
_Avoid_: base recipe, fallback

**Held recipe**:
The Harvest Recipe a player has set on a Harvester, which restricts it to the sources that recipe matches.
_Avoid_: selected recipe, filter

**Drilling Fluid**:
A fluid in `#fieldworks:drilling_fluids` that a Harvest Recipe consumes. Each belongs to a dimension through
the tag its recipes name: `#fieldworks:drilling_fluids/overworld` (drilling mud) or
`#fieldworks:drilling_fluids/nether` (magma slurry). The Harvester checks no dimension.
_Avoid_: lubricant, mud (for the general case)

**Magma slurry**:
The Nether's Drilling Fluid, crafted from a lava bucket and soul soil. It flows at lava's pace but is not
lava: it is not in `#minecraft:lava`, burns nothing, fuels no furnace and sets no crude into tar.
_Avoid_: lava, nether mud
