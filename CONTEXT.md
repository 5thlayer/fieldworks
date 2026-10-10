# Fieldworks

The resources of the world: ore patches of iron, copper, coal, stone, uranium and sulfur, oil wells
and the crude oil under them, and one map marker per patch. The FactoryWorks Pack consumes it; it
plays alone in vanilla.

## Language

The Library's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Fieldworks**:
This Library.

**Consumer**:
A mod that builds against this Library: the FactoryWorks Pack, or another Library.
_Avoid_: client (collides with the game's client side), dependent, integration

### Resources

**Patch**:
A disc of ore blocks of one resource, generated in new land beyond the spawn area. It holds a total
that its blocks share equally, and it is found, marked and mined as a whole.
_Avoid_: deposit, vein, field (an oil field is a different thing)

**Ore block**:
One block of a patch. It holds an amount, shows it as one of eight stages, and gives up one unit per
break until the last, when it becomes stone.
_Avoid_: ore node, tile

**Generation switch**:
The config entry that stops new patches of one resource from generating. Patches already in the
world stay.
_Avoid_: toggle, spawn rate

**Oil well**:
A block that holds an amount of crude and cannot be broken. Its amount decays by a fixed step per
cycle to a floor and never to zero.
_Avoid_: oil spring, pumpjack site

**Oil field**:
A group of oil wells placed together on land, spaced apart and never on a patch's ore.

**Crude oil**:
The fluid an oil well yields, in the `c:crude_oil` tag. Fieldworks gives it no bucket.

### Markers

**Marker**:
A patch's icon on a map: its resource and what is left, one per patch, shown to every player once the
patch is found. An oil field's marker shows its summed yield and is never removed.
_Avoid_: waypoint, ping

**Found**:
A patch is found when the chunk holding its centre is first sent to any player. A found patch stays
found.
_Avoid_: charted, discovered
