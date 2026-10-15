![Fieldworks](https://raw.githubusercontent.com/5thlayer/fieldworks/main/publish/fieldworks-cover.png)

Fieldworks adds a new way to gather resources with less wandering around looking for them. Ore comes in **patches** that run down as you mine them, and crude oil comes from **wells** that never run dry. If hand-mining gets tedious, you can craft a **Harvester** that does all the hard work. Vanilla ore still generates as usual; the patches come on top of it.

## Why?

I wanted Factorio's ore patches in modded Minecraft.

I also wanted endgame mining that doesn’t mean strip-mining whole chunks. Patches get bigger the further out you go, so when a base needs more ore, you build out to a richer patch. Oil wells never run dry.

**NeoForge, Minecraft 26.1.2 only.** Fieldworks is still young, so please report anything odd on [GitHub](https://github.com/5thlayer/fieldworks/issues).

To play Fieldworks beside every other FactoryWorks mod, install the [FactoryWorks Showcase](https://www.curseforge.com/minecraft/modpacks/factoryworks-showcase) modpack from CurseForge.

## Features

- **Ore patches.** Patches of iron, copper, coal, stone, uranium and sulfur generate in new land more than 150 blocks from the origin. A patch is a disc of ore blocks sharing one total, which grows the further out it lies.
- **Ore blocks that hold an amount.** A break with a vanilla pickaxe, at vanilla hardness and tool tiers, takes one unit and gives one item, and the block stays. It thins out as it empties and becomes stone on its last unit. Jade shows what is left.
- **Oil fields.** Oil wells yield crude oil, which decays to a floor and never runs out. Crude has its own bucket.
- **Map markers.** FTB Chunks' map shows one marker per patch, with its resource and what is left, for every player once anyone has found it.
- **The Harvester.** A 3×3×3 machine that works the layer under it. Over an oil well it pumps crude; otherwise it draws ore blocks one unit at a time, and breaks other ores for their loot. The Burner Harvester burns anything a furnace burns; the Electric Harvester runs on FE at twice the speed.
- **Harvest Recipes and Drilling Fluids.** Every resource has a fluid-free default, so a Harvester works as soon as it is placed and powered. Recipes add Drilling Fluid variants: pick one in EMI or JEI and the Harvester holds it. Drilling mud, the first Drilling Fluid, is brewed in a brewing stand from a water bottle with a clay ball or a clay block, and keeps a decayed well pumping above its floor.
- **Hoppers are enough.** Fuel, mud in bottles or buckets, and empty buckets go in by hopper. Results, emptied bottles and filled crude buckets are pushed out of the Harvester's front into a chest, a hopper or a belt, and crude into a pipe. Pipe mods can fill its mud tank and drain its crude too.

## For pack authors

- **Generation switches.** Each resource and oil fields have a switch in `fieldworks-common.toml`. Turning one off stops new patches; those already generated stay.
- **Harvest Recipes** are `fieldworks:harvesting` recipes: a block or block-tag source (or the oil well), a result, a duration, an optional Drilling Fluid with its mB per operation, an optional minimum Harvester tier, and `replaces_default`, which makes a resource need its recipe. The bonus recipes Fieldworks ships yield at most twice the default; yours are not capped.
- **Harvester tiers** are data: each tier's rank, speed, power (burn time, FE or both), the burn time an operation spends and its FE buffer are in `data/fieldworks/harvester_tier/`. Their names are in lang.
- **Tags.** `fieldworks:harvestable` (default `c:ores`) is what the Harvester breaks for loot; `#fieldworks:drilling_fluids` is what a recipe may spend.

## For mod authors

- Crude oil is `fieldworks:crude_oil` in `c:crude_oil`, and any fluid in that tag sets as tar against lava; tar is `fieldworks:tar` in `c:tar`. Sulfur is in `c:dusts/sulfur` and uranium ingots in `c:ingots/uranium`. The ore blocks are in `c:ores`.
- The Harvester exposes NeoForge's item, fluid and energy capabilities on every block of its footprint. Its fluid face takes Drilling Fluids in and gives crude out.
- `CHANGELOG.md` lists what each version changed for a mod building against it.

## Dependencies

**Groundworks** is required. **FTB Chunks**, **Jade**, **EMI** and **JEI** are optional.

## Art

The coal, copper, iron, sulfur and uranium ore sprites, the sulfur, raw uranium and uranium ingot sprites, and the crude oil sprite are by Malcolm Riley ([unused-textures](https://github.com/malcolmriley/unused-textures)), under CC BY 4.0; the cover and icon are drawn from them. The stone and tar sprites are Fieldworks' own. `NOTICE` and `REUSE.toml` record each file.

## Developing

A 5thlayer Module, released on its own to Modrinth and CurseForge. See `GLOSSARY.md` for the domain glossary. `sh ./gradlew build` runs the JUnit tests on a plain JVM, and `sh ./gradlew runGameTestServer` the game tests headless; the build reads Groundworks and libworks-runtime from `~/.m2`. Which operation a Harvester runs next is `harvester/OperationChoice`, with no Minecraft types, so it is tested without a world. Releases go through the libworks plugin's `release` task (`docs/agents/releases.md`).

## License

MIT, with the art above under CC BY 4.0. Source is on [GitHub](https://github.com/5thlayer/fieldworks).
Releases are on [Modrinth](https://modrinth.com/mod/V0pWlACG) and [CurseForge](https://www.curseforge.com/projects/1737485).
