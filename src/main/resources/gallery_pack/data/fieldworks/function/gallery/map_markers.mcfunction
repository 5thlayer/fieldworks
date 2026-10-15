# A patch. FTB Chunks' map marks each patch a world generates once it is found, but FTB Chunks is a third-party mod,
# which a scene never uses (libworks docs/runtime.md, Layout), so the sign says where the marker shows.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

setblock ~10 ~-1 ~-7 fieldworks:copper_ore
setblock ~10 ~-1 ~-6 fieldworks:copper_ore
setblock ~10 ~-1 ~-5 fieldworks:copper_ore
setblock ~11 ~-1 ~-8 fieldworks:copper_ore
setblock ~11 ~-1 ~-7 fieldworks:copper_ore
setblock ~11 ~-1 ~-6 fieldworks:copper_ore
setblock ~11 ~-1 ~-5 fieldworks:copper_ore
setblock ~11 ~-1 ~-4 fieldworks:copper_ore
setblock ~12 ~-1 ~-9 fieldworks:copper_ore
setblock ~12 ~-1 ~-8 fieldworks:copper_ore
setblock ~12 ~-1 ~-7 fieldworks:copper_ore
setblock ~12 ~-1 ~-6 fieldworks:copper_ore
setblock ~12 ~-1 ~-5 fieldworks:copper_ore
setblock ~12 ~-1 ~-4 fieldworks:copper_ore
setblock ~12 ~-1 ~-3 fieldworks:copper_ore
setblock ~13 ~-1 ~-9 fieldworks:copper_ore
setblock ~13 ~-1 ~-8 fieldworks:copper_ore
setblock ~13 ~-1 ~-7 fieldworks:copper_ore
setblock ~13 ~-1 ~-6 fieldworks:copper_ore
setblock ~13 ~-1 ~-5 fieldworks:copper_ore
setblock ~13 ~-1 ~-4 fieldworks:copper_ore
setblock ~13 ~-1 ~-3 fieldworks:copper_ore
setblock ~14 ~-1 ~-9 fieldworks:copper_ore
setblock ~14 ~-1 ~-8 fieldworks:copper_ore
setblock ~14 ~-1 ~-7 fieldworks:copper_ore
setblock ~14 ~-1 ~-6 fieldworks:copper_ore
setblock ~14 ~-1 ~-5 fieldworks:copper_ore
setblock ~14 ~-1 ~-4 fieldworks:copper_ore
setblock ~14 ~-1 ~-3 fieldworks:copper_ore
setblock ~15 ~-1 ~-8 fieldworks:copper_ore
setblock ~15 ~-1 ~-7 fieldworks:copper_ore
setblock ~15 ~-1 ~-6 fieldworks:copper_ore
setblock ~15 ~-1 ~-5 fieldworks:copper_ore
setblock ~15 ~-1 ~-4 fieldworks:copper_ore
setblock ~16 ~-1 ~-7 fieldworks:copper_ore
setblock ~16 ~-1 ~-6 fieldworks:copper_ore
setblock ~16 ~-1 ~-5 fieldworks:copper_ore

setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"fieldworks.gallery.map_markers"},{translate:"fieldworks.gallery.map_markers.1"},{translate:"fieldworks.gallery.map_markers.2"},{translate:"fieldworks.gallery.map_markers.3"}]}}
