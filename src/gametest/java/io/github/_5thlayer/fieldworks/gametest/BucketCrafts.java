// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.Fieldworks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/** The checks a Drilling Fluid's craft by the bucket shares (ADR 0012), magma slurry's and drilling mud's alike. */
final class BucketCrafts {

    private BucketCrafts() {
    }

    /**
     * {@code base} and {@code ingredient}, in either order, craft one {@code result}, and the base bucket becomes it,
     * so the craft leaves no empty bucket in the grid, where vanilla's remainder would leave one: one bucket in, one
     * out (fieldworks#47). {@code base} with {@code wrong} crafts nothing.
     */
    static void craftsLeavingNothing(GameTestHelper helper, Item base, Item ingredient, Item wrong, Item result) {
        ItemStack bucket = new ItemStack(base);
        ItemStack other = new ItemStack(ingredient);
        for (List<ItemStack> grid : List.of(List.of(bucket, other), List.of(other, bucket))) {
            CraftingInput input = CraftingInput.of(2, 1, grid.stream().map(ItemStack::copy).toList());
            Optional<RecipeHolder<CraftingRecipe>> recipe = recipeFor(helper, input);
            helper.assertTrue(recipe.isPresent(), "no crafting recipe takes " + bucket + " and " + other);
            ItemStack crafted = recipe.get().value().assemble(input);
            helper.assertTrue(crafted.is(result) && crafted.getCount() == 1, "they craft " + crafted);
            List<ItemStack> remaining = recipe.get().value().getRemainingItems(input).stream()
                    .filter(stack -> !stack.isEmpty()).toList();
            helper.assertTrue(remaining.isEmpty(), "the craft leaves " + remaining);
        }
        CraftingInput wrongInput = CraftingInput.of(2, 1, List.of(bucket.copy(), new ItemStack(wrong)));
        helper.assertTrue(recipeFor(helper, wrongInput).isEmpty(), wrong + " crafts something with " + bucket);
    }

    /** No {@code <fluid>_bottle} item is registered: a Drilling Fluid comes in a bucket only. */
    static void hasNoBottle(GameTestHelper helper, String fluid) {
        helper.assertTrue(BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, fluid + "_bottle")).isEmpty(),
                fluid + " has a bottle");
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipeFor(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }
}
