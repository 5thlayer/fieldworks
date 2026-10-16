// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Harvester over oil wells on a real server (#9): it pumps a well one cycle per operation into its product
 * tank, and the crude leaves by bucket, by the fluid capability, or pushed into the fluid handler at its
 * output face.
 */
final class HarvesterWellTests {

    private static final BlockPos CENTRE = ORIGIN.below();
    private static final BlockPos CHEST = ORIGIN.relative(Direction.NORTH, 2);

    /** Twice what yields the most a cycle can, 1,000 mB, so its every cycle in a test yields a bucket. */
    private static final long RICH = 60_000_000;

    private HarvesterWellTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_burner_harvester_over_a_well_pumps_crude_into_its_tank_as_the_well_decays_to_its_floor", 20,
                HarvesterWellTests::decaysToTheFloor);
        tests.test("a_burner_harvester_over_a_well_and_ore_pumps_only_the_well", 20, HarvesterWellTests::wellWinsOverOre);
        tests.test("a_burner_harvester_fills_empty_buckets_from_a_hopper_and_pushes_out_crude_oil_buckets", 300,
                HarvesterWellTests::fillsBuckets);
        tests.test("a_pipe_beside_a_burner_harvester_drains_its_crude_and_cannot_fill_its_tank", 20,
                HarvesterWellTests::pipeSideDrainOnly);
        tests.test("a_burner_harvester_pushes_crude_into_the_fluid_handler_at_its_output_face_and_pulls_nothing", 20,
                HarvesterWellTests::pushesCrude);
    }

    /**
     * A well 20 above its floor decays 10 a cycle, to the floor in two cycles and no lower, and yields 2 mB a
     * cycle there (ADR 0002). The well itself has no fluid capability: only the Harvester cycles it.
     */
    private static void decaysToTheFloor(GameTestHelper helper) {
        OilWellBlockEntity well = well(helper, CENTRE, 60_020);
        HarvesterBlockEntity machine = harvester(helper);
        BlockPos wellPos = helper.absolutePos(CENTRE);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, wellPos, null) == null,
                "the well has a fluid capability");
        for (Direction side : Direction.values()) {
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, wellPos, side) == null,
                    "the well has a fluid capability on its " + side + " face");
        }

        HarvesterTests.tick(helper, machine, 20);
        helper.assertValueEqual(well.amount(), 60_010L, "the well's amount after one cycle");
        helper.assertValueEqual(crude(helper), 2, "crude in the tank after one cycle");
        HarvesterTests.tick(helper, machine, 20);
        helper.assertValueEqual(well.amount(), 60_000L, "the well's amount after two cycles");
        HarvesterTests.tick(helper, machine, 60);
        helper.assertValueEqual(well.amount(), 60_000L, "the well's amount after five cycles, at its floor");
        helper.assertValueEqual(crude(helper), 10, "crude in the tank after five cycles");
        helper.succeed();
    }

    private static void wellWinsOverOre(GameTestHelper helper) {
        BlockPos corner = new BlockPos(3, 1, 3);
        BlockPos other = new BlockPos(5, 1, 5);
        int initial = HarvesterTests.ore(helper, CENTRE, 0);
        HarvesterTests.ore(helper, other, 0);
        well(helper, corner, RICH);
        helper.setBlock(CHEST, Blocks.CHEST);
        HarvesterBlockEntity machine = harvester(helper);

        HarvesterTests.tick(helper, machine, 100);

        helper.assertValueEqual(crude(helper), 5_000, "crude pumped in five operations");
        helper.assertValueEqual(HarvesterTests.remaining(helper, CENTRE), initial, "the centre ore block's amount");
        helper.assertValueEqual(HarvesterTests.remaining(helper, other), initial, "the corner ore block's amount");
        helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.RAW_IRON), 0, "raw iron in the chest");
        helper.succeed();
    }

    /** One hopper at the side, holding empty buckets; the filled ones reach the chest at the output face. */
    private static void fillsBuckets(GameTestHelper helper) {
        well(helper, CENTRE, RICH);
        helper.setBlock(CHEST, Blocks.CHEST);
        BlockPos hopper = ORIGIN.relative(Direction.WEST, 2);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        HopperBlockEntity feed = helper.getBlockEntity(hopper, HopperBlockEntity.class);
        feed.setItem(0, new ItemStack(Items.BUCKET, 3));
        harvester(helper);

        helper.succeedWhen(() -> {
            int filled = HarvesterTests.count(helper, CHEST, CrudeOil.BUCKET.get());
            helper.assertTrue(filled == 3, filled + " crude oil buckets in the chest, waiting for 3");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.BUCKET), 0, "empty buckets in the chest");
            helper.assertTrue(feed.isEmpty(), "the hopper still holds buckets");
            helper.assertItemEntityNotPresent(CrudeOil.BUCKET.get());
            helper.assertItemEntityNotPresent(Items.BUCKET);
        });
    }

    private static void pipeSideDrainOnly(GameTestHelper helper) {
        well(helper, CENTRE, RICH);
        HarvesterBlockEntity machine = harvester(helper);
        HarvesterTests.tick(helper, machine, 20);
        ResourceHandler<FluidResource> pipeSide = fluids(helper);
        helper.assertValueEqual(crude(helper), 1_000, "crude in the tank after one operation");

        try (Transaction filling = Transaction.openRoot()) {
            helper.assertValueEqual(pipeSide.insert(crudeOil(), 1_000, filling), 0, "crude a pipe put into the tank");
            helper.assertValueEqual(pipeSide.insert(FluidResource.of(Fluids.WATER), 1_000, filling), 0,
                    "water a pipe put into the Harvester");
            for (int index = 0; index < pipeSide.size(); index++) {
                helper.assertValueEqual(pipeSide.insert(index, crudeOil(), 1_000, filling), 0, "crude a pipe put into tank " + index);
            }
            filling.commit();
        }
        try (Transaction draining = Transaction.openRoot()) {
            helper.assertValueEqual(pipeSide.extract(crudeOil(), 1_000, draining), 1_000, "crude a pipe drained");
            draining.commit();
        }
        helper.assertValueEqual(crude(helper), 0, "crude left in the tank");
        helper.succeed();
    }

    /**
     * A tank at the output face holding water in its first tank: crude goes into its second, and the water
     * stays where it is, since the Harvester pulls nothing from its neighbour.
     */
    private static void pushesCrude(GameTestHelper helper) {
        well(helper, CENTRE, RICH);
        helper.setBlock(CHEST, TestTank.BLOCK.get());
        TestTank.Entity tank = helper.getBlockEntity(CHEST, TestTank.Entity.class);
        tank.tanks.set(0, FluidResource.of(Fluids.WATER), 1_000);
        HarvesterBlockEntity machine = harvester(helper);

        // Three operations, then a tick to push the last.
        HarvesterTests.tick(helper, machine, 61);

        helper.assertValueEqual(tank.tanks.getResource(1), crudeOil(), "the fluid in the neighbour's second tank");
        helper.assertValueEqual(tank.tanks.getAmountAsInt(1), 3_000, "crude pushed into the neighbour");
        helper.assertValueEqual(tank.tanks.getResource(0), FluidResource.of(Fluids.WATER), "the fluid in the neighbour's first tank");
        helper.assertValueEqual(tank.tanks.getAmountAsInt(0), 1_000, "water left in the neighbour");
        helper.assertValueEqual(crude(helper), 0, "crude left in the Harvester's tank");
        helper.succeed();
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static FluidResource crudeOil() {
        return FluidResource.of(CrudeOil.SOURCE.get());
    }

    private static OilWellBlockEntity well(GameTestHelper helper, BlockPos rel, long amount) {
        helper.setBlock(rel, FieldworksRegistries.OIL_WELL.get());
        OilWellBlockEntity well = helper.getBlockEntity(rel, OilWellBlockEntity.class);
        well.start(amount);
        return well;
    }

    /** A Burner Harvester facing north over the layer, fed a stack of coal through its item capability. */
    private static HarvesterBlockEntity harvester(GameTestHelper helper) {
        if (helper.getBlockState(CENTRE).isAir()) {
            helper.setBlock(CENTRE, Blocks.STONE);
        }
        HarvesterTests.place(helper, Direction.NORTH);
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            helper.assertValueEqual(items.insert(ItemResource.of(Items.COAL), 64, feeding), 64, "coal the Harvester took");
            feeding.commit();
        }
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    /** The fluid capability on a side of the footprint, where a pipe would meet it. */
    private static ResourceHandler<FluidResource> fluids(GameTestHelper helper) {
        ResourceHandler<FluidResource> fluids = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(ORIGIN.east()), Direction.EAST);
        helper.assertTrue(fluids != null, "the Harvester has no fluid capability");
        return fluids;
    }

    /** The crude in the Harvester's tanks, as a pipe at its side reads them. */
    private static int crude(GameTestHelper helper) {
        ResourceHandler<FluidResource> fluids = fluids(helper);
        int crude = 0;
        for (int index = 0; index < fluids.size(); index++) {
            if (fluids.getResource(index).equals(crudeOil())) {
                crude += fluids.getAmountAsInt(index);
            }
        }
        return crude;
    }
}
