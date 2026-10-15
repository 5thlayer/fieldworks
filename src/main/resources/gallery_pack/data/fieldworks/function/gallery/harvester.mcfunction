# A Burner Harvester fed coal by hopper and an Electric Harvester charged full of FE, each drawing a patch into a chest.
# No Module the gallery may use generates FE, so the Electric Harvester runs on its own charge until it is empty,
# unless a pack adds to #fieldworks:gallery/harvester_power, run here, a function that powers it: the Showcase
# places a Wireworks Creative Pole at ~21 ~ ~-6.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

setblock ~6 ~-1 ~-7 fieldworks:iron_ore
setblock ~6 ~-1 ~-6 fieldworks:iron_ore
setblock ~6 ~-1 ~-5 fieldworks:iron_ore
setblock ~7 ~-1 ~-8 fieldworks:iron_ore
setblock ~7 ~-1 ~-7 fieldworks:iron_ore
setblock ~7 ~-1 ~-6 fieldworks:iron_ore
setblock ~7 ~-1 ~-5 fieldworks:iron_ore
setblock ~7 ~-1 ~-4 fieldworks:iron_ore
setblock ~8 ~-1 ~-8 fieldworks:iron_ore
setblock ~8 ~-1 ~-7 fieldworks:iron_ore
setblock ~8 ~-1 ~-6 fieldworks:iron_ore
setblock ~8 ~-1 ~-5 fieldworks:iron_ore
setblock ~8 ~-1 ~-4 fieldworks:iron_ore
setblock ~9 ~-1 ~-8 fieldworks:iron_ore
setblock ~9 ~-1 ~-7 fieldworks:iron_ore
setblock ~9 ~-1 ~-6 fieldworks:iron_ore
setblock ~9 ~-1 ~-5 fieldworks:iron_ore
setblock ~9 ~-1 ~-4 fieldworks:iron_ore
setblock ~10 ~-1 ~-7 fieldworks:iron_ore
setblock ~10 ~-1 ~-6 fieldworks:iron_ore
setblock ~10 ~-1 ~-5 fieldworks:iron_ore
setblock ~16 ~-1 ~-7 fieldworks:copper_ore
setblock ~16 ~-1 ~-6 fieldworks:copper_ore
setblock ~16 ~-1 ~-5 fieldworks:copper_ore
setblock ~17 ~-1 ~-8 fieldworks:copper_ore
setblock ~17 ~-1 ~-7 fieldworks:copper_ore
setblock ~17 ~-1 ~-6 fieldworks:copper_ore
setblock ~17 ~-1 ~-5 fieldworks:copper_ore
setblock ~17 ~-1 ~-4 fieldworks:copper_ore
setblock ~18 ~-1 ~-8 fieldworks:copper_ore
setblock ~18 ~-1 ~-7 fieldworks:copper_ore
setblock ~18 ~-1 ~-6 fieldworks:copper_ore
setblock ~18 ~-1 ~-5 fieldworks:copper_ore
setblock ~18 ~-1 ~-4 fieldworks:copper_ore
setblock ~19 ~-1 ~-8 fieldworks:copper_ore
setblock ~19 ~-1 ~-7 fieldworks:copper_ore
setblock ~19 ~-1 ~-6 fieldworks:copper_ore
setblock ~19 ~-1 ~-5 fieldworks:copper_ore
setblock ~19 ~-1 ~-4 fieldworks:copper_ore
setblock ~20 ~-1 ~-7 fieldworks:copper_ore
setblock ~20 ~-1 ~-6 fieldworks:copper_ore
setblock ~20 ~-1 ~-5 fieldworks:copper_ore

