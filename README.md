![Fieldworks](https://raw.githubusercontent.com/5thlayer/fieldworks/main/publish/fieldworks-cover.png)

Fieldworks adds a new way to gather resources with less wandering around looking for them. Ore comes in **patches** that run down as you mine them, and crude oil comes from **wells** that never run dry. If hand-mining gets tedious, you can craft a **Harvester** that does all the hard work. Vanilla ore still generates as usual; the patches come on top of it.

## Why?

I wanted Factorio's ore patches in modded Minecraft.

I also wanted endgame mining that doesn’t mean strip-mining whole chunks. Patches get bigger the further out you go, so when a base needs more ore, you build out to a richer patch. Oil wells never run dry.

**NeoForge, Minecraft 26.1.2 only.** Fieldworks is still young, so please report anything odd on [GitHub](https://github.com/5thlayer/fieldworks/issues).

To play Fieldworks beside every other FactoryWorks mod, install the [FactoryWorks Showcase](https://www.curseforge.com/minecraft/modpacks/factoryworks-showcase) modpack from CurseForge.

## Features

- **Ore patches.** Every mineral the other FactoryWorks mods' recipes use in bulk has a patch, in the dimension where vanilla keeps it. In the Overworld: iron, copper, coal, stone, sulfur, uranium, redstone, gold and diamond. In the Nether: quartz, glowstone and netherite, laid on the open Nether floor, never on the roof or a lava sea. Patches generate in new land more than 150 blocks from the origin, and the rarer ones further out: sulfur and redstone 300, gold 500, uranium 600, diamond 1,500 and netherite 2,000 blocks. In the Nether a block counts for eight, so netherite starts 250 blocks out. A patch is a disc of ore blocks sharing one total, which grows the further out it lies. Patches of different resources, and oil fields, keep at least 16 blocks between them; where two would crowd each other, the rarer one generates. Nothing generates in the End.
- **Ore blocks that hold an amount.** A break with a vanilla pickaxe, at vanilla hardness and tool tiers, takes one unit and gives one item, and the block stays. It thins out as it empties and becomes stone, or netherrack in the Nether, on its last unit. Jade shows what is left. A block pays what the vanilla block drops without Silk Touch (netherite pays scrap) and needs the pickaxe tier of its vanilla counterpart. Uranium, diamond and netherite are the exception: no pickaxe works them, only the Electric Harvester with a Drilling Fluid, and a refused break tells you what the block needs, as Jade does under the amount.
- **Oil fields.** Oil wells yield crude oil, which decays to a floor and never runs out; the floor is final, and nothing pumps a well above it. Crude has its own bucket.
- **Map markers.** FTB Chunks' map shows one marker per patch, with its resource and what is left, for every player once anyone has found it.
- **The Harvester.** A 3×3×3 machine that works the nine blocks under it, and each of those columns reaches down past air, replaceable blocks and fluids, as far as its tier's reach, so ore a block or two below the layer on uneven ground is worked too; a drill head shows each column at work, in the bottom block or, where the source lies lower, placed in the cell above it with legs up to the Harvester. Over an oil well it pumps crude; otherwise it draws ore blocks one unit at a time, and breaks other ores for their loot. While it works, its firebox or coil lights up and it gives off light. The Burner Harvester burns anything a furnace burns and works every common resource; the Electric Harvester runs on FE at twice the speed, and the rare resources need it.
- **Harvest Recipes and Drilling Fluids.** A common resource has a fluid-free default, so a Burner Harvester works as soon as it is placed and fuelled. Uranium, diamond, netherite and oil wells ship as written recipes that need the Electric Harvester, and the first three also a Drilling Fluid: uranium and diamond spend 10 mB of an Overworld fluid a draw, netherite 25 mB of a Nether fluid. Pick the recipe in EMI or JEI, which show each one's minimum Harvester and fluid, and the Harvester holds it. Drilling mud, the Overworld fluid, is brewed in a brewing stand from a water bottle with a clay ball or a clay block. Magma slurry, the Nether fluid, is crafted from a lava bucket and soul soil; it flows like lava but is not lava, so it burns nothing, fuels no furnace and sets no crude into tar.
- **Hoppers are enough.** Fuel, mud in bottles or buckets, slurry in buckets, and empty buckets go in by hopper. Results, emptied bottles and filled crude buckets are pushed out of the Harvester's front into a chest, a hopper or a belt, and crude into a pipe. Pipe mods can fill its mud tank and drain its crude too.

## Gallery

Fieldworks carries a gallery of its features: one working scene each, all but the map markers, which need FTB Chunks and a patch the world generated, with a sign naming it, built in a row east of where you stand. It is a datapack in the jar, **Fieldworks Gallery**, off until you turn it on in a world's Data Packs screen or with `/datapack enable "mod/fieldworks:gallery_pack"`. Then `/function fieldworks:gallery/build` builds every scene, and `/function fieldworks:gallery/<scene>` one of them, such as `harvester` or `nether`. A function needs cheats in single player (on at world creation, or Open to LAN with cheats) or operator rights on a server. The gallery clears the ground it builds on and changes nothing else; it adds no recipes, loot or worldgen.

## For pack authors

- **Generation switches.** Each of the twelve resources and oil fields has a switch in `fieldworks-common.toml`. Turning one off stops new patches; those already generated stay. A world updating to a version with new resources gets their patches only in chunks generated afterwards.
- **Patch numbers** are a classpath resource, `fieldworks/ore/amounts.json`, read when blocks register: each resource's dimension (`overworld` or `nether`), its `min_distance` in Overworld-equivalent blocks and its amount, density, spot size and edge, and the `clearance` every patch and oil field keeps from one of another kind (16 blocks). A datapack cannot add a resource or change these.
- **Harvest Recipes** are `fieldworks:harvesting` recipes: a block or block-tag source (or the oil well), a result, a duration, an optional Drilling Fluid with its mB per operation, an optional minimum Harvester tier, and `replaces_default`, which makes a resource need its recipe, by hand as well as at the Harvester. A well's result is `{"crude_oil": <mB>}`, or `{"crude_oil": "cycle"}` for the well's own yield; a well's floor is final, so no recipe lifts it. Fieldworks' recipes for uranium, diamond, netherite scrap and the oil well are ordinary written recipes that replace the default, so a datapack overrides them by id: `fieldworks:uranium_from_ore`, `diamond_from_ore`, `netherite_scrap_from_ore` and `crude_from_oil_well`. The bonus recipes yield at most twice the default; yours are not capped.
- **Harvester tiers** are data: each tier's rank, speed, power (burn time, FE or both), the burn time an operation spends, its FE buffer and its `reach` (how many blocks down past the one under it its columns look, 0 when absent, no cap) are in `data/fieldworks/harvester_tier/`. Their names are in lang.
- **Tags.** `fieldworks:harvestable` (default `c:ores`) is what the Harvester breaks for loot; `#fieldworks:drilling_fluids` is what a recipe may spend, and holds `#fieldworks:drilling_fluids/overworld` (drilling mud) and `#fieldworks:drilling_fluids/nether` (magma slurry), where a pack adds its own fluids to a dimension. The Harvester checks no dimension: which one a recipe wants is the tag it names. `#fieldworks:patch_nether` (`#minecraft:is_nether`) and `#fieldworks:patch_land` are the biomes Patches generate in.

## For mod authors

- Crude oil is `fieldworks:crude_oil` in `c:crude_oil`, and any fluid in that tag sets as tar against lava; tar is `fieldworks:tar` in `c:tar`. Sulfur is in `c:dusts/sulfur` and uranium ingots in `c:ingots/uranium`. The ore blocks are in `c:ores` and each in `c:ores/<resource>`, but for stone. Magma slurry is `fieldworks:magma_slurry`, in no tag a mod reads but Fieldworks' own, and reachable through NeoForge's fluid capability like any bucket.
- The Harvester exposes NeoForge's item, fluid and energy capabilities on every block of its footprint. Its fluid face takes Drilling Fluids in and gives crude out.
- `CHANGELOG.md` lists what each version changed for a mod building against it.

## Dependencies

**Groundworks** is required and is a separate download. **FTB Chunks**, **Jade**, **EMI** and **JEI** are optional.

## Art

The coal, copper, iron, sulfur, uranium, diamond, gold, glowstone and quartz ore sprites (quartz wears his saltpeter), the redstone and netherite ones (his lapis and anthracite turned red and brown), the sulfur, raw uranium and uranium ingot sprites, and the crude oil sprite are by Malcolm Riley ([unused-textures](https://github.com/malcolmriley/unused-textures)), under CC BY 4.0; the cover and icon are drawn from them. The stone and tar sprites are Fieldworks' own. `NOTICE` and `REUSE.toml` record each file.

## Developing

A 5thlayer Module, released on its own to Modrinth and CurseForge. See `GLOSSARY.md` for the domain glossary. `sh ./gradlew build` runs the JUnit tests on a plain JVM, and `sh ./gradlew runGameTestServer` the game tests headless; the build reads Groundworks and libworks-runtime from `~/.m2`. Which operation a Harvester runs next is `harvester/OperationChoice`, with no Minecraft types, so it is tested without a world. Releases go through the libworks plugin's `release` task (`docs/agents/releases.md`).

## License

MIT, with the art above under CC BY 4.0. Source is on [GitHub](https://github.com/5thlayer/fieldworks).
Releases are on [Modrinth](https://modrinth.com/mod/V0pWlACG) and [CurseForge](https://www.curseforge.com/projects/1737485).
