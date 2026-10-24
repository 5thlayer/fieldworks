// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.network.HoldRecipePacket;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Held recipe (#10): set on an open Harvester by the packet a recipe viewer's transfer sends, saved with the
 * block, and the only recipe the Harvester then runs. The recipes are the game tests' own datapack's.
 */
final class HeldRecipeTests {

    private static final Identifier IRON_NUGGETS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "gametest/iron_nuggets");

    private HeldRecipeTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_held_recipe_set_from_the_viewer_works_only_the_sources_it_matches_in_a_mixed_footprint", 300,
                HeldRecipeTests::worksOnlyItsSources);
        tests.test("a_held_recipe_matching_nothing_under_the_footprint_idles_and_the_default_recipes_do_not_run", 20,
                HeldRecipeTests::idlesWithNothingMatching);
        tests.test("the_held_recipe_survives_a_save_and_reload_and_still_works_only_its_sources", 20,
                HeldRecipeTests::survivesReload);
        tests.test("the_viewers_transfer_holds_only_a_harvest_recipe_and_only_on_an_open_harvester", 20,
                HeldRecipeTests::holdsOnlyAHarvestRecipe);
        tests.test("the_viewer_lists_every_harvest_recipe_and_each_ore_blocks_default_recipe_unless_replaced", 1,
                HeldRecipeTests::viewerLists);
    }

    /**
     * Iron under one corner, copper under two others: holding the iron nuggets recipe, the Harvester draws the iron
     * for nuggets over and over and never touches the copper, which its Default recipe would draw.
     */
    private static void worksOnlyItsSources(GameTestHelper helper) {
        BlockPos iron = new BlockPos(3, 1, 3);
        BlockPos copper = new BlockPos(5, 1, 5);
        BlockPos copperToo = new BlockPos(3, 1, 5);
        int ironInitial = HarvesterTests.ore(helper, OreResource.IRON, iron, 0);
        int copperInitial = HarvesterTests.ore(helper, OreResource.COPPER, copper, 0);
        HarvesterTests.ore(helper, OreResource.COPPER, copperToo, 0);
        helper.setBlock(HarvesterTests.ORIGIN.below(), Blocks.STONE);
        BlockPos chest = HarvesterTests.ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);
        BlockPos hopper = HarvesterTests.ORIGIN.relative(Direction.WEST, 2);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 4));
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH);

        helper.assertTrue(transfer(helper, player, IRON_NUGGETS), "the transfer did not hold the iron nuggets recipe");

        helper.succeedWhen(() -> {
            helper.assertTrue(HarvesterTests.count(helper, chest, Items.IRON_NUGGET) >= 6, "the iron is not being drawn for nuggets");
            helper.assertTrue(HarvesterTests.remaining(helper, iron) <= ironInitial - 2, "the iron ore block was drawn fewer than twice");
            helper.assertValueEqual(HarvesterTests.remaining(helper, copper), copperInitial, "a copper ore block's amount");
            helper.assertValueEqual(HarvesterTests.remaining(helper, copperToo), copperInitial, "the other copper ore block's amount");
            helper.assertValueEqual(HarvesterTests.count(helper, chest, Items.RAW_COPPER), 0, "raw copper in the chest");
            helper.assertValueEqual(HarvesterTests.count(helper, chest, Items.RAW_IRON), 0, "raw iron in the chest");
        });
    }

    /** Only copper under the footprint and the iron recipe held: fuelled, the Harvester idles and says why. */
    private static void idlesWithNothingMatching(GameTestHelper helper) {
        BlockPos copper = HarvesterTests.ORIGIN.below().east();
        int copperInitial = HarvesterTests.ore(helper, OreResource.COPPER, copper, 0);
        helper.setBlock(HarvesterTests.ORIGIN.below(), Blocks.STONE);
        BlockPos chest = HarvesterTests.ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH);
        feedCoal(helper);
        HarvesterBlockEntity machine = helper.getBlockEntity(HarvesterTests.ORIGIN, HarvesterBlockEntity.class);
        helper.assertTrue(transfer(helper, player, IRON_NUGGETS), "the transfer did not hold the iron nuggets recipe");

        HarvesterTests.tick(helper, machine, 200);

        helper.assertValueEqual(HarvesterTests.remaining(helper, copper), copperInitial, "the copper ore block's amount");
        helper.assertValueEqual(HarvesterTests.count(helper, chest, Items.RAW_COPPER), 0, "raw copper in the chest");
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NOTHING_MATCHING), "the idle reason");
        helper.succeed();
    }

    /**
     * Saved and read back into a fresh block entity, as a chunk reload does, the Harvester still holds the recipe: run
     * by hand, it draws the iron for nuggets and leaves the copper.
     */
    private static void survivesReload(GameTestHelper helper) {
        BlockPos iron = new BlockPos(3, 1, 3);
        BlockPos copper = new BlockPos(5, 1, 5);
        int ironInitial = HarvesterTests.ore(helper, OreResource.IRON, iron, 0);
        int copperInitial = HarvesterTests.ore(helper, OreResource.COPPER, copper, 0);
        helper.setBlock(HarvesterTests.ORIGIN.below(), Blocks.STONE);
        BlockPos chest = HarvesterTests.ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH);
        feedCoal(helper);
        HarvesterBlockEntity machine = helper.getBlockEntity(HarvesterTests.ORIGIN, HarvesterBlockEntity.class);
        helper.assertTrue(transfer(helper, player, IRON_NUGGETS), "the transfer did not hold the iron nuggets recipe");

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        machine.saveCustomOnly(saved);
        HarvesterBlockEntity loaded = new HarvesterBlockEntity(helper.absolutePos(HarvesterTests.ORIGIN), helper.getBlockState(HarvesterTests.ORIGIN));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));

        helper.assertValueEqual(loaded.heldRecipe(), Optional.of(IRON_NUGGETS), "the Held recipe read back");
        HarvesterTests.tick(helper, loaded, 41);
        helper.assertValueEqual(HarvesterTests.remaining(helper, iron), ironInitial - 1, "the iron ore block's amount");
        helper.assertValueEqual(HarvesterTests.remaining(helper, copper), copperInitial, "the copper ore block's amount");
        helper.assertValueEqual(HarvesterTests.count(helper, chest, Items.IRON_NUGGET), 3, "iron nuggets in the chest");
        helper.succeed();
    }

    /**
     * The server holds what the packet names only if it is a Harvest Recipe, and only for a player with a Harvester's
     * menu open: a smelting recipe's id, a Default recipe's (which the viewers list but never send) and a packet with
     * no Harvester open change nothing.
     */
    private static void holdsOnlyAHarvestRecipe(GameTestHelper helper) {
        helper.setBlock(HarvesterTests.ORIGIN.below(), Blocks.STONE);
        FakePlayer player = HarvesterTests.place(helper, Direction.NORTH);
        HarvesterBlockEntity machine = helper.getBlockEntity(HarvesterTests.ORIGIN, HarvesterBlockEntity.class);

        HoldRecipePacket.receive(player, new HoldRecipePacket(IRON_NUGGETS));
        helper.assertValueEqual(machine.heldRecipe(), Optional.empty(), "the Held recipe set with no Harvester open");

        Identifier smelting = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "uranium_ingot_from_raw_uranium_smelting");
        helper.assertFalse(transfer(helper, player, smelting), "a smelting recipe was held");
        Identifier ironDefault = HarvestRecipeView.defaultId(FieldworksRegistries.ore(OreResource.IRON).get());
        helper.assertFalse(transfer(helper, player, ironDefault), "a Default recipe was held");
        helper.assertValueEqual(machine.heldRecipe(), Optional.empty(), "the Held recipe after two refusals");

        helper.assertTrue(transfer(helper, player, IRON_NUGGETS), "the iron nuggets recipe was refused");
        helper.assertValueEqual(machine.heldRecipe(), Optional.of(IRON_NUGGETS), "the Held recipe");
        helper.succeed();
    }

    private static void viewerLists(GameTestHelper helper) {
        List<HarvestRecipeView> views = HarvestRecipeView.all(helper.getLevel().getServer().getRecipeManager().recipeMap());

        HarvestRecipeView nuggets = views.stream().filter(view -> view.id().equals(IRON_NUGGETS)).findFirst()
                .orElseThrow(() -> helper.assertionException("the viewer does not list the iron nuggets recipe"));
        helper.assertFalse(nuggets.isDefault(), "the iron nuggets recipe is listed as a Default recipe");
        helper.assertValueEqual(nuggets.sources(), List.of(FieldworksRegistries.ore(OreResource.IRON).get()), "the recipe's sources");
        helper.assertTrue(ItemStack.matches(nuggets.result(), new ItemStack(Items.IRON_NUGGET, 3)), "the recipe's result is " + nuggets.result());
        helper.assertValueEqual(nuggets.duration(), 40, "the recipe's duration");
        helper.assertValueEqual(nuggets.minimumTier(), Optional.of(TierBlock.HARVESTER_1.id()), "the recipe's minimum tier");

        HarvestRecipeView ironDefault = views.stream()
                .filter(view -> view.isDefault() && view.sources().equals(List.of(FieldworksRegistries.ore(OreResource.IRON).get())))
                .findFirst().orElseThrow(() -> helper.assertionException("the viewer does not list iron ore's Default recipe"));
        helper.assertTrue(ItemStack.matches(ironDefault.result(), new ItemStack(Items.RAW_IRON)), "iron ore's Default result is " + ironDefault.result());
        helper.assertValueEqual(ironDefault.duration(), 20, "iron ore's Default duration");
        helper.assertValueEqual(ironDefault.minimumTier(), Optional.empty(), "iron ore's Default minimum tier");

        helper.assertTrue(views.stream().noneMatch(view -> view.isDefault()
                        && view.sources().contains(FieldworksRegistries.ore(OreResource.URANIUM).get())),
                "the viewer lists uranium ore's Default recipe, which a recipe replaces");
        helper.succeed();
    }

    /**
     * What a viewer's transfer does with a Harvester open: the packet, through its codec as it crosses, handled as the
     * server handles it. The menu is the block entity's own, set by hand: a fake player's {@code openMenu} opens nothing.
     */
    private static boolean transfer(GameTestHelper helper, FakePlayer player, Identifier recipe) {
        HarvesterBlockEntity machine = helper.getBlockEntity(HarvesterTests.ORIGIN, HarvesterBlockEntity.class);
        player.containerMenu = machine.createMenu(1, player.getInventory(), player);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        HoldRecipePacket.STREAM_CODEC.encode(buffer, new HoldRecipePacket(recipe));
        return HoldRecipePacket.receive(player, HoldRecipePacket.STREAM_CODEC.decode(buffer));
    }

    private static void feedCoal(GameTestHelper helper) {
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(HarvesterTests.ORIGIN.west()), Direction.WEST);
        try (Transaction feeding = Transaction.openRoot()) {
            items.insert(ItemResource.of(Items.COAL), 4, feeding);
            feeding.commit();
        }
    }
}
