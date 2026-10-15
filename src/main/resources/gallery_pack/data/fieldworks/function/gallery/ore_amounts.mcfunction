# Ore to mine by hand, and the pickaxes to mine it with: each break takes one unit and the block stays.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

setblock ~7 ~-1 ~-7 fieldworks:iron_ore
setblock ~7 ~-1 ~-6 fieldworks:iron_ore
setblock ~7 ~-1 ~-5 fieldworks:iron_ore
setblock ~8 ~-1 ~-7 fieldworks:iron_ore
setblock ~8 ~-1 ~-6 fieldworks:iron_ore
setblock ~8 ~-1 ~-5 fieldworks:iron_ore
setblock ~9 ~-1 ~-7 fieldworks:iron_ore
setblock ~9 ~-1 ~-6 fieldworks:iron_ore
setblock ~9 ~-1 ~-5 fieldworks:iron_ore
setblock ~17 ~-1 ~-7 fieldworks:uranium_ore
setblock ~17 ~-1 ~-6 fieldworks:uranium_ore
setblock ~17 ~-1 ~-5 fieldworks:uranium_ore
setblock ~18 ~-1 ~-7 fieldworks:uranium_ore
setblock ~18 ~-1 ~-6 fieldworks:uranium_ore
setblock ~18 ~-1 ~-5 fieldworks:uranium_ore
setblock ~19 ~-1 ~-7 fieldworks:uranium_ore
setblock ~19 ~-1 ~-6 fieldworks:uranium_ore
setblock ~19 ~-1 ~-5 fieldworks:uranium_ore

setblock ~13 ~ ~-6 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:stone_pickaxe",count:1},{Slot:1b,id:"minecraft:iron_pickaxe",count:1}]}

setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"fieldworks.gallery.ore_amounts"},{translate:"fieldworks.gallery.ore_amounts.1"},{translate:"fieldworks.gallery.ore_amounts.2"},{translate:"fieldworks.gallery.ore_amounts.3"}]}}
