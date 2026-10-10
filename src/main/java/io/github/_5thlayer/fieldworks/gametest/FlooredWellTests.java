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
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.compat.HarvestRecipeLines;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipe;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipes;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.YieldCondition;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A well at its floor and the drilling mud recipe Fieldworks ships (#12): the Default recipe pumps the floor rate
 * and never stops, the mud recipe beats it, and a recipe's yield condition is respected. The viewer's data for the
 * oil well's and the harvestable blocks' Default recipes is checked here too.
 */
final class FlooredWellTests {

    private static final BlockPos CENTRE = ORIGIN.below();
    private static final BlockPos CHEST = ORIGIN.relative(Direction.NORTH, 2);
    private static final BlockPos HOPPER = ORIGIN.relative(Direction.WEST, 2);

    /** The recipe Fieldworks ships: crude at the floor for drilling mud. */
    private static final Identifier MUD_CRUDE = id("crude_from_drilling_mud");
    /** A game-test recipe pumping 40 mB a cycle at any yield. */
    private static final Identifier FLAT_CRUDE = id("gametest/crude_flat");

    /** A well whose amount is its floor, 60,000: the Default recipe yields 2 mB a cycle there. */
    private static final long FLOOR = 60_000;
    /** A well 100 above that floor. */
    private static final long ABOVE_FLOOR = 60_100;

    private FlooredWellTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_floored_well_held_to_the_drilling_mud_recipe_yields_more_crude_per_operation_than_its_default", 20,
                FlooredWellTests::mudBeatsTheFloor);
        tests.test("a_well_above_its_floor_ignores_the_drilling_mud_recipe_that_needs_it_at_the_floor", 20, FlooredWellTests::aboveTheFloor);
        tests.test("a_held_well_recipe_with_no_drilling_mud_in_the_tank_idles_and_pumps_nothing", 20, FlooredWellTests::noMud);
        tests.test("a_held_crude_recipe_pumps_its_own_amount_and_the_well_still_decays", 20, FlooredWellTests::flatRecipeStillDecays);
        tests.test("one_hopper_of_mud_bottles_and_empty_buckets_keeps_both_slots_fed_with_glass_bottles_and_crude_buckets_in_the_chest", 300,
                FlooredWellTests::mixedHopper);
        tests.test("a_well_recipe_loads_a_crude_result_and_a_yield_condition_and_a_bad_one_is_refused", 1, FlooredWellTests::loadsAndRefuses);
        tests.test("the_viewer_lists_the_oil_wells_default_recipe_with_the_crude_oil_bucket_as_its_icon_and_its_crude_decaying_to_a_floor", 1,
                FlooredWellTests::viewerListsTheWell);
        tests.test("the_viewer_lists_the_drilling_mud_recipes_crude_fluid_and_yield_condition", 1, FlooredWellTests::viewerListsTheMudRecipe);
        tests.test("the_viewer_lists_each_harvestable_blocks_default_recipe_as_a_forty_tick_break_for_its_loot", 1,
                FlooredWellTests::viewerListsBreaks);
    }

    /**
     * At its floor the well pumps 2 mB a cycle with nothing held, mud in the tank or not. Held to the shipped recipe it
     * pumps 10 and takes 25 mB of mud, and the well stays at its floor (ADR 0002).
     */
    private static void mudBeatsTheFloor(GameTestHelper helper) {
        OilWellBlockEntity well = well(helper, FLOOR);
        HarvesterBlockEntity machine = harvester(helper);
        fillMud(helper, 1_000);

        HarvesterTests.tick(helper, machine, 20);
        int byDefault = crude(helper);
        helper.assertValueEqual(byDefault, 2, "crude from one Default operation at the floor");
        helper.assertValueEqual(mud(helper), 1_000, "mud taken by a Default operation");

        machine.setHeldRecipe(MUD_CRUDE);
        HarvesterTests.tick(helper, machine, 20);
        int byMud = crude(helper) - byDefault;
        helper.assertTrue(byMud > byDefault, "the mud recipe pumped " + byMud + " mB, the Default " + byDefault);
        helper.assertValueEqual(byMud, 10, "crude from one operation of the mud recipe");
        helper.assertValueEqual(mud(helper), 975, "mud left after one operation");
        helper.assertValueEqual(well.amount(), FLOOR, "the well's amount, at its floor");
        helper.assertValueEqual(machine.idleReason(), Optional.empty(), "the idle reason");
        helper.succeed();
    }

    /** The recipe's yield condition is at the floor: a well still decaying is not matched, and the Default is not run in its place. */
    private static void aboveTheFloor(GameTestHelper helper) {
        OilWellBlockEntity well = well(helper, ABOVE_FLOOR);
        HarvesterBlockEntity machine = harvester(helper);
        fillMud(helper, 1_000);
        machine.setHeldRecipe(MUD_CRUDE);

        HarvesterTests.tick(helper, machine, 40);

        helper.assertValueEqual(crude(helper), 0, "crude pumped");
        helper.assertValueEqual(mud(helper), 1_000, "mud taken");
        helper.assertValueEqual(well.amount(), ABOVE_FLOOR, "the well's amount");
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NOTHING_MATCHING), "the idle reason");
        helper.succeed();
    }

    private static void noMud(GameTestHelper helper) {
        OilWellBlockEntity well = well(helper, FLOOR);
        HarvesterBlockEntity machine = harvester(helper);
        machine.setHeldRecipe(MUD_CRUDE);

        HarvesterTests.tick(helper, machine, 40);

        helper.assertValueEqual(crude(helper), 0, "crude pumped");
        helper.assertValueEqual(well.amount(), FLOOR, "the well's amount");
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_FLUID), "the idle reason");
        helper.succeed();
    }

    /** A recipe's crude is what a cycle pumps; the cycle still takes its step off a well above its floor. */
    private static void flatRecipeStillDecays(GameTestHelper helper) {
        OilWellBlockEntity well = well(helper, ABOVE_FLOOR);
        HarvesterBlockEntity machine = harvester(helper);
        machine.setHeldRecipe(FLAT_CRUDE);

        HarvesterTests.tick(helper, machine, 20);

        helper.assertValueEqual(crude(helper), 40, "crude from one operation of the recipe");
        helper.assertValueEqual(well.amount(), ABOVE_FLOOR - 10, "the well's amount after one cycle");
        helper.succeed();
    }

    /**
     * One hopper holds a mud bottle, an empty bucket and another mud bottle (ADR 0004). The machine has pumped a
     * bucketful of crude first, so the fill slot has something to fill and the drain slot something to drain:
     * both are fed from the one hopper, and the chest at the output face gets a crude bucket and two glass bottles.
     */
    private static void mixedHopper(GameTestHelper helper) {
        well(helper, FLOOR);
        helper.setBlock(CHEST, Blocks.CHEST);
        HarvesterBlockEntity machine = harvester(helper);
        fillMud(helper, 3_000);
        machine.setHeldRecipe(MUD_CRUDE);
        // 100 operations of 10 mB: a bucket of crude in the tank before the hopper moves anything.
        HarvesterTests.tick(helper, machine, 2_000);
        helper.assertTrue(crude(helper) >= 1_000, "crude pumped: " + crude(helper));

        helper.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        HopperBlockEntity feed = helper.getBlockEntity(HOPPER, HopperBlockEntity.class);
        feed.setItem(0, new ItemStack(DrillingMud.BOTTLE.get()));
        feed.setItem(1, new ItemStack(Items.BUCKET));
        feed.setItem(2, new ItemStack(DrillingMud.BOTTLE.get()));

        helper.succeedWhen(() -> {
            helper.assertTrue(feed.isEmpty(), "the hopper still holds items");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, CrudeOil.BUCKET.get()), 1, "crude buckets in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.GLASS_BOTTLE), 2, "glass bottles in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.BUCKET), 0, "empty buckets in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, DrillingMud.BOTTLE.get()), 0, "mud bottles in the chest");
            helper.assertItemEntityNotPresent(Items.GLASS_BOTTLE);
            helper.assertItemEntityNotPresent(CrudeOil.BUCKET.get());
        });
    }

    private static void loadsAndRefuses(GameTestHelper helper) {
        HarvestRecipe mud = HarvestRecipes.find(helper.getLevel(), MUD_CRUDE)
                .orElseThrow(() -> helper.assertionException("the shipped drilling mud recipe did not load"));
        helper.assertValueEqual(mud.yieldCondition(), Optional.of(YieldCondition.AT_FLOOR), "the shipped recipe's yield condition");
        helper.assertValueEqual(mud.result(), new HarvestRecipe.Result.Crude(10), "the shipped recipe's crude");
        helper.assertValueEqual(mud.drillingFluid().orElseThrow().amount(), 25, "the shipped recipe's mud per operation");
        helper.assertValueEqual(mud.replacesDefault(), false, "whether the shipped recipe replaces the Default");
        HarvestRecipe flat = HarvestRecipes.find(helper.getLevel(), FLAT_CRUDE).orElseThrow();
        helper.assertValueEqual(flat.yieldCondition(), Optional.empty(), "a recipe with no yield condition");

        expectRefused(helper, "a yield condition that is neither", "yield_condition", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:oil_well", "result": {"crude_oil": 10}, "duration": 20,
                 "yield_condition": "sometimes"}""");
        expectRefused(helper, "no crude", "crude_oil", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:oil_well", "result": {"crude_oil": 0}, "duration": 20}""");
        expectRefused(helper, "a crude result from an ore block", "crude", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": {"crude_oil": 10}, "duration": 20}""");
        expectRefused(helper, "an item result from an oil well", "oil", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:oil_well", "result": "minecraft:iron_nugget", "duration": 20}""");
        expectRefused(helper, "a yield condition on an ore block", "yield_condition", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": "minecraft:iron_nugget", "duration": 20,
                 "yield_condition": "at_floor"}""");
        helper.succeed();
    }

    /** The well has no item, so its slot shows the crude oil bucket; its output is crude per cycle at a normal well, and where it falls to. */
    private static void viewerListsTheWell(GameTestHelper helper) {
        HarvestRecipeView well = views(helper).stream()
                .filter(view -> view.isDefault() && view.sources().contains(FieldworksRegistries.OIL_WELL.get())).findFirst()
                .orElseThrow(() -> helper.assertionException("the viewer does not list the oil well's Default recipe"));

        helper.assertValueEqual(well.sourceIcons().stream().map(ItemStack::getItem).toList(), List.of(CrudeOil.BUCKET.get()),
                "the oil well's icon");
        helper.assertValueEqual(well.crude(), 10, "crude per cycle at a normal well");
        helper.assertValueEqual(well.crudeAtFloor(), 2, "crude per cycle at the floor");
        helper.assertTrue(well.result().isEmpty(), "the oil well's Default lists an item result");
        helper.assertValueEqual(well.duration(), 20, "the oil well's Default duration");
        helper.assertValueEqual(well.minimumTier(), Optional.empty(), "the oil well's Default minimum tier");
        helper.succeed();
    }

    private static void viewerListsTheMudRecipe(GameTestHelper helper) {
        HarvestRecipeView mud = views(helper).stream().filter(view -> view.id().equals(MUD_CRUDE)).findFirst()
                .orElseThrow(() -> helper.assertionException("the viewer does not list the drilling mud recipe"));

        helper.assertFalse(mud.isDefault(), "the drilling mud recipe is listed as a Default recipe");
        helper.assertValueEqual(mud.crude(), 10, "the recipe's crude");
        helper.assertValueEqual(mud.crudeAtFloor(), 0, "a written recipe lists no floor figure");
        helper.assertValueEqual(mud.yieldCondition(), Optional.of(YieldCondition.AT_FLOOR), "the recipe's yield condition");
        helper.assertValueEqual(mud.fluids(), List.of(DrillingMud.SOURCE.get()), "the recipe's fluids");
        helper.assertValueEqual(mud.fluidAmount(), 25, "the recipe's mud per operation");
        helper.assertValueEqual(mud.sourceIcons().stream().map(ItemStack::getItem).toList(), List.of(CrudeOil.BUCKET.get()),
                "the recipe's source icon");
        helper.succeed();
    }

    /** Diamond ore is harvestable and not an ore block: a 40-tick break. Iron ore is in the same tag and is only drawn, in 20. */
    private static void viewerListsBreaks(GameTestHelper helper) {
        List<HarvestRecipeView> views = views(helper);
        HarvestRecipeView diamond = views.stream()
                .filter(view -> view.isDefault() && view.sources().contains(Blocks.DIAMOND_ORE)).findFirst()
                .orElseThrow(() -> helper.assertionException("the viewer does not list diamond ore's Default recipe"));

        helper.assertValueEqual(diamond.duration(), 40, "diamond ore's Default duration");
        helper.assertTrue(diamond.breaksForLoot(), "diamond ore's Default does not break for its loot");
        helper.assertValueEqual(HarvestRecipeLines.detail(diamond), Component.empty(), "the line under diamond ore's Default");
        helper.assertValueEqual(diamond.sourceIcons().stream().map(ItemStack::getItem).toList(), List.of(Items.DIAMOND_ORE),
                "diamond ore's icon");
        helper.assertValueEqual(diamond.minimumTier(), Optional.empty(), "diamond ore's Default minimum tier");

        var iron = FieldworksRegistries.ore(io.github._5thlayer.fieldworks.ore.OreResource.IRON).get();
        List<HarvestRecipeView> ironDefaults = views.stream().filter(view -> view.isDefault() && view.sources().contains(iron)).toList();
        helper.assertValueEqual(ironDefaults.size(), 1, "Default recipes listed for iron ore");
        helper.assertValueEqual(ironDefaults.get(0).duration(), 20, "iron ore's Default duration");
        helper.assertFalse(ironDefaults.get(0).breaksForLoot(), "iron ore's Default breaks for its loot");
        helper.assertTrue(views.stream().filter(HarvestRecipeView::isDefault).map(HarvestRecipeView::id).distinct().count()
                        == views.stream().filter(HarvestRecipeView::isDefault).count(),
                "two Default recipes share an id");
        helper.succeed();
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, path);
    }

    private static List<HarvestRecipeView> views(GameTestHelper helper) {
        return HarvestRecipeView.all(helper.getLevel().getServer().getRecipeManager().recipeMap());
    }

    private static void expectRefused(GameTestHelper helper, String what, String named, String json) {
        DataResult<Recipe<?>> result = Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(json));
        String error = result.error().map(DataResult.Error::message).orElse("");
        helper.assertTrue(result.error().isPresent(), "a recipe with " + what + " was accepted");
        helper.assertTrue(error.contains(named), "the refusal of " + what + " does not say '" + named + "': " + error);
    }

    private static OilWellBlockEntity well(GameTestHelper helper, long amount) {
        helper.setBlock(CENTRE, FieldworksRegistries.OIL_WELL.get());
        OilWellBlockEntity well = helper.getBlockEntity(CENTRE, OilWellBlockEntity.class);
        well.start(amount);
        return well;
    }

    /** A Burner Harvester facing north over the layer, fed coal through its item capability. */
    private static HarvesterBlockEntity harvester(GameTestHelper helper) {
        HarvesterTests.place(helper, Direction.NORTH);
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            items.insert(ItemResource.of(Items.COAL), 64, feeding);
            feeding.commit();
        }
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    /** Puts mud into the Drilling Fluid tank by the fluid capability, as a pipe would. */
    private static void fillMud(GameTestHelper helper, int amount) {
        try (Transaction filling = Transaction.openRoot()) {
            helper.assertValueEqual(fluids(helper).insert(FluidResource.of(DrillingMud.SOURCE.get()), amount, filling), amount,
                    "mud put into the tank");
            filling.commit();
        }
    }

    private static ResourceHandler<FluidResource> fluids(GameTestHelper helper) {
        ResourceHandler<FluidResource> fluids = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(ORIGIN.east()), Direction.EAST);
        helper.assertTrue(fluids != null, "the Harvester has no fluid capability");
        return fluids;
    }

    private static int crude(GameTestHelper helper) {
        return amountOf(helper, FluidResource.of(CrudeOil.SOURCE.get()));
    }

    private static int mud(GameTestHelper helper) {
        return amountOf(helper, FluidResource.of(DrillingMud.SOURCE.get()));
    }

    private static int amountOf(GameTestHelper helper, FluidResource fluid) {
        ResourceHandler<FluidResource> fluids = fluids(helper);
        int total = 0;
        for (int index = 0; index < fluids.size(); index++) {
            if (fluids.getResource(index).equals(fluid)) {
                total += fluids.getAmountAsInt(index);
            }
        }
        return total;
    }
}
