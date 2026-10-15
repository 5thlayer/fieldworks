# A well decayed to its floor, under a Burner Harvester holding the shipped drilling mud recipe, which beats the floor.
# Mud goes in by hopper in bottles; the brewing stand beside brews more from water bottles and clay.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

setblock ~13 ~-1 ~-6 fieldworks:oil_well{Amount:60000L,Initial:300000L}

setblock ~13 ~ ~-6 fieldworks:harvester_1[facing=north]{held_recipe:"fieldworks:crude_from_drilling_mud"} strict
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
setblock ~13 ~3 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal",count:32},{Slot:1b,id:"minecraft:bucket",count:16}]}
setblock ~11 ~ ~-6 minecraft:hopper[facing=east]{Items:[{Slot:0b,id:"fieldworks:drilling_mud_bottle",count:16}]}
setblock ~13 ~ ~-8 minecraft:chest[facing=south]

setblock ~8 ~ ~-3 minecraft:brewing_stand{Items:[{Slot:0b,id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:water"}}},{Slot:1b,id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:water"}}},{Slot:2b,id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:water"}}},{Slot:3b,id:"minecraft:clay_ball",count:3},{Slot:4b,id:"minecraft:blaze_powder",count:1}]}

setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"fieldworks.gallery.drilling_fluids"},{translate:"fieldworks.gallery.drilling_fluids.1"},{translate:"fieldworks.gallery.drilling_fluids.2"},{translate:"fieldworks.gallery.drilling_fluids.3"}]}}
