// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatus;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatusLines;
import io.github._5thlayer.fieldworks.harvester.HarvesterTier;
import io.github._5thlayer.fieldworks.harvester.HarvesterTiers;
import io.github._5thlayer.fieldworks.harvester.HeldRecipeView;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.network.ClearHeldRecipePacket;
import io.github._5thlayer.fieldworks.network.HeldRecipeSyncPacket;
import io.github._5thlayer.fieldworks.network.HoldRecipePacket;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * What a Harvester shows of itself (#13), through the menu's data and the status the Jade tooltip reads, which are
 * one and the same record: power only for the kinds its tier declares, both tanks, progress, the Held recipe and the
 * idle reason the choice returns. Also the idle reasons for running out of power and for a full buffer or tank, and the
 * packet that clears the Held recipe. The screen and the tooltip only draw what these return.
 */
final class HarvesterMenuTests {

    private static final BlockPos CENTRE = ORIGIN.below();
    private static final BlockPos CHEST = ORIGIN.relative(Direction.NORTH, 2);
    private static final Identifier IRON_NUGGETS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/iron_nuggets");
    private static final Identifier MUDDY_IRON = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/muddy_iron");
    private static final Identifier BOTH_KINDS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/both_kinds");

    private HarvesterMenuTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("the_menu_shows_power_only_for_the_kinds_the_tier_declares", 20, HarvesterMenuTests::powerKinds);
        tests.test("the_menu_shows_burn_fe_and_progress_as_the_harvester_works", 20, HarvesterMenuTests::burnFeAndProgress);
        tests.test("the_menu_shows_the_drilling_fluid_tank_and_the_product_tank", 20, HarvesterMenuTests::bothTanks);
        tests.test("the_menu_and_the_status_show_the_idle_reason_the_harvester_returns", 20, HarvesterMenuTests::idleReasonIsTheChoices);
        tests.test("the_menu_shows_the_held_recipe_and_its_clear_packet_returns_the_harvester_to_its_default_recipes", 300,
                HarvesterMenuTests::clearsTheHeldRecipe);
        tests.test("the_clear_packet_changes_nothing_with_no_harvester_open", 20, HarvesterMenuTests::clearNeedsAnOpenHarvester);
        tests.test("the_menu_has_a_fuel_a_fill_and_a_drain_slot_that_take_only_theirs_and_shows_the_output_buffer", 100,
                HarvesterMenuTests::slots);
        tests.test("a_harvester_with_no_power_idles_with_no_power_and_again_when_its_fuel_runs_out", 3_000, HarvesterMenuTests::noPower);
        tests.test("a_harvester_with_a_full_output_buffer_idles_with_output_full", 20, HarvesterMenuTests::outputFull);
        tests.test("a_harvester_over_a_well_with_a_full_product_tank_idles_with_output_full", 20, HarvesterMenuTests::productTankFull);
        tests.test("the_status_the_jade_tooltip_reads_crosses_the_wire_and_the_tooltip_names_the_idle_reason", 20,
                HarvesterMenuTests::statusCrossesTheWire);
    }

    /** A burner takes fuel and no FE, an electric one FE and no fuel, a tier of both kinds both. */
    private static void powerKinds(GameTestHelper helper) {
        helper.setBlock(CENTRE, Blocks.STONE);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterMenu burner = menu(helper);
        helper.assertTrue(burner.takesFuel(), "the Burner Harvester's menu shows no fuel");
        helper.assertFalse(burner.takesEnergy(), "the Burner Harvester's menu shows FE");
        helper.assertValueEqual(burner.energyCapacity(), 0, "the Burner Harvester's FE capacity");
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        HarvesterMenu electric = menu(helper);
        helper.assertFalse(electric.takesFuel(), "the Electric Harvester's menu shows fuel");
        helper.assertTrue(electric.takesEnergy(), "the Electric Harvester's menu shows no FE");
        helper.assertValueEqual(electric.energyCapacity(), 50_000, "the Electric Harvester's FE capacity");
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        HarvesterTier both = HarvesterTiers.get(BOTH_KINDS);
        helper.assertTrue(both != null, "the game tests' datapack tier " + BOTH_KINDS + " did not load");
        try (var swap = HarvesterTiers.swapped(TierBlock.HARVESTER_2, both)) {
            HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
            HarvesterMenu menu = menu(helper);
            helper.assertTrue(menu.takesFuel() && menu.takesEnergy(), "a tier of both kinds does not show both");
            helper.assertValueEqual(menu.energyCapacity(), 400, "the both-kinds tier's FE capacity");
        }
        helper.succeed();
    }

    /** Ten ticks into a 20-tick draw on coal: halfway, with the coal lit and half an operation's burn spent. */
    private static void burnFeAndProgress(GameTestHelper helper) {
        HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insertItem(helper, Items.COAL, 1);
        HarvesterBlockEntity machine = machine(helper);
        HarvesterMenu menu = menu(helper);
        int burnTime = new ItemStack(Items.COAL).getBurnTime(null, helper.getLevel().fuelValues());
        int perOperation = HarvesterTiers.get(TierBlock.HARVESTER_1).power().burnPerOperation();

        HarvesterTests.tick(helper, machine, 10);

        helper.assertValueEqual(menu.progress(), 10, "ticks of progress");
        helper.assertValueEqual(menu.operationTicks(), 20, "ticks the operation takes");
        helper.assertValueEqual(menu.progressFraction(), 0.5f, "progress as a fraction");
        helper.assertValueEqual(menu.burnTotal(), burnTime, "the coal's burn time");
        helper.assertValueEqual(menu.burnLeft(), burnTime - perOperation / 2, "the burn left");
        helper.assertValueEqual(menu.status().burnOperations(), (burnTime - perOperation / 2) / (float) perOperation,
                "the operations the burn has left");
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        feed(helper, 1_000);
        HarvesterTests.tick(helper, machine(helper), 5);
        HarvesterMenu electric = menu(helper);
        helper.assertValueEqual(electric.energy(), 1_000 - 5 * 40, "FE in the buffer after five ticks at 40 FE");
        helper.assertValueEqual(electric.operationTicks(), 10, "ticks an operation takes at speed x2");
        helper.assertValueEqual(electric.progress(), 5, "ticks of progress at speed x2");
        helper.succeed();
    }

    private static void bothTanks(GameTestHelper helper) {
        OilWellBlockEntity well = well(helper, 60_000_000);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterTests.holdingTheWellsCycle(helper);
        insertItem(helper, Items.COAL, 4);
        HarvesterMenu menu = menu(helper);
        helper.assertTrue(menu.drillingFluid().isEmpty() && menu.product().isEmpty(), "a new Harvester's menu shows a tank holding something");
        helper.assertValueEqual(menu.tankCapacity(), 8_000, "a tank's capacity");

        ResourceHandler<FluidResource> pipe = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(ORIGIN.east()), Direction.EAST);
        try (Transaction filling = Transaction.openRoot()) {
            helper.assertValueEqual(pipe.insert(FluidResource.of(DrillingMud.SOURCE.get()), 750, filling), 750, "mud the pipe put in");
            filling.commit();
        }
        HarvesterTests.tick(helper, machine(helper), 40);

        helper.assertTrue(FluidStack.matches(menu.drillingFluid(), new FluidStack(DrillingMud.SOURCE.get(), 750)),
                "the Drilling Fluid tank shows " + menu.drillingFluid());
        helper.assertTrue(FluidStack.matches(menu.product(), new FluidStack(CrudeOil.SOURCE.get(), 2_000)),
                "the product tank shows " + menu.product());
        helper.assertTrue(well.amount() > 0, "the well is gone");
        helper.succeed();
    }

    /** Whatever the choice returns, the menu and the status give: no fluid, nothing matching, then none once the recipe is gone. */
    private static void idleReasonIsTheChoices(GameTestHelper helper) {
        HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insertItem(helper, Items.COAL, 4);
        HarvesterBlockEntity machine = machine(helper);
        HarvesterMenu menu = open(helper, player);

        HarvesterTests.tick(helper, machine, 1);
        helper.assertValueEqual(menu.idleReason(), Optional.empty(), "the menu's idle reason while working");
        helper.assertValueEqual(machine.status().idle(), Optional.empty(), "the status's idle reason while working");

        hold(player, MUDDY_IRON);
        HarvesterTests.tick(helper, machine, 1);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_FLUID), "the Harvester's idle reason");
        helper.assertValueEqual(menu.idleReason(), machine.idleReason(), "the menu's idle reason");
        helper.assertValueEqual(machine.status().idle(), machine.idleReason(), "the status's idle reason");

        helper.setBlock(CENTRE, Blocks.STONE);
        HarvesterTests.tick(helper, machine, 1);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NOTHING_MATCHING), "the Harvester's idle reason over stone");
        helper.assertValueEqual(menu.idleReason(), machine.idleReason(), "the menu's idle reason over stone");
        helper.succeed();
    }

    /**
     * Copper under the footprint and the iron nuggets recipe held: the Harvester idles, and the menu names the recipe.
     * The clear packet empties it, the menu follows, and the Harvester draws the copper by its Default recipe.
     */
    private static void clearsTheHeldRecipe(GameTestHelper helper) {
        BlockPos copper = CENTRE.east();
        HarvesterTests.ore(helper, OreResource.COPPER, copper, 0);
        helper.setBlock(CENTRE, Blocks.STONE);
        helper.setBlock(CHEST, Blocks.CHEST);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insertItem(helper, Items.COAL, 4);
        HarvesterBlockEntity machine = machine(helper);
        HarvesterMenu menu = open(helper, player);
        helper.assertValueEqual(menu.held(), Optional.empty(), "the menu's Held recipe before any is held");

        helper.assertTrue(hold(player, IRON_NUGGETS), "the transfer did not hold the iron nuggets recipe");
        HarvesterTests.tick(helper, machine, 100);
        Optional<HeldRecipeView> held = menu.held();
        helper.assertValueEqual(held.map(HeldRecipeView::id), Optional.of(IRON_NUGGETS), "the menu's Held recipe");
        helper.assertTrue(held.isPresent() && ItemStack.matches(held.get().result(), new ItemStack(Items.IRON_NUGGET, 3)),
                "the Held recipe's result is " + held.map(HeldRecipeView::result));
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NOTHING_MATCHING), "the idle reason while the recipe is held");
        helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.RAW_COPPER), 0, "raw copper drawn while the recipe is held");

        helper.assertTrue(clear(player), "the clear packet was refused");

        helper.assertValueEqual(machine.heldRecipe(), Optional.empty(), "the Held recipe after clearing");
        helper.assertValueEqual(menu.held(), Optional.empty(), "the menu's Held recipe after clearing");
        HarvesterTests.tick(helper, machine, 100);
        helper.assertTrue(HarvesterTests.count(helper, CHEST, Items.RAW_COPPER) >= 3, "the Harvester does not run its Default recipes after clearing");
        helper.assertValueEqual(machine.idleReason(), Optional.empty(), "the idle reason once it works again");
        helper.succeed();
    }

    private static void clearNeedsAnOpenHarvester(GameTestHelper helper) {
        helper.setBlock(CENTRE, Blocks.STONE);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterBlockEntity machine = machine(helper);
        machine.setHeldRecipe(IRON_NUGGETS);

        helper.assertFalse(ClearHeldRecipePacket.receive(player, ClearHeldRecipePacket.INSTANCE), "a clear with no Harvester open was taken");
        helper.assertValueEqual(machine.heldRecipe(), Optional.of(IRON_NUGGETS), "the Held recipe after a clear with no menu open");
        helper.succeed();
    }

    /**
     * The fuel slot takes fuel on a burning tier and nothing on an electric one; the fill slot an empty bucket, the drain
     * slot a mud bucket; the buffer slots take nothing from a player but show what the Harvester drew, and a shift-click
     * of coal goes to the fuel slot.
     */
    private static void slots(GameTestHelper helper) {
        HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterBlockEntity machine = machine(helper);
        HarvesterMenu menu = open(helper, player);
        Slot fuel = menu.slots.get(HarvesterMenu.FUEL_SLOT);
        Slot fill = menu.slots.get(HarvesterMenu.FILL_SLOT);
        Slot drain = menu.slots.get(HarvesterMenu.DRAIN_SLOT);
        Slot buffer = menu.slots.get(HarvesterMenu.BUFFER_FIRST);

        helper.assertTrue(fuel.isActive(), "the Burner Harvester's fuel slot is hidden");
        helper.assertTrue(fuel.mayPlace(new ItemStack(Items.COAL)), "the fuel slot refused coal");
        helper.assertFalse(fuel.mayPlace(new ItemStack(Items.COBBLESTONE)), "the fuel slot took cobblestone");
        helper.assertTrue(fill.mayPlace(new ItemStack(Items.BUCKET)), "the fill slot refused an empty bucket");
        helper.assertFalse(fill.mayPlace(new ItemStack(Items.COAL)), "the fill slot took coal");
        helper.assertTrue(drain.mayPlace(new ItemStack(DrillingMud.BUCKET.get())), "the drain slot refused a mud bucket");
        helper.assertFalse(drain.mayPlace(new ItemStack(Items.BUCKET)), "the drain slot took an empty bucket");
        helper.assertFalse(buffer.mayPlace(new ItemStack(Items.COAL)), "an output buffer slot took coal");

        player.getInventory().setItem(9, new ItemStack(Items.COAL, 4));
        menu.quickMoveStack(player, HarvesterMenu.FIRST_PLAYER_SLOT);
        helper.assertValueEqual(fuel.getItem().getCount(), 4, "coal a shift-click put in the fuel slot");
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "coal left in the inventory slot");

        HarvesterTests.tick(helper, machine, 40);
        helper.assertTrue(ItemStack.matches(buffer.getItem(), new ItemStack(Items.RAW_IRON, 2)),
                "the first output buffer slot shows " + buffer.getItem());
        helper.assertTrue(buffer.mayPickup(player), "the player cannot take from the output buffer");
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        HarvesterMenu electric = open(helper, player);
        Slot noFuel = electric.slots.get(HarvesterMenu.FUEL_SLOT);
        helper.assertFalse(noFuel.isActive(), "the Electric Harvester's fuel slot is shown");
        helper.assertFalse(noFuel.mayPlace(new ItemStack(Items.COAL)), "the Electric Harvester's fuel slot took coal");
        helper.succeed();
    }

    private static void noPower(GameTestHelper helper) {
        HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterBlockEntity machine = machine(helper);

        HarvesterTests.tick(helper, machine, 1);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_POWER), "the Burner Harvester's idle reason with no fuel");
        helper.assertValueEqual(menu(helper).idleReason(), Optional.of(IdleReason.NO_POWER), "the menu's idle reason with no fuel");

        insertItem(helper, Items.COAL, 1);
        HarvesterTests.tick(helper, machine, 1);
        helper.assertValueEqual(machine.idleReason(), Optional.empty(), "the idle reason with coal");
        HarvesterTests.tick(helper, machine, new ItemStack(Items.COAL).getBurnTime(null, helper.getLevel().fuelValues()) + 20);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_POWER), "the idle reason once the coal ran out");
        helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN), false);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        HarvesterBlockEntity electric = machine(helper);
        HarvesterTests.tick(helper, electric, 1);
        helper.assertValueEqual(electric.idleReason(), Optional.of(IdleReason.NO_POWER), "the Electric Harvester's idle reason with no FE");
        feed(helper, 400);
        HarvesterTests.tick(helper, electric, 1);
        helper.assertValueEqual(electric.idleReason(), Optional.empty(), "the idle reason with FE");
        helper.succeed();
    }

    /** A full chest at the output face and a stack of fuel: the Harvester draws until its buffer is full, then says so. */
    private static void outputFull(GameTestHelper helper) {
        HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        helper.setBlock(CHEST, Blocks.CHEST);
        Container full = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        insertItem(helper, Items.COAL_BLOCK, 64);
        HarvesterBlockEntity machine = machine(helper);

        HarvesterTests.tick(helper, machine, 15_000);

        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.OUTPUT_FULL), "the idle reason with a full buffer");
        helper.assertValueEqual(menu(helper).idleReason(), Optional.of(IdleReason.OUTPUT_FULL), "the menu's idle reason with a full buffer");
        helper.succeed();
    }

    /** Eight cycles of 1,000 mB fill the 8,000 mB product tank, with nothing at the output face to take it. */
    private static void productTankFull(GameTestHelper helper) {
        well(helper, 60_000_000);
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterTests.holdingTheWellsCycle(helper);
        insertItem(helper, Items.COAL, 4);
        HarvesterBlockEntity machine = machine(helper);

        HarvesterTests.tick(helper, machine, 8 * 20 + 40);

        helper.assertTrue(FluidStack.matches(menu(helper).product(), new FluidStack(CrudeOil.SOURCE.get(), 8_000)), "the product tank is not full");
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.OUTPUT_FULL), "the idle reason with a full product tank");
        helper.assertValueEqual(menu(helper).idleReason(), Optional.of(IdleReason.OUTPUT_FULL), "the menu's idle reason with a full product tank");
        helper.succeed();
    }

    /** The status as the server sends it to Jade, and the Held recipe's sync to a menu, read back through their codecs. */
    private static void statusCrossesTheWire(GameTestHelper helper) {
        HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        HarvesterBlockEntity machine = machine(helper);
        open(helper, player);
        helper.assertTrue(hold(player, MUDDY_IRON), "the transfer did not hold the muddy iron recipe");
        HarvesterTests.tick(helper, machine, 1);
        HarvesterStatus status = machine.status();
        helper.assertValueEqual(status.idle(), Optional.of(IdleReason.NO_FLUID), "the status's idle reason");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        HarvesterStatus.STREAM_CODEC.encode(buffer, status);
        helper.assertValueEqual(HarvesterStatus.STREAM_CODEC.decode(buffer), status, "the status read back");

        Optional<HeldRecipeView> held = machine.heldView(helper.getLevel());
        helper.assertValueEqual(held.map(HeldRecipeView::id), Optional.of(MUDDY_IRON), "the Held recipe's view");
        HeldRecipeSyncPacket packet = new HeldRecipeSyncPacket(7, held);
        HeldRecipeSyncPacket.STREAM_CODEC.encode(buffer, packet);
        HeldRecipeSyncPacket read = HeldRecipeSyncPacket.STREAM_CODEC.decode(buffer);
        helper.assertValueEqual(read.containerId(), 7, "the sync's container id");
        helper.assertValueEqual(read.held().map(HeldRecipeView::id), Optional.of(MUDDY_IRON), "the sync's Held recipe");

        Component state = HarvesterStatusLines.state(status.idle());
        helper.assertTrue(state.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("fieldworks.harvester.idle.no_fluid"),
                "the tooltip does not name the idle reason: " + state);
        helper.succeed();
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static HarvesterBlockEntity machine(GameTestHelper helper) {
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    /** The Harvester's menu as a player's inventory would open it; its data is read live, as the sync reads it. */
    private static HarvesterMenu menu(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "fieldworks_menu"));
        return (HarvesterMenu) machine(helper).createMenu(1, player.getInventory(), player);
    }

    /** The menu opened for {@code player}, as the packets the server handles find it. */
    private static HarvesterMenu open(GameTestHelper helper, FakePlayer player) {
        HarvesterMenu menu = (HarvesterMenu) machine(helper).createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    private static boolean hold(FakePlayer player, Identifier recipe) {
        return HoldRecipePacket.receive(player, new HoldRecipePacket(recipe));
    }

    private static boolean clear(FakePlayer player) {
        return ClearHeldRecipePacket.receive(player, ClearHeldRecipePacket.INSTANCE);
    }

    private static OilWellBlockEntity well(GameTestHelper helper, long amount) {
        helper.setBlock(CENTRE, FieldworksRegistries.OIL_WELL.get());
        OilWellBlockEntity well = helper.getBlockEntity(CENTRE, OilWellBlockEntity.class);
        well.start(amount);
        return well;
    }

    /** Through a block on the footprint's edge, as a hopper there would. */
    private static void insertItem(GameTestHelper helper, net.minecraft.world.item.Item item, int count) {
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            items.insert(ItemResource.of(item), count, feeding);
            feeding.commit();
        }
    }

    private static void feed(GameTestHelper helper, int amount) {
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(energy != null, "the Harvester has no energy capability");
        EnergyHandlerUtil.move(new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000, 1_000_000), energy, amount, null);
    }
}