setblock ~8 ~ ~-6 fieldworks:harvester_1[facing=north] strict
setblock ~7 ~ ~-7 fieldworks:harvester_1_part[facing=north,part=1] strict
setblock ~8 ~ ~-7 fieldworks:harvester_1_part[facing=north,part=2] strict
setblock ~9 ~ ~-7 fieldworks:harvester_1_part[facing=north,part=3] strict
setblock ~7 ~ ~-6 fieldworks:harvester_1_part[facing=north,part=4] strict
setblock ~9 ~ ~-6 fieldworks:harvester_1_part[facing=north,part=5] strict
setblock ~7 ~ ~-5 fieldworks:harvester_1_part[facing=north,part=6] strict
setblock ~8 ~ ~-5 fieldworks:harvester_1_part[facing=north,part=7] strict
setblock ~9 ~ ~-5 fieldworks:harvester_1_part[facing=north,part=8] strict
setblock ~7 ~1 ~-7 fieldworks:harvester_1_part[facing=north,part=9] strict
setblock ~8 ~1 ~-7 fieldworks:harvester_1_part[facing=north,part=10] strict
setblock ~9 ~1 ~-7 fieldworks:harvester_1_part[facing=north,part=11] strict
setblock ~7 ~1 ~-6 fieldworks:harvester_1_part[facing=north,part=12] strict
setblock ~8 ~1 ~-6 fieldworks:harvester_1_part[facing=north,part=13] strict
setblock ~9 ~1 ~-6 fieldworks:harvester_1_part[facing=north,part=14] strict
setblock ~7 ~1 ~-5 fieldworks:harvester_1_part[facing=north,part=15] strict
setblock ~8 ~1 ~-5 fieldworks:harvester_1_part[facing=north,part=16] strict
setblock ~9 ~1 ~-5 fieldworks:harvester_1_part[facing=north,part=17] strict
setblock ~7 ~2 ~-7 fieldworks:harvester_1_part[facing=north,part=18] strict
setblock ~8 ~2 ~-7 fieldworks:harvester_1_part[facing=north,part=19] strict
setblock ~9 ~2 ~-7 fieldworks:harvester_1_part[facing=north,part=20] strict
setblock ~7 ~2 ~-6 fieldworks:harvester_1_part[facing=north,part=21] strict
setblock ~8 ~2 ~-6 fieldworks:harvester_1_part[facing=north,part=22] strict
setblock ~9 ~2 ~-6 fieldworks:harvester_1_part[facing=north,part=23] strict
setblock ~7 ~2 ~-5 fieldworks:harvester_1_part[facing=north,part=24] strict
setblock ~8 ~2 ~-5 fieldworks:harvester_1_part[facing=north,part=25] strict
setblock ~9 ~2 ~-5 fieldworks:harvester_1_part[facing=north,part=26] strict
setblock ~8 ~3 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal",count:64}]}
setblock ~8 ~ ~-8 minecraft:chest[facing=south]

setblock ~18 ~ ~-6 fieldworks:harvester_2[facing=north]{energy:{energy:50000}} strict
setblock ~17 ~ ~-7 fieldworks:harvester_2_part[facing=north,part=1] strict
setblock ~18 ~ ~-7 fieldworks:harvester_2_part[facing=north,part=2] strict
setblock ~19 ~ ~-7 fieldworks:harvester_2_part[facing=north,part=3] strict
setblock ~17 ~ ~-6 fieldworks:harvester_2_part[facing=north,part=4] strict
setblock ~19 ~ ~-6 fieldworks:harvester_2_part[facing=north,part=5] strict
setblock ~17 ~ ~-5 fieldworks:harvester_2_part[facing=north,part=6] strict
setblock ~18 ~ ~-5 fieldworks:harvester_2_part[facing=north,part=7] strict
setblock ~19 ~ ~-5 fieldworks:harvester_2_part[facing=north,part=8] strict
setblock ~17 ~1 ~-7 fieldworks:harvester_2_part[facing=north,part=9] strict
setblock ~18 ~1 ~-7 fieldworks:harvester_2_part[facing=north,part=10] strict
setblock ~19 ~1 ~-7 fieldworks:harvester_2_part[facing=north,part=11] strict
setblock ~17 ~1 ~-6 fieldworks:harvester_2_part[facing=north,part=12] strict
setblock ~18 ~1 ~-6 fieldworks:harvester_2_part[facing=north,part=13] strict
setblock ~19 ~1 ~-6 fieldworks:harvester_2_part[facing=north,part=14] strict
setblock ~17 ~1 ~-5 fieldworks:harvester_2_part[facing=north,part=15] strict
setblock ~18 ~1 ~-5 fieldworks:harvester_2_part[facing=north,part=16] strict
setblock ~19 ~1 ~-5 fieldworks:harvester_2_part[facing=north,part=17] strict
setblock ~17 ~2 ~-7 fieldworks:harvester_2_part[facing=north,part=18] strict
setblock ~18 ~2 ~-7 fieldworks:harvester_2_part[facing=north,part=19] strict
setblock ~19 ~2 ~-7 fieldworks:harvester_2_part[facing=north,part=20] strict
setblock ~17 ~2 ~-6 fieldworks:harvester_2_part[facing=north,part=21] strict
setblock ~18 ~2 ~-6 fieldworks:harvester_2_part[facing=north,part=22] strict
setblock ~19 ~2 ~-6 fieldworks:harvester_2_part[facing=north,part=23] strict
setblock ~17 ~2 ~-5 fieldworks:harvester_2_part[facing=north,part=24] strict
setblock ~18 ~2 ~-5 fieldworks:harvester_2_part[facing=north,part=25] strict
setblock ~19 ~2 ~-5 fieldworks:harvester_2_part[facing=north,part=26] strict
setblock ~18 ~ ~-8 minecraft:chest[facing=south]
function #fieldworks:gallery/harvester_power

setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"fieldworks.gallery.harvester"},{translate:"fieldworks.gallery.harvester.1"},{translate:"fieldworks.gallery.harvester.2"},{translate:"fieldworks.gallery.harvester.3"}]}}
