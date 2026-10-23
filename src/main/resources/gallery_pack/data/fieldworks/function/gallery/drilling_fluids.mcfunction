# An Electric Harvester charged full of FE on a uranium patch, holding the shipped recipe that needs drilling mud:
# uranium is gated behind the Electric Harvester and the Overworld Drilling Fluid. Mud goes in by hopper in bottles;
# the brewing stand beside it brews more from water bottles and clay.
function fieldworks:gallery/clear

setblock ~10 ~-1 ~-7 fieldworks:uranium_ore
setblock ~10 ~-1 ~-6 fieldworks:uranium_ore
setblock ~10 ~-1 ~-5 fieldworks:uranium_ore
setblock ~11 ~-1 ~-8 fieldworks:uranium_ore
setblock ~11 ~-1 ~-7 fieldworks:uranium_ore
setblock ~11 ~-1 ~-6 fieldworks:uranium_ore
setblock ~11 ~-1 ~-5 fieldworks:uranium_ore
setblock ~11 ~-1 ~-4 fieldworks:uranium_ore
setblock ~12 ~-1 ~-9 fieldworks:uranium_ore
setblock ~12 ~-1 ~-8 fieldworks:uranium_ore
setblock ~12 ~-1 ~-7 fieldworks:uranium_ore
setblock ~12 ~-1 ~-6 fieldworks:uranium_ore
setblock ~12 ~-1 ~-5 fieldworks:uranium_ore
setblock ~12 ~-1 ~-4 fieldworks:uranium_ore
setblock ~12 ~-1 ~-3 fieldworks:uranium_ore
setblock ~13 ~-1 ~-9 fieldworks:uranium_ore
setblock ~13 ~-1 ~-8 fieldworks:uranium_ore
setblock ~13 ~-1 ~-7 fieldworks:uranium_ore
setblock ~13 ~-1 ~-6 fieldworks:uranium_ore
setblock ~13 ~-1 ~-5 fieldworks:uranium_ore
setblock ~13 ~-1 ~-4 fieldworks:uranium_ore
setblock ~13 ~-1 ~-3 fieldworks:uranium_ore
setblock ~14 ~-1 ~-9 fieldworks:uranium_ore
setblock ~14 ~-1 ~-8 fieldworks:uranium_ore
setblock ~14 ~-1 ~-7 fieldworks:uranium_ore
setblock ~14 ~-1 ~-6 fieldworks:uranium_ore
setblock ~14 ~-1 ~-5 fieldworks:uranium_ore
setblock ~14 ~-1 ~-4 fieldworks:uranium_ore
setblock ~14 ~-1 ~-3 fieldworks:uranium_ore
setblock ~15 ~-1 ~-8 fieldworks:uranium_ore
setblock ~15 ~-1 ~-7 fieldworks:uranium_ore
setblock ~15 ~-1 ~-6 fieldworks:uranium_ore
setblock ~15 ~-1 ~-5 fieldworks:uranium_ore
setblock ~15 ~-1 ~-4 fieldworks:uranium_ore
setblock ~16 ~-1 ~-7 fieldworks:uranium_ore
setblock ~16 ~-1 ~-6 fieldworks:uranium_ore
setblock ~16 ~-1 ~-5 fieldworks:uranium_ore

setblock ~13 ~ ~-6 fieldworks:harvester_2[facing=north]{energy:{energy:50000},held_recipe:"fieldworks:uranium_from_ore"} strict
setblock ~12 ~ ~-7 fieldworks:harvester_2_part[facing=north,part=1] strict
setblock ~13 ~ ~-7 fieldworks:harvester_2_part[facing=north,part=2] strict
setblock ~14 ~ ~-7 fieldworks:harvester_2_part[facing=north,part=3] strict
setblock ~12 ~ ~-6 fieldworks:harvester_2_part[facing=north,part=4] strict
setblock ~14 ~ ~-6 fieldworks:harvester_2_part[facing=north,part=5] strict
setblock ~12 ~ ~-5 fieldworks:harvester_2_part[facing=north,part=6] strict
setblock ~13 ~ ~-5 fieldworks:harvester_2_part[facing=north,part=7] strict
setblock ~14 ~ ~-5 fieldworks:harvester_2_part[facing=north,part=8] strict
setblock ~12 ~1 ~-7 fieldworks:harvester_2_part[facing=north,part=9] strict
setblock ~13 ~1 ~-7 fieldworks:harvester_2_part[facing=north,part=10] strict
setblock ~14 ~1 ~-7 fieldworks:harvester_2_part[facing=north,part=11] strict
setblock ~12 ~1 ~-6 fieldworks:harvester_2_part[facing=north,part=12] strict
setblock ~13 ~1 ~-6 fieldworks:harvester_2_part[facing=north,part=13] strict
setblock ~14 ~1 ~-6 fieldworks:harvester_2_part[facing=north,part=14] strict
setblock ~12 ~1 ~-5 fieldworks:harvester_2_part[facing=north,part=15] strict
setblock ~13 ~1 ~-5 fieldworks:harvester_2_part[facing=north,part=16] strict
setblock ~14 ~1 ~-5 fieldworks:harvester_2_part[facing=north,part=17] strict
setblock ~12 ~2 ~-7 fieldworks:harvester_2_part[facing=north,part=18] strict
setblock ~13 ~2 ~-7 fieldworks:harvester_2_part[facing=north,part=19] strict
setblock ~14 ~2 ~-7 fieldworks:harvester_2_part[facing=north,part=20] strict
setblock ~12 ~2 ~-6 fieldworks:harvester_2_part[facing=north,part=21] strict
setblock ~13 ~2 ~-6 fieldworks:harvester_2_part[facing=north,part=22] strict
setblock ~14 ~2 ~-6 fieldworks:harvester_2_part[facing=north,part=23] strict
setblock ~12 ~2 ~-5 fieldworks:harvester_2_part[facing=north,part=24] strict
setblock ~13 ~2 ~-5 fieldworks:harvester_2_part[facing=north,part=25] strict
setblock ~14 ~2 ~-5 fieldworks:harvester_2_part[facing=north,part=26] strict
setblock ~11 ~ ~-6 minecraft:hopper[facing=east]{Items:[{Slot:0b,id:"fieldworks:drilling_mud_bottle",count:16}]}
setblock ~13 ~ ~-8 minecraft:chest[facing=south]

setblock ~8 ~ ~-3 minecraft:brewing_stand{Items:[{Slot:0b,id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:water"}}},{Slot:1b,id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:water"}}},{Slot:2b,id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:water"}}},{Slot:3b,id:"minecraft:clay_ball",count:3},{Slot:4b,id:"minecraft:blaze_powder",count:1}]}

function fieldworks:gallery/sign {scene:"drilling_fluids"}
