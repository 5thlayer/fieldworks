# Ore to mine by hand, and the pickaxes to mine it with: each break takes one unit and the block stays.
function fieldworks:gallery/clear

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

function fieldworks:gallery/sign {scene:"ore_amounts"}
