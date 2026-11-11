// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * An ore block no patch placed starts at its {@code fieldworks:default_amount}, which a pack overrides (ADR 0013). All
 * of these share the {@code ore_amount_} prefix, so {@code --tests 'fieldworks:ore_amount_*'} selects them.
 */
final class DefaultAmountTests {

    private DefaultAmountTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("ore_amount_iron_outside_a_patch_starts_at_the_jars_number", 20, DefaultAmountTests::jarsNumber);
        tests.testOnItsOwn("ore_amount_a_pack_sets_iron_to_three_and_leaves_netherite_at_one", 100, DefaultAmountTests::setByAPack);
    }

    private static void jarsNumber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        OreBlock ore = FieldworksRegistries.ore(OreResource.IRON).get();
        level.setBlockAndUpdate(pos, ore.defaultBlockState());

        helper.assertValueEqual(OreMining.remaining(level, pos, ore), 6400, "an unpatched iron block's amount");
        helper.succeed();
    }

    private static void setByAPack(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        String id = OfferedPacks.id(OfferedPacks.ORE_AMOUNT);
        OfferedPacks.offer(server, OfferedPacks.ORE_AMOUNT, true);
        CompletableFuture<Void> on = GalleryCheck.select(server, id, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), id + " is not on yet"))
                .thenExecute(() -> {
                    try {
                        threeDrawsSpendIron(helper);
                        netheriteStartsAtOne(helper);
                    } finally {
                        off.set(GalleryCheck.select(server, id, false));
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), id + " is not off yet"))
                .thenExecute(() -> OfferedPacks.offer(server, OfferedPacks.ORE_AMOUNT, false))
                .thenSucceed();
    }

    private static void threeDrawsSpendIron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        OreBlock ore = FieldworksRegistries.ore(OreResource.IRON).get();
        level.setBlockAndUpdate(pos, ore.defaultBlockState());

        for (int draw = 1; draw <= 2; draw++) {
            OreMining.draw(level, ore, pos);
            helper.assertValueEqual(OreMining.remaining(level, pos, ore), 3 - draw, "iron left after " + draw + " draws");
        }
        OreMining.draw(level, ore, pos);
        helper.assertValueEqual(level.getBlockState(pos).getBlock(), Blocks.STONE, "the block after three draws");
    }

    /** A block the pack left without an entry starts at the floor of 1. */
    private static void netheriteStartsAtOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 1));
        OreBlock ore = FieldworksRegistries.ore(OreResource.NETHERITE).get();
        level.setBlockAndUpdate(pos, ore.defaultBlockState());

        helper.assertValueEqual(OreMining.remaining(level, pos, ore), 1, "an unpatched netherite block's amount with no entry");
    }
}
