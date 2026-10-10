# Fieldworks

Ore patches, oil fields and the Harvester that works them, for NeoForge. Resources become something you
find, claim and work, the way a factory game does, beside vanilla's own ores.

## What it adds

- **Ore patches** of iron, copper, coal, stone, uranium and sulfur, generated in new land more than 150
  blocks from the origin. A patch is a disc of ore blocks sharing one total, which grows the further
  out it lies. Each resource has a generation switch in `fieldworks-common.toml`.
- **Ore blocks that hold an amount.** A break with a vanilla pickaxe, at vanilla hardness and tool
  tiers, takes one unit and gives one item, and the block stays until its last unit, when it becomes
  stone.
- **Oil fields** of oil wells, whose crude oil (`c:crude_oil`) decays to a floor and never runs dry.
  Crude has its own bucket.
- **Sulfur, raw uranium and uranium ingots**, in `c:dusts/sulfur` and `c:ingots/uranium`.
- **One map marker per patch** on FTB Chunks' map, with what is left, for every player once anyone has
  found it.
- **The Harvester**, a 3×3×3 machine that works the layer under it. Over an oil well it pumps crude;
  otherwise it draws ore blocks and breaks any block in `fieldworks:harvestable` (default `c:ores`) for
  its loot. It comes in two tiers, the Burner Harvester and the Electric Harvester, whose speed and
  power (burn time, FE or both) are data a pack can change.
- **Harvest Recipes**, shown in EMI and JEI. Every source has a fluid-free default, so a Harvester works
  as soon as it is placed and powered; written recipes add Drilling Fluid variants and bonuses, and a
  player picks one from the recipe viewer as the Harvester's Held recipe. The first Drilling Fluid,
  drilling mud, is brewed in a vanilla brewing stand.
- **Hoppers are enough.** Fuel, Drilling Fluid in bottles or buckets, and empty buckets go in by hopper;
  results, emptied containers and filled crude buckets are pushed out of one face into a chest, a
  hopper or a belt. Pipe mods reach the same tanks.

Fieldworks plays alone in vanilla. It needs Groundworks, and FTB Chunks, Jade, EMI and JEI are
optional. It began as FactoryWorks Core in [5thlayer/factoryworks](https://github.com/5thlayer/factoryworks),
whose pack consumes it (FactoryWorks ADR-0127).

## Building

`sh ./gradlew build` builds the jar and runs the JUnit tests; `sh ./gradlew runGameTestServer` runs the
game tests headless. The build reads Groundworks and libworks-runtime from `~/.m2`. Releases go
through `scripts/release.sh` (`docs/agents/releases.md`).

## Licence

MIT, under REUSE. The coal, copper, iron and uranium ore sprites and the crude oil sprite are Malcolm
Riley's, under CC BY 4.0 (`NOTICE`).
