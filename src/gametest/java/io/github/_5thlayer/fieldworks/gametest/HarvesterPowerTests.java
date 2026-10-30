// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.github._5thlayer.fieldworks.harvester.HarvesterTier;
import io.github._5thlayer.fieldworks.harvester.HarvesterTiers;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester's power as its tier gives it (#7, ADR 0005): the Electric Harvester on FE, a tier that takes both
 * kinds paying FE first with fuel as backup, and each tier exposing only the power it declares. Ticks are run by
 * hand, so the counts are exact.
 */
final class HarvesterPowerTests {

    /** The game tests' datapack's tier that takes FE and fuel, with a buffer of ten ticks' FE. */
    private static final Identifier BOTH_KINDS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/both_kinds");

    /** An ore draw's ticks at speed x1. */
    private static final int OPERATION_TICKS = 20;

    private static final int BUFFER_SLOTS = HarvesterMenu.FIRST_PLAYER_SLOT - HarvesterMenu.BUFFER_FIRST;

    private HarvesterPowerTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("an_electric_harvester_fed_fe_draws_ore_twice_as_fast_as_a_burner_harvester", 20,
                HarvesterPowerTests::twiceAsFast);
        tests.test("a_tier_that_takes_both_kinds_pays_fe_first_falls_back_to_fuel_and_finishes_a_started_burn", 20,
                HarvesterPowerTests::bothKinds);
        tests.test("one_coal_buys_a_burner_harvester_eight_operations_whether_it_draws_ore_or_breaks_harvestable_blocks", 20,
                HarvesterPowerTests::coalBuysEightOperations);
        tests.test("a_burner_harvester_exposes_no_energy_capability", 20, HarvesterPowerTests::burnOnlyHasNoEnergy);
        tests.test("an_electric_harvester_takes_no_fuel_from_a_hopper", 60, HarvesterPowerTests::feOnlyTakesNoFuel);
        tests.test("a_harvester_lighting_a_lava_bucket_as_it_draws_waits_for_room_for_the_bucket_and_the_draw", 20,
                HarvesterPowerTests::roomForTheBucketAndTheDraw);
    }

    /** 200 ticks of work: ten 20-tick draws at speed x1, twenty at the Electric Harvester's x2. */
    private static void twiceAsFast(GameTestHelper helper) {
        BlockPos ore = ORIGIN.below();
        int initial = HarvesterTests.ore(helper, OreResource.IRON, ore, 0);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insert(helper, Items.COAL, 2);
        HarvesterTests.tick(helper, machine(helper), 200);
        int harvester1Draws = initial - HarvesterTests.remaining(helper, ore);
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        helper.assertValueEqual(feed(helper, 50_000), 50_000, "FE the Electric Harvester took");
        HarvesterTests.tick(helper, machine(helper), 200);
        int harvester2Draws = initial - harvester1Draws - HarvesterTests.remaining(helper, ore);

        helper.assertValueEqual(harvester1Draws, 10, "units harvester_1 drew in 200 ticks");
        helper.assertValueEqual(harvester2Draws, 20, "units harvester_2 drew in 200 ticks");
        helper.assertValueEqual((int) energy(helper).getAmountAsLong(), 50_000 - 200 * 40, "FE left after 200 ticks at 40 FE a tick");
        helper.succeed();
    }

    /**
     * The Electric Harvester re-powered by the game tests' datapack tier that takes both kinds: ten ticks of FE,
     * then the coal it kept is lit; FE fed back mid-burn waits until the burn has run out.
     */
    private static void bothKinds(GameTestHelper helper) {
        HarvesterTier both = HarvesterTiers.get(BOTH_KINDS);
        helper.assertTrue(both != null, "the game tests' datapack tier " + BOTH_KINDS + " did not load");
        int burnTime = new ItemStack(Items.COAL).getBurnTime(null, helper.getLevel().fuelValues());
        int burnWork = burnTime * OPERATION_TICKS / both.power().burnPerOperation();
        BlockPos ore = ORIGIN.below();
        int initial = HarvesterTests.ore(helper, OreResource.IRON, ore, 0);

        try (var swap = HarvesterTiers.swapped(TierBlock.HARVESTER_2, both)) {
            HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
            HarvesterBlockEntity machine = machine(helper);
            helper.assertValueEqual(insert(helper, Items.COAL, 1), 1, "coal the Harvester took");
            helper.assertValueEqual(feed(helper, 1_000), 400, "FE the Harvester took into its buffer of 400");

            HarvesterTests.tick(helper, machine, 10);
            helper.assertValueEqual((int) energy(helper).getAmountAsLong(), 0, "FE left after ten ticks at 40 FE a tick");
            helper.assertValueEqual(coal(helper), 1, "coal kept while FE paid");

            HarvesterTests.tick(helper, machine, 10);
            helper.assertValueEqual(coal(helper), 0, "coal left once FE ran out");
            helper.assertValueEqual(initial - HarvesterTests.remaining(helper, ore), 1, "units drawn in 20 ticks of work");

            helper.assertValueEqual(feed(helper, 400), 400, "FE fed back mid-burn");
            HarvesterTests.tick(helper, machine, burnWork - 10);
            helper.assertValueEqual((int) energy(helper).getAmountAsLong(), 400, "FE spent while the coal still burned");
            HarvesterTests.tick(helper, machine, 1);
            helper.assertValueEqual((int) energy(helper).getAmountAsLong(), 360, "FE once the burn ran out");
            helper.assertValueEqual(initial - HarvesterTests.remaining(helper, ore), (burnWork + 11) / OPERATION_TICKS,
                    "units drawn, one per 20 ticks of work whatever paid for them");
        }
        helper.succeed();
    }

    /** One coal, as it buys a furnace eight smelts: eight 20-tick ore draws, and eight 40-tick breaks of nine blocks. */
    private static void coalBuysEightOperations(GameTestHelper helper) {
        BlockPos centre = ORIGIN.below();
        int initial = HarvesterTests.ore(helper, OreResource.IRON, centre, 0);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insert(helper, Items.COAL, 1);
        HarvesterTests.tick(helper, machine(helper), 10 * OPERATION_TICKS);
        helper.assertValueEqual(initial - HarvesterTests.remaining(helper, centre), 8, "ore draws one coal bought");
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        for (BlockPos rel : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            helper.setBlock(rel, Blocks.IRON_ORE);
        }
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insert(helper, Items.COAL, 1);
        HarvesterTests.tick(helper, machine(helper), 10 * 2 * OPERATION_TICKS);
        int left = 0;
        for (BlockPos rel : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            if (helper.getBlockState(rel).is(Blocks.IRON_ORE)) {
                left++;
            }
        }
        helper.assertValueEqual(9 - left, 8, "harvestable blocks one coal broke");
        helper.succeed();
    }

    private static void burnOnlyHasNoEnergy(GameTestHelper helper) {
        helper.setBlock(ORIGIN.below(), Blocks.STONE);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        for (BlockPos rel : new BlockPos[] {ORIGIN, ORIGIN.west(), ORIGIN.above(2).south().east()}) {
            helper.assertTrue(energy(helper, rel, null) == null, "the Burner Harvester has an energy capability at " + rel);
            for (Direction side : Direction.values()) {
                helper.assertTrue(energy(helper, rel, side) == null,
                        "the Burner Harvester has an energy capability at " + rel + " on its " + side + " face");
            }
        }
        helper.succeed();
    }

    /** A hopper of coal against the Electric Harvester's side keeps its coal, while FE goes in. */
    private static void feOnlyTakesNoFuel(GameTestHelper helper) {
        BlockPos hopper = ORIGIN.relative(Direction.WEST, 2);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 4));
        helper.setBlock(ORIGIN.below(), Blocks.STONE);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);

        helper.assertValueEqual(insert(helper, Items.COAL, 1), 0, "coal the Electric Harvester took");
        helper.assertValueEqual(feed(helper, 40), 40, "FE the Electric Harvester took");
        helper.startSequence().thenIdle(40).thenExecute(() -> helper.assertValueEqual(
                helper.getBlockEntity(hopper, HopperBlockEntity.class).getItem(0).getCount(), 4, "coal left in the hopper"))
                .thenSucceed();
    }

    /**
     * A Burner Harvester fast enough that its first tick both lights a lava bucket and completes a draw, over a buffer
     * with one slot free: the bucket and the raw iron need a slot each, so it waits rather than lose either (ADR 0004).
     * With a second slot freed, it lights and draws, and both are in the buffer.
     */
    private static void roomForTheBucketAndTheDraw(GameTestHelper helper) {
        HarvesterTier burner = HarvesterTiers.get(TierBlock.HARVESTER_1);
        helper.assertTrue(burner != null, "the Burner Harvester's tier did not load");
        HarvesterTier oneTick = new HarvesterTier(burner.rank(), OPERATION_TICKS, burner.power(), burner.feBuffer(), burner.reach());
        BlockPos ore = ORIGIN.below();
        int initial = HarvesterTests.ore(helper, OreResource.IRON, ore, 0);

        try (var swap = HarvesterTiers.swapped(TierBlock.HARVESTER_1, oneTick)) {
            FakePlayer player = HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
            HarvesterBlockEntity machine = machine(helper);
            HarvesterMenu menu = (HarvesterMenu) machine.createMenu(1, player.getInventory(), player);
            menu.slots.get(HarvesterMenu.FUEL_SLOT).set(new ItemStack(Items.LAVA_BUCKET));
            for (int index = 1; index < BUFFER_SLOTS; index++) {
                menu.slots.get(HarvesterMenu.BUFFER_FIRST + index).set(new ItemStack(Items.COBBLESTONE, 64));
            }

            HarvesterTests.tick(helper, machine, 1);
            helper.assertValueEqual(initial - HarvesterTests.remaining(helper, ore), 0, "units drawn with room for one of two");
            helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.OUTPUT_FULL), "the idle reason with room for one of two");
            helper.assertTrue(menu.slots.get(HarvesterMenu.FUEL_SLOT).getItem().is(Items.LAVA_BUCKET), "the lava bucket was lit");
            helper.assertTrue(menu.slots.get(HarvesterMenu.BUFFER_FIRST).getItem().isEmpty(), "the free slot was filled");

            menu.slots.get(HarvesterMenu.BUFFER_FIRST + 1).set(ItemStack.EMPTY);
            HarvesterTests.tick(helper, machine, 1);
            helper.assertValueEqual(initial - HarvesterTests.remaining(helper, ore), 1, "units drawn with room for both");
            helper.assertTrue(menu.slots.get(HarvesterMenu.FUEL_SLOT).getItem().isEmpty(), "the lava bucket was not lit");
            helper.assertValueEqual(buffered(menu, Items.BUCKET), 1, "buckets in the buffer");
            helper.assertTrue(buffered(menu, Items.RAW_IRON) > 0, "the draw's raw iron is not in the buffer");
        }
        helper.succeed();
    }

    private static int buffered(HarvesterMenu menu, Item item) {
        int count = 0;
        for (int index = 0; index < BUFFER_SLOTS; index++) {
            ItemStack stack = menu.slots.get(HarvesterMenu.BUFFER_FIRST + index).getItem();
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static HarvesterBlockEntity machine(GameTestHelper helper) {
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    /** Through a block on the footprint's edge, as a hopper or a cable there would. */
    private static int insert(GameTestHelper helper, Item item, int count) {
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            int taken = items.insert(ItemResource.of(item), count, feeding);
            feeding.commit();
            return taken;
        }
    }

    private static int coal(GameTestHelper helper) {
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        int coal = 0;
        for (int index = 0; index < items.size(); index++) {
            if (items.getResource(index).is(Items.COAL)) {
                coal += items.getAmountAsInt(index);
            }
        }
        return coal;
    }

    /** Moves FE in from a source that never runs dry, and returns how much the Harvester took. */
    private static int feed(GameTestHelper helper, int amount) {
        SimpleEnergyHandler supply = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000, 1_000_000);
        return EnergyHandlerUtil.move(supply, energy(helper), amount, null);
    }

    private static EnergyHandler energy(GameTestHelper helper) {
        EnergyHandler energy = energy(helper, ORIGIN.west(), Direction.WEST);
        helper.assertTrue(energy != null, "the Harvester has no energy capability");
        return energy;
    }

    private static @Nullable EnergyHandler energy(GameTestHelper helper, BlockPos rel, @Nullable Direction side) {
        return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(rel), side);
    }
}
