// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import io.github._5thlayer.fieldworks.harvester.HarvesterBlock;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Harvester lit while it works (#42): its origin carries {@code LIT} while an operation progresses, goes unlit on
 * any idle reason, and gives off block light 13 while lit. Ticks are run by hand, so the counts are exact.
 */
final class HarvesterLitTests {

    /** An ore draw's ticks at speed x1. */
    private static final int OPERATION_TICKS = 20;

    private HarvesterLitTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("harvester_lit_while_a_burner_harvester_draws_and_unlit_once_its_fuel_runs_out", 20, HarvesterLitTests::burnerWorks);
        tests.test("harvester_lit_stays_off_with_no_power", 20, HarvesterLitTests::noPower);
        tests.test("harvester_lit_goes_unlit_when_its_output_is_full", 20, HarvesterLitTests::outputFull);
        tests.test("harvester_lit_while_an_electric_harvester_works_on_fe", 20, HarvesterLitTests::electricWorks);
        tests.test("harvester_lit_gives_off_light_13_and_unlit_none", 20, HarvesterLitTests::light);
    }

    private static void burnerWorks(GameTestHelper helper) {
        HarvesterTests.ore(helper, ORIGIN.below(), 0);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterBlockEntity machine = machine(helper);
        helper.assertFalse(lit(helper), "a fresh Harvester was lit");
        insertCoal(helper, 1);

        HarvesterTests.tick(helper, machine, 1);
        helper.assertTrue(lit(helper), "the Burner Harvester was not lit on its first working tick");
        // One coal buys eight 20-tick draws.
        HarvesterTests.tick(helper, machine, 8 * OPERATION_TICKS - 1);
        helper.assertTrue(lit(helper), "the Burner Harvester went dark before its coal ran out");
        HarvesterTests.tick(helper, machine, 1);
        helper.assertFalse(lit(helper), "the Burner Harvester stayed lit with no fuel left");
        helper.succeed();
    }

    private static void noPower(GameTestHelper helper) {
        HarvesterTests.ore(helper, ORIGIN.below(), 0);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterTests.tick(helper, machine(helper), 5);
        helper.assertFalse(lit(helper), "a Burner Harvester with no fuel was lit");
        helper.succeed();
    }

    /** A full chest at the output face: the Harvester draws until its own buffer is full too, then goes dark. */
    private static void outputFull(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(3, 1, 3, 5, 1, 5)) {
            HarvesterTests.ore(helper, pos.immutable(), 0);
        }
        BlockPos chest = ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);
        Container full = helper.getBlockEntity(chest, ChestBlockEntity.class);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insertCoal(helper, 64);
        HarvesterBlockEntity machine = machine(helper);

        HarvesterTests.tick(helper, machine, 5);
        helper.assertTrue(lit(helper), "the Harvester was not lit while its buffer had room");
        HarvesterTests.tick(helper, machine, 15_000);
        helper.assertFalse(lit(helper), "the Harvester stayed lit with its output full");
        helper.succeed();
    }

    private static void electricWorks(GameTestHelper helper) {
        HarvesterTests.ore(helper, ORIGIN.below(), 0);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        HarvesterBlockEntity machine = machine(helper);
        helper.assertFalse(lit(helper), "an Electric Harvester with no FE was lit");
        HarvesterTests.tick(helper, machine, 1);
        helper.assertFalse(lit(helper), "an Electric Harvester with no FE was lit after a tick");

        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(energy != null, "the Electric Harvester has no energy capability");
        // 40 FE a tick: ten ticks' worth.
        EnergyHandlerUtil.move(new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000, 1_000_000), energy, 400, null);
        HarvesterTests.tick(helper, machine, 10);
        helper.assertTrue(lit(helper), "the Electric Harvester was not lit while FE paid for its work");
        HarvesterTests.tick(helper, machine, 1);
        helper.assertFalse(lit(helper), "the Electric Harvester stayed lit once its FE ran out");
        helper.succeed();
    }

    private static void light(GameTestHelper helper) {
        helper.setBlock(ORIGIN.below(), Blocks.STONE);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        BlockPos origin = helper.absolutePos(ORIGIN);
        var level = helper.getLevel();
        helper.assertValueEqual(level.getBlockState(origin).getLightEmission(), 0, "light of an unlit Harvester");
        level.setBlock(origin, level.getBlockState(origin).setValue(HarvesterBlock.LIT, true), 3);
        helper.assertValueEqual(level.getBlockState(origin).getLightEmission(), 13, "light of a lit Harvester");
        helper.succeed();
    }

    private static boolean lit(GameTestHelper helper) {
        return helper.getBlockState(ORIGIN).getValue(HarvesterBlock.LIT);
    }

    private static HarvesterBlockEntity machine(GameTestHelper helper) {
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    /** Through a block on the footprint's edge, as a hopper there would. */
    private static void insertCoal(GameTestHelper helper, int count) {
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            helper.assertValueEqual(items.insert(ItemResource.of(Items.COAL), count, feeding), count, "coal the Harvester took");
            feeding.commit();
        }
    }
}
