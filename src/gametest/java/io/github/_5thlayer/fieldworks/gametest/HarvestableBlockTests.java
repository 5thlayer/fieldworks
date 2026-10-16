// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Harvestable blocks on a real server (#6): a Harvester breaks a block in {@code fieldworks:harvestable} for its
 * loot, which lands in the chest at its output face, and leaves air.
 */
final class HarvestableBlockTests {

    private static final BlockPos CENTRE = ORIGIN.below();
    private static final BlockPos CHEST = ORIGIN.relative(Direction.NORTH, 2);

    private HarvestableBlockTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_burner_harvester_breaks_a_vanilla_ore_to_air_and_its_loot_reaches_the_chest", 200,
                helper -> breaks(helper, Blocks.IRON_ORE, Items.RAW_IRON));
        tests.test("a_burner_harvester_breaks_diamond_ore_which_needs_a_better_pick_by_hand", 200,
                helper -> breaks(helper, Blocks.DIAMOND_ORE, Items.DIAMOND));
        tests.test("a_burner_harvester_draws_an_ore_block_tagged_c_ores_and_never_breaks_it", 200,
                HarvestableBlockTests::drawsOreBlock);
        tests.test("a_burner_harvester_works_an_ore_block_and_a_harvestable_block_in_turn_and_skips_other_blocks", 400,
                HarvestableBlockTests::mixedFootprint);
    }

    private static void breaks(GameTestHelper helper, Block ore, Item loot) {
        helper.setBlock(CENTRE, ore);
        feed(helper);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, loot), 1, "loot in the chest");
            helper.assertBlockPresent(Blocks.AIR, CENTRE);
            helper.assertItemEntityNotPresent(loot);
        });
    }

    /** The ore block is in {@code c:ores}, and so in the harvestable tag, yet each operation takes one unit of it. */
    private static void drawsOreBlock(GameTestHelper helper) {
        int initial = HarvesterTests.ore(helper, CENTRE, 0);
        feed(helper);

        helper.succeedWhen(() -> {
            int arrived = HarvesterTests.count(helper, CHEST, Items.RAW_IRON);
            helper.assertTrue(arrived >= 2, arrived + " raw iron in the chest, waiting for 2");
            helper.assertValueEqual(HarvesterTests.remaining(helper, CENTRE), initial - arrived, "the ore block's amount");
        });
    }

    /**
     * A drawn ore block, a diamond ore and a dirt block in the layer: the dirt is in neither group and is left
     * alone, the diamond ore is broken once, and the ore block goes on being drawn.
     */
    private static void mixedFootprint(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(4, 1, 3);
        BlockPos diamond = new BlockPos(5, 1, 5);
        int initial = HarvesterTests.ore(helper, CENTRE, 0);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(diamond, Blocks.DIAMOND_ORE);
        feed(helper);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.DIAMOND), 1, "diamonds in the chest");
            helper.assertBlockPresent(Blocks.AIR, diamond);
            helper.assertBlockPresent(Blocks.DIRT, dirt);
            int drawn = initial - HarvesterTests.remaining(helper, CENTRE);
            helper.assertTrue(drawn >= 2, "the ore block was drawn " + drawn + " times, waiting for 2");
        });
    }

    /** A chest at the output face, and a hopper at the west side feeding the Harvester coal; then the Harvester. */
    private static void feed(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        BlockPos hopper = ORIGIN.relative(Direction.WEST, 2);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 8));
        HarvesterTests.place(helper, Direction.NORTH);
    }
}
