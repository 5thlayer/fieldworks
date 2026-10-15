// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * The Gallery pack builds a scene of each README feature, in README order, through libworks'
 * {@link GalleryCheck} (libworks ADR 0010).
 */
final class GalleryTests {

    private GalleryTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("the_gallery_builds_a_scene_of_each_readme_feature", GalleryCheck.TICKS,
                helper -> GalleryCheck.builds(helper, Fieldworks.MOD_ID, new BlockPos(0, 1, 8), List.of(
                        new Scene("ore_patches", new BlockPos(6, -1, -3), FieldworksRegistries.ore(OreResource.IRON).get()),
                        new Scene("ore_amounts", new BlockPos(18, -1, -6), FieldworksRegistries.ore(OreResource.URANIUM).get()),
                        new Scene("oil_fields", new BlockPos(13, -1, -6), FieldworksRegistries.OIL_WELL.get()),
                        new Scene("map_markers", new BlockPos(13, -1, -6), FieldworksRegistries.ore(OreResource.COPPER).get()),
                        new Scene("harvester", new BlockPos(18, 0, -6), Harvesters.block(TierBlock.HARVESTER_2).get()),
                        new Scene("drilling_fluids", new BlockPos(8, 0, -3), Blocks.BREWING_STAND),
                        new Scene("hoppers", new BlockPos(13, 0, -6), Harvesters.block(TierBlock.HARVESTER_1).get()))));
    }
}
