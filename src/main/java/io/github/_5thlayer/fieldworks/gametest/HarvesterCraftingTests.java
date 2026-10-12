// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Both Harvesters are crafted at a crafting table (#24), from vanilla items and the tier below, on the solo run
 * where only Groundworks is beside the Library.
 */
final class HarvesterCraftingTests {

    private HarvesterCraftingTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_crafting_table_makes_a_burner_harvester_from_iron_cobblestone_a_furnace_and_an_iron_pickaxe", 1,
                HarvesterCraftingTests::harvester1);
        tests.test("a_crafting_table_makes_an_electric_harvester_from_a_burner_harvester_copper_iron_and_redstone", 1,
                HarvesterCraftingTests::harvester2);
    }

    private static void harvester1(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        crafts(helper, TierBlock.HARVESTER_1, List.of(
                iron, iron, iron,
                cobble, new ItemStack(Items.FURNACE), cobble,
                cobble, new ItemStack(Items.IRON_PICKAXE), cobble));
    }

    private static void harvester2(GameTestHelper helper) {
        ItemStack copper = new ItemStack(Items.COPPER_INGOT);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        crafts(helper, TierBlock.HARVESTER_2, List.of(
                copper, redstone, copper,
                iron, new ItemStack(Harvesters.item(TierBlock.HARVESTER_1).get()), iron,
                copper, redstone, copper));
    }

    private static void crafts(GameTestHelper helper, TierBlock tier, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "no crafting recipe takes the grid for " + tier.id());
        ItemStack result = recipe.get().value().assemble(input);
        helper.assertTrue(result.is(Harvesters.item(tier).get()), "the grid for " + tier.id() + " crafts " + result);
        helper.succeed();
    }
}
