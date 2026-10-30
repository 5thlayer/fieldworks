// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipe;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipes;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
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

/**
 * Harvest Recipes (#8): read from a datapack, and run by a Harvester. The recipes are the game tests' own
 * datapack's ({@code gametest_pack/}), so a player's world never has them.
 */
final class HarvestRecipeTests {

    private HarvestRecipeTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_datapack_harvest_recipe_for_a_block_or_a_block_tag_loads", 1, HarvestRecipeTests::loads);
        tests.test("a_malformed_harvest_recipe_is_refused", 1, HarvestRecipeTests::refusesMalformed);
        tests.test("a_recipe_that_replaces_the_default_makes_the_harvester_skip_that_block_and_work_the_rest", 200,
                HarvestRecipeTests::skipsReplaced);
        tests.test("a_held_recipe_whose_minimum_tier_is_harvester_2_idles_on_harvester_1_and_runs_on_harvester_2", 20,
                HarvestRecipeTests::minimumTier);
    }

    private static Identifier recipe(String name) {
        return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/" + name);
    }

    private static void loads(GameTestHelper helper) {
        HarvestRecipe nuggets = HarvestRecipes.find(helper.getLevel(), recipe("iron_nuggets"))
                .orElseThrow(() -> helper.assertionException("the iron_nuggets recipe did not load"));
        helper.assertTrue(nuggets.source().contains(FieldworksRegistries.ore(OreResource.IRON).get().builtInRegistryHolder()),
                "the recipe's source does not hold iron ore");
        helper.assertValueEqual(nuggets.result().stack().getItem(), Items.IRON_NUGGET, "the recipe's result item");
        helper.assertValueEqual(nuggets.result().stack().getCount(), 3, "the recipe's result count");
        helper.assertValueEqual(nuggets.duration(), 40, "the recipe's duration");
        helper.assertValueEqual(nuggets.minimumTier().orElseThrow(), TierBlock.HARVESTER_1.id(), "the recipe's minimum tier");
        helper.assertFalse(nuggets.replacesDefault(), "replaces_default is false when omitted");

        HarvestRecipe everyOre = HarvestRecipes.find(helper.getLevel(), recipe("every_ore")).orElseThrow(
                () -> helper.assertionException("the every_ore recipe, whose source is a block tag, did not load"));
        helper.assertTrue(everyOre.source().contains(FieldworksRegistries.ore(OreResource.COPPER).get().builtInRegistryHolder()),
                "a recipe sourced from #c:ores does not hold copper ore");
        helper.assertTrue(everyOre.minimumTier().isEmpty(), "a recipe that names no minimum tier has one");
        helper.succeed();
    }

    /**
     * The load drops a recipe its codec refuses and logs it by id; here the three that the game tests' datapack
     * holds are absent, and the same text through the codec says what is wrong.
     */
    private static void refusesMalformed(GameTestHelper helper) {
        for (String name : new String[] {"malformed_duration", "malformed_tier", "malformed_source"}) {
            helper.assertTrue(HarvestRecipes.find(helper.getLevel(), recipe(name)).isEmpty(), name + " was loaded");
        }
        expectRefused(helper, "a duration of 0", "duration", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": "minecraft:iron_nugget", "duration": 0}""");
        expectRefused(helper, "a minimum tier no Harvester has", "harvester_9", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "result": "minecraft:iron_nugget",
                 "duration": 20, "minimum_tier": "fieldworks:harvester_9"}""");
        expectRefused(helper, "a source that is no block", "no_such_ore", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:no_such_ore", "result": "minecraft:iron_nugget", "duration": 20}""");
        expectRefused(helper, "a recipe with no result", "result", """
                {"type": "fieldworks:harvesting", "source": "fieldworks:iron_ore", "duration": 20}""");
        helper.succeed();
    }

    private static void expectRefused(GameTestHelper helper, String what, String named, String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<Recipe<?>> result = Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), element);
        String error = result.error().map(DataResult.Error::message).orElse("");
        helper.assertTrue(result.error().isPresent(), "a recipe with " + what + " was accepted");
        helper.assertTrue(error.contains(named), "the refusal of " + what + " does not say '" + named + "': " + error);
    }

    /**
     * Uranium ore is replaced by a recipe, iron and copper are not: fed coal, the Harvester draws the iron and
     * the copper over and over and never the uranium.
     */
    private static void skipsReplaced(GameTestHelper helper) {
        BlockPos uranium = new BlockPos(3, 1, 3);
        BlockPos iron = new BlockPos(5, 1, 5);
        BlockPos copper = new BlockPos(3, 1, 5);
        int uraniumInitial = HarvesterTests.ore(helper, OreResource.URANIUM, uranium, 0);
        int ironInitial = HarvesterTests.ore(helper, OreResource.IRON, iron, 0);
        int copperInitial = HarvesterTests.ore(helper, OreResource.COPPER, copper, 0);

        // The Harvester is placed by a click on the block under it, which no ore block here stands as.
        helper.setBlock(HarvesterTests.ORIGIN.below(), Blocks.STONE);
        BlockPos chest = HarvesterTests.ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);
        BlockPos hopper = HarvesterTests.ORIGIN.relative(Direction.WEST, 2);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 4));
        HarvesterTests.place(helper, Direction.NORTH);

        helper.succeedWhen(() -> {
            helper.assertTrue(HarvesterTests.count(helper, chest, Items.RAW_IRON) >= 2, "the iron is not being drawn");
            helper.assertTrue(HarvesterTests.count(helper, chest, Items.RAW_COPPER) >= 2, "the copper is not being drawn");
            helper.assertTrue(HarvesterTests.remaining(helper, iron) < ironInitial, "the iron ore block is untouched");
            helper.assertTrue(HarvesterTests.remaining(helper, copper) < copperInitial, "the copper ore block is untouched");
            helper.assertValueEqual(HarvesterTests.remaining(helper, uranium), uraniumInitial, "the uranium ore block's amount");
            helper.assertValueEqual(HarvesterTests.count(helper, chest, FieldworksRegistries.RAW_URANIUM.get()), 0, "raw uranium in the chest");
        });
    }

    /**
     * harvester_1, rank 1, refuses a recipe that needs harvester_2 and draws nothing in its place; harvester_2, at
     * speed x2, runs the 40-tick recipe in 20 ticks.
     */
    private static void minimumTier(GameTestHelper helper) {
        BlockPos ore = HarvesterTests.ORIGIN.below();
        int initial = HarvesterTests.ore(helper, OreResource.IRON, ore, 0);
        BlockPos chest = HarvesterTests.ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_1);
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(HarvesterTests.ORIGIN.west()), Direction.WEST);
        try (Transaction feeding = Transaction.openRoot()) {
            items.insert(ItemResource.of(Items.COAL), 4, feeding);
            feeding.commit();
        }
        HarvesterBlockEntity rank1 = helper.getBlockEntity(HarvesterTests.ORIGIN, HarvesterBlockEntity.class);
        rank1.setHeldRecipe(recipe("rank_2_nuggets"));
        HarvesterTests.tick(helper, rank1, 40);
        helper.assertValueEqual(rank1.idleReason(), Optional.of(IdleReason.TIER_TOO_LOW), "harvester_1's idle reason");
        helper.assertValueEqual(HarvesterTests.remaining(helper, ore), initial, "the ore block's amount under harvester_1");
        helper.getLevel().destroyBlock(helper.absolutePos(HarvesterTests.ORIGIN), false);

        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
                helper.absolutePos(HarvesterTests.ORIGIN.west()), Direction.WEST);
        helper.assertTrue(energy != null, "harvester_2 has no energy capability");
        EnergyHandlerUtil.move(new SimpleEnergyHandler(10_000, 10_000, 10_000, 10_000), energy, 10_000, null);
        HarvesterBlockEntity rank2 = helper.getBlockEntity(HarvesterTests.ORIGIN, HarvesterBlockEntity.class);
        rank2.setHeldRecipe(recipe("rank_2_nuggets"));
        HarvesterTests.tick(helper, rank2, 20);
        helper.assertValueEqual(rank2.idleReason(), Optional.empty(), "harvester_2's idle reason");
        helper.assertValueEqual(HarvesterTests.remaining(helper, ore), initial - 1, "the ore block's amount after harvester_2's operation");
        HarvesterTests.tick(helper, rank2, 1);
        helper.assertValueEqual(HarvesterTests.count(helper, chest, Items.IRON_NUGGET), 5, "iron nuggets in the chest: the recipe's result");
        helper.succeed();
    }
}
