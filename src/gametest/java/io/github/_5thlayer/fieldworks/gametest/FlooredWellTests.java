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
 * A well at its floor and the oil well recipe Fieldworks ships (#12, ADR 0007): a well pumps at its floor for good and
 * nothing raises it (ADR-0081), and a recipe's crude is fixed or the well's own cycle. The viewer's data for the oil
 * well's and the harvestable blocks' recipes is checked here too.
 */
final class FlooredWellTests {

    private static final BlockPos CENTRE = ORIGIN.below();
    private static final BlockPos CHEST = ORIGIN.relative(Direction.NORTH, 2);
    private static final BlockPos HOPPER = ORIGIN.relative(Direction.WEST, 2);

    /** The recipe Fieldworks ships: the well's own cycle, for the Electric Harvester and no fluid. */
    private static final Identifier WELL_CRUDE = id("crude_from_oil_well");
    /** A game-test recipe pumping 40 mB a cycle at any yield. */
    private static final Identifier FLAT_CRUDE = id("gametest/crude_flat");

    /** A well that started at a normal amount: it decays to a floor of 60,000, where the Default recipe yields 2 mB a cycle. */
    private static final long NORMAL_START = 300_000;
    private static final long FLOOR = 60_000;
    /** A well that started five times richer: its floor is 300,000, where the Default recipe yields 10 mB a cycle. */
    private static final long RICH_START = 1_500_000;
    /** A well 100 above the floor of one that started at 60,000. */
    private static final long ABOVE_FLOOR = 60_100;

    private FlooredWellTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("patches_a_decayed_oil_well_pumps_its_floor_rate_for_good_and_neither_mud_nor_a_held_recipe_lifts_it", 400,
                helper -> floorIsFinal(helper, NORMAL_START, 2));
        tests.test("patches_a_decayed_rich_oil_well_pumps_its_own_higher_floor_rate_for_good", 400,
                helper -> floorIsFinal(helper, RICH_START, 10));
        tests.test("a_held_crude_recipe_pumps_its_own_amount_and_the_well_still_decays", 20, FlooredWellTests::flatRecipeStillDecays);
        tests.test("one_hopper_of_mud_buckets_and_an_empty_bucket_keeps_both_slots_fed_with_emptied_and_crude_buckets_in_the_chest", 300,
                FlooredWellTests::mixedHopper);
        tests.test("patches_the_shipped_oil_well_recipe_loads_and_a_restoring_crude_result_is_refused", 1, FlooredWellTests::loadsAndRefuses);
        tests.test("patches_the_viewer_lists_the_oil_wells_recipe_in_place_of_its_default_with_the_crude_oil_bucket_as_its_icon", 1,
                FlooredWellTests::viewerListsTheWell);
        tests.test("the_viewer_lists_each_harvestable_blocks_default_recipe_as_a_forty_tick_break_for_its_loot", 1,
                FlooredWellTests::viewerListsBreaks);
    }

    /**
     * An Electric Harvester holding the shipped recipe pumps what the well's own cycle gives: at the floor 2 mB for a
     * well that started at a normal amount, 10 for one five times richer. Drilling mud in the tank is taken by nothing,
     * and the well stays at its floor however long it is pumped (ADR-0081).
     */
    private static void floorIsFinal(GameTestHelper helper, long initial, int perOperation) {
        OilWellBlockEntity well = flooredWell(helper, initial);
        long floor = well.amount();
        HarvesterBlockEntity machine = PatchGateTests.electric(helper);
        fillMud(helper, 1_000);
        machine.setHeldRecipe(WELL_CRUDE);

        HarvesterTests.tick(helper, machine, 200);

        int pumped = crude(helper);
        helper.assertTrue(pumped >= 10 * perOperation, "crude pumped in 200 ticks: " + pumped);
        helper.assertValueEqual(pumped % perOperation, 0, "crude pumped, in whole operations of " + perOperation + " mB");
        helper.assertValueEqual(mud(helper), 1_000, "mud taken");
        helper.assertValueEqual(well.amount(), floor, "the well's amount, at its floor");
        helper.assertValueEqual(machine.idleReason(), Optional.empty(), "the idle reason");
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
     * One hopper holds a mud bucket, an empty bucket and another mud bucket (ADR 0004). The machine has pumped a
     * bucketful of crude first, so the fill slot has something to fill and the drain slot something to drain:
     * both are fed from the one hopper, and the chest at the output face gets a crude bucket and the two emptied
     * mud buckets.
     */
    private static void mixedHopper(GameTestHelper helper) {
        flooredWell(helper, NORMAL_START);
        helper.setBlock(CHEST, Blocks.CHEST);
        HarvesterBlockEntity machine = harvester(helper);
        fillMud(helper, 3_000);
        machine.setHeldRecipe(FLAT_CRUDE);
        // 25 operations of 40 mB: a bucket of crude in the tank before the hopper moves anything.
        HarvesterTests.tick(helper, machine, 2_000);
        helper.assertTrue(crude(helper) >= 1_000, "crude pumped: " + crude(helper));

        helper.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        HopperBlockEntity feed = helper.getBlockEntity(HOPPER, HopperBlockEntity.class);
        feed.setItem(0, new ItemStack(DrillingMud.BUCKET.get()));
        feed.setItem(1, new ItemStack(Items.BUCKET));
        feed.setItem(2, new ItemStack(DrillingMud.BUCKET.get()));

        helper.succeedWhen(() -> {
            helper.assertTrue(feed.isEmpty(), "the hopper still holds items");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, CrudeOil.BUCKET.get()), 1, "crude buckets in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, Items.BUCKET), 2, "empty buckets in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, CHEST, DrillingMud.BUCKET.get()), 0, "mud buckets in the chest");
            helper.assertItemEntityNotPresent(Items.BUCKET);
            helper.assertItemEntityNotPresent(CrudeOil.BUCKET.get());
        });
    }

    private static void loadsAndRefuses(GameTestHelper helper) {
        HarvestRecipe crude = HarvestRecipes.find(helper.getLevel(), WELL_CRUDE)
                .orElseThrow(() -> helper.assertionException("the shipped oil well recipe did not load"));
        helper.assertValueEqual(crude.result(), new HarvestRecipe.Result.Cycle(), "the shipped recipe's crude");
        helper.assertValueEqual(crude.minimumTier(), Optional.of(id("harvester_2")), "the shipped recipe's minimum tier");
        helper.assertValueEqual(crude.drillingFluid(), Optional.empty(), "the shipped recipe's fluid");
        helper.assertValueEqual(crude.replacesDefault(), true, "whether the shipped recipe replaces the Default");
        helper.assertTrue(HarvestRecipes.find(helper.getLevel(), id("crude_from_drilling_mud")).isEmpty(),
                "the drilling mud well recipe still loads");

        expectRefused(helper, "no crude", "crude_oil", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:oil_well", "result": {"crude_oil": 0}, "duration": 20}""");
        expectRefused(helper, "a crude result that restores a share of the yield", "", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:oil_well", "result": {"crude_oil": {"restores": 1.0}}, "duration": 20}""");
        expectRefused(helper, "a crude result that is neither a number nor the well's cycle", "crude_oil", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:oil_well", "result": {"crude_oil": "plenty"}, "duration": 20}""");
        expectRefused(helper, "a crude result from an ore block", "crude", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": {"crude_oil": 10}, "duration": 20}""");
        expectRefused(helper, "the well's cycle from an ore block", "crude", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": {"crude_oil": "cycle"}, "duration": 20}""");
        expectRefused(helper, "an item result from an oil well", "oil", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:oil_well", "result": "minecraft:iron_nugget", "duration": 20}""");
        helper.succeed();
    }

    /**
     * The well has no item, so its slot shows the crude oil bucket. The shipped recipe replaces its Default, so the
     * viewer lists that and no Default; it lists the crude per cycle at a normal well, where that falls to, and the
     * Electric Harvester it needs.
     */
    private static void viewerListsTheWell(GameTestHelper helper) {
        List<HarvestRecipeView> wells = views(helper).stream()
                .filter(view -> view.sources().contains(FieldworksRegistries.OIL_WELL.get())).toList();
        helper.assertValueEqual(wells.stream().filter(HarvestRecipeView::isDefault).count(), 0L, "Default recipes listed for the oil well");
        HarvestRecipeView well = wells.stream().filter(view -> view.id().equals(WELL_CRUDE)).findFirst()
                .orElseThrow(() -> helper.assertionException("the viewer does not list the oil well's recipe"));

        helper.assertValueEqual(well.sourceIcons().stream().map(ItemStack::getItem).toList(), List.of(CrudeOil.BUCKET.get()),
                "the oil well's icon");
        helper.assertValueEqual(well.crude(), 10, "crude per cycle at a normal well");
        helper.assertValueEqual(well.crudeAtFloor(), 2, "crude per cycle at the floor");
        helper.assertTrue(well.result().isEmpty(), "the oil well's recipe lists an item result");
        helper.assertValueEqual(well.duration(), 20, "the oil well's recipe duration");
        helper.assertValueEqual(well.minimumTier(), Optional.of(id("harvester_2")), "the oil well's minimum tier");
        helper.assertValueEqual(well.fluids(), List.of(), "the oil well's fluids");
        helper.assertValueEqual(HarvestRecipeLines.detail(well), Component.translatable("fieldworks.harvesting.falls_to", 2),
                "the line under the oil well's recipe");
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

    /** A well started at {@code initial} and cycled down to its floor. */
    private static OilWellBlockEntity flooredWell(GameTestHelper helper, long initial) {
        OilWellBlockEntity well = well(helper, initial);
        while (!well.atFloor()) {
            well.cycle();
        }
        helper.assertValueEqual(well.amount(), Math.max(FLOOR, initial / 5), "the well's floor");
        return well;
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
