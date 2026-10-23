# Builds each scene in README order, the first where the player stands and each further one 24 blocks east (libworks ADR 0010).
function fieldworks:gallery/ore_patches
execute positioned ~24 ~ ~ run function fieldworks:gallery/ore_amounts
execute positioned ~48 ~ ~ run function fieldworks:gallery/oil_fields
execute positioned ~72 ~ ~ run function fieldworks:gallery/harvester
execute positioned ~96 ~ ~ run function fieldworks:gallery/drilling_fluids
execute positioned ~120 ~ ~ run function fieldworks:gallery/nether
