# Fieldworks

Ore that comes in **patches**, and oil that comes from **wells**. Fieldworks makes resources something you find, claim and work, the way a factory game does, without replacing vanilla's ores.

## What it adds

- **Ore patches** of iron, copper, coal, stone, uranium and sulfur, generated in new land more than 150 blocks from the world's origin. A patch is a disc of ore blocks that share one total, which grows the further out you go.
- **Ore blocks that hold an amount.** Each break with a vanilla pickaxe takes one unit and gives one item, and the block stays. It turns to stone only when its last unit is mined. The block's look thins out as it empties, and Jade shows what is left.
- **Oil fields** of oil wells. A well yields **crude oil** (`c:crude_oil`) and decays to a floor, but never runs dry.
- **Sulfur, raw uranium and uranium ingots**, in `c:dusts/sulfur` and `c:ingots/uranium` for other mods' recipes.
- **One map marker per patch** on FTB Chunks' map, showing its resource and what is left, for every player once anyone has found it.

Every resource and oil fields have a generation switch in `fieldworks-common.toml`. Patches already generated stay.

## Good to know

- Fieldworks plays alone in vanilla. FTB Chunks and Jade are optional.
- There is no machine yet to pump a well. The Harvester, which works patches and pumps wells, is coming in a later version.
- Fieldworks is part of the FactoryWorks suite of mods, and the FactoryWorks pack uses it.

## Credits

The coal, copper, iron and uranium ore sprites and the crude oil sprite are by Malcolm Riley ([unused-textures](https://github.com/malcolmriley/unused-textures)), under CC BY 4.0. Fieldworks is MIT licensed and was written with Claude.
