# A Burner Harvester on an iron patch with nothing but hoppers: coal from a chest above, ore out of its front into a
# hopper and on into a chest.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

setblock ~11 ~-1 ~-7 fieldworks:iron_ore
setblock ~11 ~-1 ~-6 fieldworks:iron_ore
setblock ~11 ~-1 ~-5 fieldworks:iron_ore
setblock ~12 ~-1 ~-8 fieldworks:iron_ore
setblock ~12 ~-1 ~-7 fieldworks:iron_ore
setblock ~12 ~-1 ~-6 fieldworks:iron_ore
setblock ~12 ~-1 ~-5 fieldworks:iron_ore
setblock ~12 ~-1 ~-4 fieldworks:iron_ore
setblock ~13 ~-1 ~-8 fieldworks:iron_ore
setblock ~13 ~-1 ~-7 fieldworks:iron_ore
setblock ~13 ~-1 ~-6 fieldworks:iron_ore
setblock ~13 ~-1 ~-5 fieldworks:iron_ore
setblock ~13 ~-1 ~-4 fieldworks:iron_ore
setblock ~14 ~-1 ~-8 fieldworks:iron_ore
setblock ~14 ~-1 ~-7 fieldworks:iron_ore
setblock ~14 ~-1 ~-6 fieldworks:iron_ore
setblock ~14 ~-1 ~-5 fieldworks:iron_ore
setblock ~14 ~-1 ~-4 fieldworks:iron_ore
setblock ~15 ~-1 ~-7 fieldworks:iron_ore
setblock ~15 ~-1 ~-6 fieldworks:iron_ore
setblock ~15 ~-1 ~-5 fieldworks:iron_ore

setblock ~13 ~ ~-6 fieldworks:harvester_1[facing=north] strict
setblock ~12 ~ ~-7 fieldworks:harvester_1_part[facing=north,part=1] strict
setblock ~13 ~ ~-7 fieldworks:harvester_1_part[facing=north,part=2] strict
setblock ~14 ~ ~-7 fieldworks:harvester_1_part[facing=north,part=3] strict
setblock ~12 ~ ~-6 fieldworks:harvester_1_part[facing=north,part=4] strict
setblock ~14 ~ ~-6 fieldworks:harvester_1_part[facing=north,part=5] strict
setblock ~12 ~ ~-5 fieldworks:harvester_1_part[facing=north,part=6] strict
setblock ~13 ~ ~-5 fieldworks:harvester_1_part[facing=north,part=7] strict
setblock ~14 ~ ~-5 fieldworks:harvester_1_part[facing=north,part=8] strict
setblock ~12 ~1 ~-7 fieldworks:harvester_1_part[facing=north,part=9] strict
setblock ~13 ~1 ~-7 fieldworks:harvester_1_part[facing=north,part=10] strict
setblock ~14 ~1 ~-7 fieldworks:harvester_1_part[facing=north,part=11] strict
setblock ~12 ~1 ~-6 fieldworks:harvester_1_part[facing=north,part=12] strict
setblock ~13 ~1 ~-6 fieldworks:harvester_1_part[facing=north,part=13] strict
setblock ~14 ~1 ~-6 fieldworks:harvester_1_part[facing=north,part=14] strict
setblock ~12 ~1 ~-5 fieldworks:harvester_1_part[facing=north,part=15] strict
setblock ~13 ~1 ~-5 fieldworks:harvester_1_part[facing=north,part=16] strict
setblock ~14 ~1 ~-5 fieldworks:harvester_1_part[facing=north,part=17] strict
setblock ~12 ~2 ~-7 fieldworks:harvester_1_part[facing=north,part=18] strict
setblock ~13 ~2 ~-7 fieldworks:harvester_1_part[facing=north,part=19] strict
setblock ~14 ~2 ~-7 fieldworks:harvester_1_part[facing=north,part=20] strict
setblock ~12 ~2 ~-6 fieldworks:harvester_1_part[facing=north,part=21] strict
setblock ~13 ~2 ~-6 fieldworks:harvester_1_part[facing=north,part=22] strict
setblock ~14 ~2 ~-6 fieldworks:harvester_1_part[facing=north,part=23] strict
setblock ~12 ~2 ~-5 fieldworks:harvester_1_part[facing=north,part=24] strict
setblock ~13 ~2 ~-5 fieldworks:harvester_1_part[facing=north,part=25] strict
setblock ~14 ~2 ~-5 fieldworks:harvester_1_part[facing=north,part=26] strict
setblock ~13 ~4 ~-6 minecraft:chest{Items:[{Slot:0b,id:"minecraft:coal",count:64}]}
setblock ~13 ~3 ~-6 minecraft:hopper[facing=down]
setblock ~13 ~ ~-8 minecraft:hopper[facing=north]
setblock ~13 ~ ~-9 minecraft:chest[facing=south]

setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"fieldworks.gallery.hoppers"},{translate:"fieldworks.gallery.hoppers.1"},{translate:"fieldworks.gallery.hoppers.2"},{translate:"fieldworks.gallery.hoppers.3"}]}}
