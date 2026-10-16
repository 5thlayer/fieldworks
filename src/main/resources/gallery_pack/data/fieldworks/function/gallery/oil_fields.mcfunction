# An oil field's wells, each its own amount, and a Burner Harvester pumping one by its Default recipe: empty
# buckets in by hopper come out of its front full of crude.
function fieldworks:gallery/clear

setblock ~13 ~-1 ~-6 fieldworks:oil_well{Amount:600000L,Initial:600000L}
setblock ~6 ~-1 ~-3 fieldworks:oil_well{Amount:450000L,Initial:450000L}
setblock ~20 ~-1 ~-3 fieldworks:oil_well{Amount:700000L,Initial:700000L}
setblock ~8 ~-1 ~-10 fieldworks:oil_well{Amount:520000L,Initial:520000L}
setblock ~19 ~-1 ~-10 fieldworks:oil_well{Amount:380000L,Initial:380000L}

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
setblock ~13 ~3 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal",count:32},{Slot:1b,id:"minecraft:bucket",count:16}]}
setblock ~13 ~ ~-8 minecraft:chest[facing=south]

function fieldworks:gallery/sign {scene:"oil_fields"}
