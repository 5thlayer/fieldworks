# A scene's sign at ~13 ~ ~, facing south, waxed. Its name is in bold, and clicking it runs the scene's function
# again from the plot's corner, 13 blocks west of the sign, which builds it afresh.
$setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"fieldworks.gallery.$(scene)",bold:true,click_event:{action:"run_command",command:"execute positioned ~-13 ~ ~ run function fieldworks:gallery/$(scene)"}},{translate:"fieldworks.gallery.$(scene).1"},{translate:"fieldworks.gallery.$(scene).2"},{translate:"fieldworks.gallery.$(scene).3"}]}}
