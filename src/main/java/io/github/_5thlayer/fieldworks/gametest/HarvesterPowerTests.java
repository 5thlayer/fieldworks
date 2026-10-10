// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.HarvesterTier;
import io.github._5thlayer.fieldworks.harvester.HarvesterTiers;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
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

    private HarvesterPowerTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("an_electric_harvester_fed_fe_draws_ore_twice_as_fast_as_a_burner_harvester", 20,
                HarvesterPowerTests::twiceAsFast);
        tests.test("a_tier_that_takes_both_kinds_pays_fe_first_falls_back_to_fuel_and_finishes_a_started_burn", 20,
                HarvesterPowerTests::bothKinds);
        tests.test("a_burner_harvester_exposes_no_energy_capability", 20, HarvesterPowerTests::burnOnlyHasNoEnergy);
        tests.test("an_electric_harvester_takes_no_fuel_from_a_hopper", 60, HarvesterPowerTests::feOnlyTakesNoFuel);
    }

    /** 200 ticks of work: ten 20-tick draws at speed x1, twenty at the Electric Harvester's x2. */
    private static void twiceAsFast(GameTestHelper helper) {
        BlockPos ore = ORIGIN.below();
        int initial = HarvesterTests.ore(helper, OreResource.IRON, ore, 0);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insert(helper, Items.COAL, 1);
        HarvesterTests.tick(helper, machine(helper), 200);
        int burnerDraws = initial - HarvesterTests.remaining(helper, ore);
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        helper.assertValueEqual(feed(helper, 50_000), 50_000, "FE the Electric Harvester took");
        HarvesterTests.tick(helper, machine(helper), 200);
        int electricDraws = initial - burnerDraws - HarvesterTests.remaining(helper, ore);

        helper.assertValueEqual(burnerDraws, 10, "units the Burner Harvester drew in 200 ticks");
        helper.assertValueEqual(electricDraws, 20, "units the Electric Harvester drew in 200 ticks");
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
            HarvesterTests.tick(helper, machine, burnTime - 10);
            helper.assertValueEqual((int) energy(helper).getAmountAsLong(), 400, "FE spent while the coal still burned");
            HarvesterTests.tick(helper, machine, 1);
            helper.assertValueEqual((int) energy(helper).getAmountAsLong(), 360, "FE once the burn ran out");
            helper.assertValueEqual(initial - HarvesterTests.remaining(helper, ore), (burnTime + 11) / 20,
                    "units drawn, one per 20 ticks of work whatever paid for them");
        }
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

    // -- fixtures -------------------------------------------------------------------------------

    private static HarvesterBlockEntity machine(GameTestHelper helper) {
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    /** Through a block on the footprint's edge, as a hopper or a cable there would. */
    private static int insert(GameTestHelper helper, net.minecraft.world.item.Item item, int count) {
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
