// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipe;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipes;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Harvester's Drilling Fluid (#11): mud in bottles or buckets by hopper, or by pipe, fills its tank; a Held recipe
 * that names a Drilling Fluid consumes it, and only a Held recipe ever does. The recipes are the game tests' own
 * datapack's.
 */
final class DrillingFluidTests {

    private static final BlockPos CENTRE = ORIGIN.below();
    private static final BlockPos CHEST = ORIGIN.relative(Direction.NORTH, 2);
    private static final BlockPos HOPPER = ORIGIN.relative(Direction.WEST, 2);
    private static final Identifier MUDDY_IRON = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/muddy_iron");

    private DrillingFluidTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("mud_bottles_fed_by_hopper_fill_the_drilling_fluid_tank_and_glass_bottles_arrive_in_the_chest", 200,
                DrillingFluidTests::bottlesFillTheTank);
        tests.test("mud_buckets_fed_by_hopper_fill_the_drilling_fluid_tank_and_empty_buckets_arrive_in_the_chest", 200,
                DrillingFluidTests::bucketsFillTheTank);
        tests.test("a_mud_bottle_waits_in_the_drain_slot_while_the_tank_has_no_room_for_it", 20, DrillingFluidTests::waitsForRoom);
        tests.test("a_pipe_fills_the_drilling_fluid_tank_with_mud_and_never_drains_it", 20, DrillingFluidTests::pipeFillsOnly);
        tests.test("the_drilling_fluid_tank_is_never_pushed_to_the_output_face", 20, DrillingFluidTests::neverPushed);
        tests.test("a_held_recipe_with_a_drilling_fluid_consumes_it_per_operation_and_idles_when_the_tank_runs_dry", 20,
                DrillingFluidTests::consumesPerOperation);
        tests.test("with_no_held_recipe_a_harvester_consumes_no_mud_however_much_is_in_the_tank", 20, DrillingFluidTests::consumesNothingUnheld);
        tests.test("an_operation_whose_drilling_fluid_is_gone_when_it_completes_idles_with_no_fluid_and_keeps_its_place_and_progress", 20,
                DrillingFluidTests::fluidGoneAtCompletion);
        tests.test("a_harvest_recipe_loads_with_a_drilling_fluid_and_one_with_none_to_take_is_refused", 1, DrillingFluidTests::loadsAndRefuses);
        tests.test("a_held_recipe_naming_a_fluid_outside_the_drilling_fluids_never_runs", 20, DrillingFluidTests::neverRunsOnAForeignFluid);
        tests.test("the_viewer_lists_a_recipes_drilling_fluid_and_its_mb_per_operation", 1, DrillingFluidTests::viewerLists);
    }

    /** One hopper at the side holding four mud bottles: the tank fills a bottle at a time, the glass bottles reach the chest. */
    private static void bottlesFillTheTank(GameTestHelper helper) {
        feed(helper, new ItemStack(DrillingMud.BOTTLE.get(), 4));
        place(helper);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(mud(helper), 1_000, "mud in the Drilling Fluid tank");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.GLASS_BOTTLE), 4, "glass bottles in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, DrillingMud.BOTTLE.get()), 0, "mud bottles in the chest");
            helper.assertItemEntityNotPresent(Items.GLASS_BOTTLE);
            helper.assertItemEntityNotPresent(DrillingMud.BOTTLE.get());
        });
    }

    private static void bucketsFillTheTank(GameTestHelper helper) {
        feed(helper, new ItemStack(DrillingMud.BUCKET.get()));
        place(helper);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(mud(helper), 1_000, "mud in the Drilling Fluid tank");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.BUCKET), 1, "empty buckets in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, DrillingMud.BUCKET.get()), 0, "mud buckets in the chest");
            helper.assertItemEntityNotPresent(Items.BUCKET);
        });
    }

    /**
     * 7,900 mB in the tank leaves no room for a 250 mB bottle: it stays in the drain slot, whole, and the tank, the
     * chest and the world are as they were.
     */
    private static void waitsForRoom(GameTestHelper helper) {
        HarvesterBlockEntity machine = place(helper);
        fill(helper, 7_900);
        helper.setBlock(CHEST, Blocks.CHEST);
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        try (Transaction feeding = Transaction.openRoot()) {
            helper.assertValueEqual(items.insert(ItemResource.of(DrillingMud.BOTTLE.get()), 1, feeding), 1, "mud bottles the Harvester took");
            feeding.commit();
        }

        HarvesterTests.tick(helper, machine, 30);

        helper.assertValueEqual(mud(helper), 7_900, "mud in the tank");
        helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.GLASS_BOTTLE), 0, "glass bottles in the chest");
        helper.assertValueEqual(HarvesterTests.count(helper, CHEST, DrillingMud.BOTTLE.get()), 0, "mud bottles in the chest");
        helper.assertItemEntityNotPresent(DrillingMud.BOTTLE.get());

        helper.succeed();
    }

    /**
     * A pipe at the Harvester's side puts mud, and only a Drilling Fluid, into the tank up to its 8,000 mB, and takes
     * none out. The mud survives a save and a reload.
     */
    private static void pipeFillsOnly(GameTestHelper helper) {
        HarvesterBlockEntity machine = place(helper);
        ResourceHandler<FluidResource> pipeSide = fluids(helper);

        try (Transaction filling = Transaction.openRoot()) {
            helper.assertValueEqual(pipeSide.insert(FluidResource.of(Fluids.WATER), 1_000, filling), 0, "water a pipe put into the Harvester");
            helper.assertValueEqual(pipeSide.insert(FluidResource.of(CrudeOil.SOURCE.get()), 1_000, filling), 0,
                    "crude a pipe put into the Harvester");
            helper.assertValueEqual(pipeSide.insert(mudResource(), 3_000, filling), 3_000, "mud a pipe put into the Harvester");
            filling.commit();
        }
        helper.assertValueEqual(mud(helper), 3_000, "mud in the tank after the first fill");
        try (Transaction filling = Transaction.openRoot()) {
            helper.assertValueEqual(pipeSide.insert(mudResource(), 6_000, filling), 5_000, "mud a pipe put in past the tank's capacity");
            filling.commit();
        }
        helper.assertValueEqual(mud(helper), 8_000, "mud in the full tank");

        try (Transaction draining = Transaction.openRoot()) {
            helper.assertValueEqual(pipeSide.extract(mudResource(), 8_000, draining), 0, "mud a pipe drained");
            for (int index = 0; index < pipeSide.size(); index++) {
                helper.assertValueEqual(pipeSide.extract(index, mudResource(), 8_000, draining), 0, "mud a pipe drained from tank " + index);
            }
            draining.commit();
        }
        helper.assertValueEqual(mud(helper), 8_000, "mud left in the tank");

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        machine.saveCustomOnly(saved);
        HarvesterBlockEntity loaded = new HarvesterBlockEntity(helper.absolutePos(ORIGIN), helper.getBlockState(ORIGIN));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        helper.getLevel().setBlockEntity(loaded);
        helper.assertValueEqual(mud(helper), 8_000, "mud in the tank read back");
        helper.succeed();
    }

    /** A tank at the output face takes crude the Harvester pushes, never the mud: that tank is passive (ADR 0004). */
    private static void neverPushed(GameTestHelper helper) {
        helper.setBlock(CHEST, TestTank.BLOCK.get());
        TestTank.Entity tank = helper.getBlockEntity(CHEST, TestTank.Entity.class);
        HarvesterBlockEntity machine = place(helper);
        fill(helper, 2_000);

        HarvesterTests.tick(helper, machine, 30);

        helper.assertValueEqual(tank.tanks.getAmountAsInt(0), 0, "fluid pushed into the neighbour's first tank");
        helper.assertValueEqual(tank.tanks.getAmountAsInt(1), 0, "fluid pushed into the neighbour's second tank");
        helper.assertValueEqual(mud(helper), 2_000, "mud left in the Harvester");
        helper.succeed();
    }

    /**
     * Holding a recipe that takes 250 mB of mud and gives two raw iron for an operation: 500 mB do two operations,
     * and the third finds the tank dry and idles, leaving the ore alone.
     */
    private static void consumesPerOperation(GameTestHelper helper) {
        int initial = HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        helper.setBlock(CHEST, Blocks.CHEST);
        HarvesterBlockEntity machine = place(helper);
        fill(helper, 500);
        feedCoal(helper);
        machine.setHeldRecipe(MUDDY_IRON);

        HarvesterTests.tick(helper, machine, 19);
        helper.assertValueEqual(mud(helper), 500, "mud before the first operation completes");
        HarvesterTests.tick(helper, machine, 1);
        helper.assertValueEqual(mud(helper), 250, "mud after one operation");
        HarvesterTests.tick(helper, machine, 20);
        helper.assertValueEqual(mud(helper), 0, "mud after two operations");
        HarvesterTests.tick(helper, machine, 41);

        helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.RAW_IRON), 4, "raw iron from two operations of two");
        helper.assertValueEqual(HarvesterTests.remaining(helper, CENTRE), initial - 2, "the ore block's amount");
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_FLUID), "the idle reason");
        helper.succeed();
    }

    /**
     * Nineteen ticks into a 20-tick operation on the first of two iron ore blocks the tank is emptied, as a save
     * edited between ticks would leave it: the last tick idles with no fluid, spends no burn, draws nothing and keeps
     * its progress and its place in the round, and with mud back the operation completes on the same block (#11).
     */
    private static void fluidGoneAtCompletion(GameTestHelper helper) {
        BlockPos first = new BlockPos(3, 1, 3);
        BlockPos last = new BlockPos(5, 1, 5);
        int initial = HarvesterTests.ore(helper, OreResource.IRON, first, 0);
        HarvesterTests.ore(helper, OreResource.IRON, last, 0);
        HarvesterBlockEntity machine = place(helper);
        fill(helper, 250);
        feedCoal(helper);
        machine.setHeldRecipe(MUDDY_IRON);
        HarvesterTests.tick(helper, machine, 19);

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        machine.saveCustomOnly(saved);
        CompoundTag tag = saved.buildResult();
        tag.remove("drilling_fluid");
        HarvesterBlockEntity emptied = new HarvesterBlockEntity(helper.absolutePos(ORIGIN), helper.getBlockState(ORIGIN));
        emptied.setLevel(helper.getLevel());
        emptied.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        helper.getLevel().setBlockEntity(emptied);
        int burnLeft = emptied.status().burnLeft();

        HarvesterTests.tick(helper, emptied, 1);
        helper.assertValueEqual(emptied.idleReason(), Optional.of(IdleReason.NO_FLUID), "the idle reason with the mud gone");
        helper.assertValueEqual(emptied.status().progress(), 19, "progress kept");
        helper.assertValueEqual(emptied.status().burnLeft(), burnLeft, "burn left");
        helper.assertValueEqual(HarvesterTests.remaining(helper, first), initial, "the first ore block's amount");

        fill(helper, 250);
        HarvesterTests.tick(helper, emptied, 1);
        helper.assertValueEqual(emptied.idleReason(), Optional.empty(), "the idle reason with the mud back");
        helper.assertValueEqual(HarvesterTests.remaining(helper, first), initial - 1, "the first ore block's amount");
        helper.assertValueEqual(HarvesterTests.remaining(helper, last), initial, "the last ore block's amount");
        helper.assertValueEqual(mud(helper), 0, "mud left");
        helper.succeed();
    }

    /** The recipe is on offer and the tank is full of mud, but nothing is held: the Default recipe runs and no mud goes. */
    private static void consumesNothingUnheld(GameTestHelper helper) {
        int initial = HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        helper.setBlock(CHEST, Blocks.CHEST);
        HarvesterBlockEntity machine = place(helper);
        fill(helper, 8_000);
        feedCoal(helper);

        HarvesterTests.tick(helper, machine, 61);

        helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.RAW_IRON), 3, "raw iron from three Default draws");
        helper.assertValueEqual(HarvesterTests.remaining(helper, CENTRE), initial - 3, "the ore block's amount");
        helper.assertValueEqual(mud(helper), 8_000, "mud left in the tank");
        helper.assertValueEqual(machine.idleReason(), Optional.empty(), "the idle reason");
        helper.succeed();
    }

    private static void loadsAndRefuses(GameTestHelper helper) {
        HarvestRecipe muddy = HarvestRecipes.find(helper.getLevel(), MUDDY_IRON)
                .orElseThrow(() -> helper.assertionException("the muddy_iron recipe did not load"));
        HarvestRecipe.FluidInput input = muddy.drillingFluid().orElseThrow(() -> helper.assertionException("the recipe has no drilling fluid"));
        helper.assertValueEqual(input.amount(), 250, "the recipe's mB per operation");
        helper.assertTrue(input.serving().stream().anyMatch(fluid -> fluid.value() == DrillingMud.SOURCE.get()),
                "#fieldworks:drilling_fluids does not serve a recipe naming it");
        helper.assertTrue(HarvestRecipes.find(helper.getLevel(), Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/iron_nuggets"))
                .orElseThrow().drillingFluid().isEmpty(), "a recipe that names no drilling fluid has one");

        expectRefused(helper, "a drilling fluid of 0 mB", "amount", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": "minecraft:iron_nugget", "duration": 20,
                 "drilling_fluid": {"fluid": "fieldworks:drilling_mud", "amount": 0}}""");
        expectRefused(helper, "a drilling fluid naming no fluid", "fluid", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": "minecraft:iron_nugget", "duration": 20,
                 "drilling_fluid": {"fluid": [], "amount": 250}}""");
        expectRefused(helper, "a drilling fluid that is no fluid", "no_such_fluid", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": "minecraft:iron_nugget", "duration": 20,
                 "drilling_fluid": {"fluid": "fieldworks:no_such_fluid", "amount": 250}}""");
        helper.succeed();
    }

    /** Water is no Drilling Fluid: a recipe that names only water loads, but a tank of mud cannot meet it. */
    private static void neverRunsOnAForeignFluid(GameTestHelper helper) {
        int initial = HarvesterTests.ore(helper, OreResource.IRON, CENTRE, 0);
        helper.setBlock(CHEST, Blocks.CHEST);
        HarvesterBlockEntity machine = place(helper);
        fill(helper, 1_000);
        feedCoal(helper);
        machine.setHeldRecipe(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/watery_iron"));

        HarvesterTests.tick(helper, machine, 41);

        helper.assertValueEqual(HarvesterTests.remaining(helper, CENTRE), initial, "the ore block's amount");
        helper.assertValueEqual(mud(helper), 1_000, "mud left in the tank");
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_FLUID), "the idle reason");
        helper.succeed();
    }

    private static void viewerLists(GameTestHelper helper) {
        List<HarvestRecipeView> views = HarvestRecipeView.all(helper.getLevel().getServer().getRecipeManager().recipeMap());
        HarvestRecipeView muddy = views.stream().filter(view -> view.id().equals(MUDDY_IRON)).findFirst()
                .orElseThrow(() -> helper.assertionException("the viewer does not list the muddy_iron recipe"));

        helper.assertValueEqual(muddy.fluids(), List.of(DrillingMud.SOURCE.get()), "the fluids the viewer lists");
        helper.assertValueEqual(muddy.fluidAmount(), 250, "the mB the viewer lists");
        helper.assertTrue(views.stream().filter(HarvestRecipeView::isDefault).allMatch(view -> view.fluids().isEmpty()),
                "a Default recipe lists a fluid");
        helper.succeed();
    }

    private static void expectRefused(GameTestHelper helper, String what, String named, String json) {
        DataResult<Recipe<?>> result = Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(json));
        String error = result.error().map(DataResult.Error::message).orElse("");
        helper.assertTrue(result.error().isPresent(), "a recipe with " + what + " was accepted");
        helper.assertTrue(error.contains(named), "the refusal of " + what + " does not say '" + named + "': " + error);
    }

    // -- fixtures -------------------------------------------------------------------------------

    /** A hopper at the side, pointing in, holding {@code stack}; the chest is at the output face. */
    private static void feed(GameTestHelper helper, ItemStack stack) {
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.getBlockEntity(HOPPER, HopperBlockEntity.class).setItem(0, stack);
    }

    /** A Burner Harvester facing north over the layer, which has stone under it unless a test set something else. */
    private static HarvesterBlockEntity place(GameTestHelper helper) {
        if (helper.getBlockState(CENTRE).isAir()) {
            helper.setBlock(CENTRE, Blocks.STONE);
        }
        HarvesterTests.place(helper, Direction.NORTH);
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    private static void feedCoal(GameTestHelper helper) {
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        try (Transaction feeding = Transaction.openRoot()) {
            items.insert(ItemResource.of(Items.COAL), 8, feeding);
            feeding.commit();
        }
    }

    /** Puts mud into the tank by the fluid capability, as a pipe would. */
    private static void fill(GameTestHelper helper, int amount) {
        try (Transaction filling = Transaction.openRoot()) {
            helper.assertValueEqual(fluids(helper).insert(mudResource(), amount, filling), amount, "mud put into the tank");
            filling.commit();
        }
    }

    private static FluidResource mudResource() {
        return FluidResource.of(DrillingMud.SOURCE.get());
    }

    /** The fluid capability on a side of the footprint, where a pipe would meet it. */
    private static ResourceHandler<FluidResource> fluids(GameTestHelper helper) {
        ResourceHandler<FluidResource> fluids = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(ORIGIN.east()), Direction.EAST);
        helper.assertTrue(fluids != null, "the Harvester has no fluid capability");
        return fluids;
    }

    /** The mud in the Harvester's tanks, as a pipe at its side reads them. */
    private static int mud(GameTestHelper helper) {
        ResourceHandler<FluidResource> fluids = fluids(helper);
        int mud = 0;
        for (int index = 0; index < fluids.size(); index++) {
            if (fluids.getResource(index).equals(mudResource())) {
                mud += fluids.getAmountAsInt(index);
            }
        }
        return mud;
    }
}
