// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import io.github._5thlayer.fieldworks.harvester.DrillHeadBlock;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlock;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.HarvesterTier;
import io.github._5thlayer.fieldworks.harvester.HarvesterTiers;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.groundworks.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Harvester's reach and its drill heads on a real server (#34, ADR 0008): each of the nine columns under the footprint
 * looks down through air, replaceable blocks and fluids to the first other block within reach, a drill head stands over
 * a source lower than the block directly under the Harvester, and the Harvester follows the world with no fuel in it.
 *
 * <p>The layer under the Harvester is {@code y = 1}, the layer below it {@code y = 0}, and the Burner Harvester's reach
 * is 1. A column the tests look at is the north-west one, over {@link #COLUMN}.
 */
final class HarvesterReachTests {

    /** The north-west column's block directly under the Harvester, and the one below it. */
    private static final BlockPos COLUMN = new BlockPos(3, 1, 3);
    private static final BlockPos BELOW = COLUMN.below();
    /** The Harvester's own bottom block over {@link #COLUMN}. */
    private static final BlockPos FOOT = new BlockPos(3, 2, 3);

    private HarvesterReachTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("harvester_reach_a_column_works_an_ore_block_one_below_the_layer", 100, HarvesterReachTests::worksBelowTheLayer);
        tests.test("harvester_reach_a_source_directly_under_makes_the_bottom_block_the_drill_head", 20, HarvesterReachTests::bottomBlockIsTheHead);
        tests.test("harvester_reach_a_column_with_nothing_within_reach_shows_no_drill_head", 20, HarvesterReachTests::nothingWithinReach);
        tests.test("harvester_reach_a_tier_with_no_reach_works_only_the_block_directly_under", 20, HarvesterReachTests::noReachWorksOnlyUnder);
        tests.test("harvester_reach_a_broken_harvestable_block_opens_its_column_to_the_ore_below", 20, HarvesterReachTests::openedColumn);
        tests.test("harvester_reach_an_opened_column_never_looks_past_reach", 20, HarvesterReachTests::openedPastReach);
        tests.test("harvester_reach_a_spent_ore_block_leaves_its_column_with_no_source", 20, HarvesterReachTests::spentColumn);
        tests.test("harvester_reach_a_drill_head_goes_when_its_source_does", 20, HarvesterReachTests::headGoesWithItsSource);
        tests.test("harvester_reach_a_drill_head_goes_with_its_harvester", 20, HarvesterReachTests::headGoesWithTheHarvester);
        tests.test("harvester_reach_a_drill_head_takes_a_replaceable_blocks_place_and_holds_its_cell", 20, HarvesterReachTests::replacesAndHolds);
        tests.test("harvester_reach_a_drill_head_waterlogs_in_a_water_source_and_leaves_the_water", 20, HarvesterReachTests::waterlogs);
        tests.test("harvester_reach_a_drill_head_destroys_lava", 20, HarvesterReachTests::destroysLava);
        tests.test("harvester_reach_breaking_a_drill_head_dismantles_the_harvester", 20, HarvesterReachTests::dismantles);
    }

    // -- the tests ------------------------------------------------------------------------------

    private static void worksBelowTheLayer(GameTestHelper helper) {
        int initial = HarvesterTests.ore(helper, BELOW, 0);
        HarvesterBlockEntity machine = standing(helper);
        feed(helper);

        helper.assertTrue(isHead(helper, COLUMN), "no drill head over the ore block a block below the layer");
        HarvesterTests.tick(helper, machine, 2_000);
        helper.assertTrue(HarvesterTests.remaining(helper, BELOW) < initial, "the column's ore block was not drawn");
        helper.succeed();
    }

    private static void bottomBlockIsTheHead(GameTestHelper helper) {
        HarvesterTests.ore(helper, COLUMN, 0);
        HarvesterTests.ore(helper, ORIGIN.below(), 0);
        standing(helper);

        helper.assertTrue(drill(helper, FOOT), "the part over an ore block directly under is no drill head");
        helper.assertTrue(drill(helper, ORIGIN), "the origin over an ore block directly under is no drill head");
        helper.assertFalse(drill(helper, ORIGIN.east()), "a part over nothing is a drill head");
        helper.assertFalse(isHead(helper, COLUMN), "a drill head block stands where the bottom block is the head");
        helper.succeed();
    }

    private static void nothingWithinReach(GameTestHelper helper) {
        helper.setBlock(COLUMN, Blocks.STONE);
        standing(helper);

        helper.assertFalse(drill(helper, FOOT), "a column over stone shows a drill head");
        helper.assertFalse(isHead(helper, COLUMN.above()), "a drill head stands over stone");
        helper.succeed();
    }

    private static void noReachWorksOnlyUnder(GameTestHelper helper) {
        HarvesterTests.ore(helper, BELOW, 0);
        try (HarvesterTiers.Swap swap = noReach()) {
            standing(helper);

            helper.assertFalse(isHead(helper, COLUMN), "a tier with no reach put a drill head over a source a block down");
            helper.assertFalse(drill(helper, FOOT), "a tier with no reach shows a drill head");
        }
        helper.succeed();
    }

    private static void openedColumn(GameTestHelper helper) {
        helper.setBlock(COLUMN, Blocks.IRON_ORE);
        HarvesterTests.ore(helper, BELOW, 0);
        HarvesterBlockEntity machine = standing(helper);
        helper.assertTrue(drill(helper, FOOT), "the harvestable block directly under was not the source");

        helper.setBlock(COLUMN, Blocks.AIR);
        HarvesterTests.tick(helper, machine, 1);

        helper.assertFalse(drill(helper, FOOT), "the bottom block is still the drill head after its source was broken");
        helper.assertTrue(isHead(helper, COLUMN), "the opened column put no drill head over the ore block below");
        helper.succeed();
    }

    private static void openedPastReach(GameTestHelper helper) {
        helper.setBlock(COLUMN, Blocks.IRON_ORE);
        HarvesterTests.ore(helper, BELOW, 0);
        try (HarvesterTiers.Swap swap = noReach()) {
            HarvesterBlockEntity machine = standing(helper);
            helper.assertTrue(drill(helper, FOOT), "the harvestable block directly under was not the source");

            helper.setBlock(COLUMN, Blocks.AIR);
            HarvesterTests.tick(helper, machine, 1);

            helper.assertFalse(drill(helper, FOOT), "the bottom block is still the drill head after its source was broken");
            helper.assertFalse(isHead(helper, COLUMN), "an opened column looked past its reach");
        }
        helper.succeed();
    }

    private static void spentColumn(GameTestHelper helper) {
        HarvesterTests.ore(helper, COLUMN, 0);
        HarvesterTests.ore(helper, BELOW, 0);
        HarvesterBlockEntity machine = standing(helper);
        helper.assertTrue(drill(helper, FOOT), "the ore block directly under was not the source");

        // A spent ore block is stone, which a column does not pass, so the ore below it is out of sight.
        helper.setBlock(COLUMN, Blocks.STONE);
        HarvesterTests.tick(helper, machine, 1);

        helper.assertFalse(drill(helper, FOOT), "the bottom block is still the drill head over spent stone");
        helper.assertFalse(isHead(helper, COLUMN), "a spent column put a drill head over the ore below the stone");
        helper.succeed();
    }

    private static void headGoesWithItsSource(GameTestHelper helper) {
        HarvesterTests.ore(helper, BELOW, 0);
        HarvesterBlockEntity machine = standing(helper);
        helper.assertTrue(isHead(helper, COLUMN), "no drill head over the ore block");

        helper.setBlock(BELOW, Blocks.STONE);
        HarvesterTests.tick(helper, machine, 1);

        helper.assertBlockPresent(Blocks.AIR, COLUMN);
        helper.succeed();
    }

    private static void headGoesWithTheHarvester(GameTestHelper helper) {
        HarvesterTests.ore(helper, BELOW, 0);
        FakePlayer player = place(helper);
        helper.assertTrue(isHead(helper, COLUMN), "no drill head over the ore block");

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertBlockPresent(Blocks.AIR, COLUMN);
        helper.assertBlockPresent(Blocks.AIR, FOOT);
        helper.succeed();
    }

    private static void replacesAndHolds(GameTestHelper helper) {
        HarvesterTests.ore(helper, BELOW, 0);
        helper.setBlock(COLUMN, Blocks.SHORT_GRASS);
        standing(helper);

        BlockState head = helper.getBlockState(COLUMN);
        helper.assertTrue(head.getBlock() instanceof DrillHeadBlock, "the head did not take the grass's place: " + head);
        helper.assertFalse(head.canBeReplaced(), "a drill head gives up its cell");
        helper.assertTrue(head.getCollisionShape(helper.getLevel(), helper.absolutePos(COLUMN), CollisionContext.empty()).isEmpty(),
                "a drill head collides");
        helper.assertFalse(head.getShape(helper.getLevel(), helper.absolutePos(COLUMN)).isEmpty(), "a drill head has no outline");
        helper.succeed();
    }

    private static void waterlogs(GameTestHelper helper) {
        HarvesterTests.ore(helper, BELOW, 0);
        helper.setBlock(COLUMN, Blocks.WATER);
        HarvesterBlockEntity machine = standing(helper);

        BlockState head = helper.getBlockState(COLUMN);
        helper.assertTrue(head.getBlock() instanceof DrillHeadBlock && head.getValue(BlockStateProperties.WATERLOGGED), "the head is not waterlogged: " + head);
        helper.assertTrue(head.getFluidState().is(Fluids.WATER) && head.getFluidState().isSource(), "a waterlogged head holds no water source");

        helper.setBlock(BELOW, Blocks.STONE);
        HarvesterTests.tick(helper, machine, 1);

        helper.assertBlockPresent(Blocks.WATER, COLUMN);
        helper.succeed();
    }

    private static void destroysLava(GameTestHelper helper) {
        HarvesterTests.ore(helper, BELOW, 0);
        helper.setBlock(COLUMN, Blocks.LAVA);
        standing(helper);

        BlockState head = helper.getBlockState(COLUMN);
        helper.assertTrue(head.getBlock() instanceof DrillHeadBlock, "the head did not take the lava's place: " + head);
        helper.assertFalse(head.getValue(BlockStateProperties.WATERLOGGED), "the head is waterlogged in lava");
        helper.assertTrue(head.getFluidState().isEmpty(), "the lava survived in the head's cell");
        helper.succeed();
    }

    private static void dismantles(GameTestHelper helper) {
        HarvesterTests.ore(helper, BELOW, 0);
        FakePlayer player = place(helper);
        helper.assertTrue(isHead(helper, COLUMN), "no drill head over the ore block");

        player.gameMode.destroyBlock(helper.absolutePos(COLUMN));

        Direction facing = Direction.NORTH;
        for (BlockPos pos : footprint().positions(helper.absolutePos(ORIGIN), facing)) {
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "the break left " + helper.getLevel().getBlockState(pos) + " at " + pos);
        }
        helper.assertBlockPresent(Blocks.AIR, COLUMN);
        Item item = Harvesters.item(TierBlock.HARVESTER_1).get();
        int dropped = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(dropped == 1, "the break dropped " + dropped + " Harvesters, not 1");
        helper.succeed();
    }

    // -- fixtures -------------------------------------------------------------------------------

    /**
     * Clears the layer under the Harvester and puts stone under its middle and the layer below, then stands a Burner
     * Harvester over it, whatever the test has put there: {@link #COLUMN} and {@link #BELOW} are its own to set. The
     * Harvester ticks once, as the world would, so its drill heads stand.
     */
    private static HarvesterBlockEntity standing(GameTestHelper helper) {
        place(helper);
        HarvesterBlockEntity machine = helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
        HarvesterTests.tick(helper, machine, 1);
        return machine;
    }

    private static FakePlayer place(GameTestHelper helper) {
        if (helper.getBlockState(ORIGIN.below()).isAir()) {
            helper.setBlock(ORIGIN.below(), Blocks.STONE);
        }
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH);
        HarvesterTests.tick(helper, helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class), 1);
        return player;
    }

    /** Coal in the Harvester, through the item capability of a block on its edge, as a hopper would. */
    private static void feed(GameTestHelper helper) {
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            items.insert(ItemResource.of(net.minecraft.world.item.Items.COAL), 64, feeding);
            feeding.commit();
        }
    }

    /** The Burner Harvester's tier with no reach, until the result is closed. */
    private static HarvesterTiers.Swap noReach() {
        return HarvesterTiers.swapped(TierBlock.HARVESTER_1, new HarvesterTier(1, 1.0, new HarvesterTier.Power(true, 0, 200), 0, 0));
    }

    private static boolean isHead(GameTestHelper helper, BlockPos rel) {
        return helper.getBlockState(rel).getBlock() instanceof DrillHeadBlock;
    }

    private static boolean drill(GameTestHelper helper, BlockPos rel) {
        return helper.getBlockState(rel).getValue(HarvesterBlock.DRILL);
    }

    private static Footprint footprint() {
        return Harvesters.footprint(TierBlock.HARVESTER_1);
    }
}
