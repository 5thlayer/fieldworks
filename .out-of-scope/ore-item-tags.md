# Ore items in the common item tags

Fieldworks does not put its ore items in the `c:ores` or `c:ores/<resource>` item tags. The ore
*blocks* are in those block tags (`c:ores` for all six, `c:ores/<resource>` for every one but stone,
from [#21](https://github.com/5thlayer/fieldworks/issues/21)), and that is where the tags stop.

## Why this is out of scope

No player in survival can ever hold an ore item:

- A survival break of an ore block is cancelled (`OreMining.onBreak`). It pays out one unit of the
  resource, such as raw iron or coal, and the block stays until it runs out and becomes stone. Silk
  touch changes nothing.
- The block is registered with `noLootTable()` (`OreBlock`), so nothing else can make it drop itself.
- Pistons can't push it (`PushReaction.BLOCK`), and the Harvester draws from it and never breaks it.

The items exist only in creative mode and from commands. Tagging them buys a survival player
nothing, and it costs something: EMI and JEI fill recipe inputs from item tags. Every crusher or
ore-doubling recipe in another mod that takes `#c:ores/iron` would show Fieldworks' iron ore as an
input, an item players would go looking for and never find.

What would bring this back in scope is a way to obtain the ore items in survival. The item tags
would then belong to that change.

## Prior requests

- [#28](https://github.com/5thlayer/fieldworks/issues/28): "Ore items in the c:ores and c:ores/<resource> item tags"
