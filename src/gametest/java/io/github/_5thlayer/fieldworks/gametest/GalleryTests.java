// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * The Gallery pack builds a scene of each README feature but the map markers, in README order, through
 * libworks' {@link GalleryCheck} (libworks ADR 0010). A marker needs FTB Chunks and a patch the world
 * generated, neither of which a scene can place. A scene's sign, clicked, builds the scene again. Each
 * test turns the pack on, which reloads the server's data, so each runs in a batch of its own.
 */
final class GalleryTests {

    /** Where the gallery is run from: the platform's south-west corner, its plots spilling north and east of the platform. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** Where a scene's sign stands, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(13, 0, 0);
    /** The harvester scene's Electric Harvester and the chest it fills, from where the scene is run. */
    private static final BlockPos ELECTRIC_HARVESTER = new BlockPos(18, 0, -6);
    private static final BlockPos ELECTRIC_CHEST = new BlockPos(18, 0, -8);

    private GalleryTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.testOnItsOwn("the_gallery_builds_a_scene_of_each_feature_it_can_show", GalleryCheck.TICKS,
                helper -> GalleryCheck.builds(helper, Fieldworks.MOD_ID, ORIGIN, List.of(
                        new Scene("ore_patches", new BlockPos(6, -1, -3), FieldworksRegistries.ore(OreResource.IRON).get()),
                        new Scene("ore_amounts", new BlockPos(18, -1, -6), FieldworksRegistries.ore(OreResource.URANIUM).get()),
                        new Scene("oil_fields", new BlockPos(13, -1, -6), FieldworksRegistries.OIL_WELL.get()),
                        new Scene("harvester", ELECTRIC_HARVESTER, Harvesters.block(TierBlock.HARVESTER_2).get()),
                        new Scene("drilling_fluids", new BlockPos(8, 0, -3), Blocks.BREWING_STAND))));
        tests.testOnItsOwn("the_gallery_scenes_sign_clicked_builds_the_scene_again_and_leaves_nothing_loose", GalleryCheck.TICKS,
                GalleryTests::signReplays);
    }

    /**
     * The harvester scene's sign, clicked, builds the scene afresh where it stands: its Electric Harvester,
     * taken away, again, its chest empty of what the first run gathered, and nothing the clear knocked loose left lying.
     */
    private static void signReplays(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var at = helper.absolutePos(ORIGIN);
        var source = GalleryCheck.operator(helper, at);
        var plot = GalleryCheck.plot(at);
        var pack = GalleryPack.id(Fieldworks.MOD_ID);
        var sign = at.offset(SIGN);
        var chest = at.offset(ELECTRIC_CHEST);
        var on = GalleryCheck.select(server, pack, true);
        clearAwayIfItFails(helper, plot, pack, on);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), pack + " is not on yet"))
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Fieldworks.MOD_ID, "harvester"));
                    if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity box) || !box.isEmpty()) {
                        throw helper.assertionException("the harvester scene's chest is not where it should be, or not empty");
                    }
                    box.setItem(0, new ItemStack(Items.DIAMOND));
                    level.removeBlock(at.offset(ELECTRIC_HARVESTER), false);
                    helper.assertTrue(level.getBlockEntity(sign) instanceof SignBlockEntity entity
                                    && entity.executeClickCommandsIfPresent(level, source.getPlayer(), sign, true),
                            "the scene's sign runs no command when clicked");
                    helper.assertTrue(level.getBlockState(at.offset(ELECTRIC_HARVESTER)).is(Harvesters.block(TierBlock.HARVESTER_2).get()),
                            "clicking the sign did not build the Electric Harvester again where it stood");
                    helper.assertTrue(level.getBlockEntity(chest) instanceof ChestBlockEntity rebuilt && rebuilt.isEmpty(),
                            "clicking the sign did not build the chest afresh");
                    var loose = level.getEntitiesOfClass(ItemEntity.class, AABB.of(plot));
                    helper.assertTrue(loose.isEmpty(), "building the scene again left items loose: " + loose.stream().map(ItemEntity::getItem).toList());
                    StructureUtils.clearSpaceForStructure(plot, level);
                    off.set(GalleryCheck.select(server, pack, false));
                })
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), pack + " is not off yet"))
                .thenSucceed();
    }

    /**
     * A failed test skips its own last steps, which would leave the plot built and the pack on under the
     * batches after it. It turns the pack off only once turning it on is done, since two reloads race.
     */
    private static void clearAwayIfItFails(GameTestHelper helper, BoundingBox plot,
            String pack, CompletableFuture<Void> on) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
            }

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                var level = helper.getLevel();
                StructureUtils.clearSpaceForStructure(plot, level);
                var server = level.getServer();
                on.whenComplete((done, failed) -> server.execute(() -> GalleryCheck.select(server, pack, false)));
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
            }
        });
    }
}
