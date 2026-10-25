// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A shapeless craft that leaves nothing in the grid, {@code "type": "fieldworks:crafting_shapeless_no_remainder"},
 * written like vanilla's {@code minecraft:crafting_shapeless}. It is for a craft whose result is made out of its
 * container: the magma slurry bucket is the lava bucket refilled, so the craft gives no empty bucket back
 * (fieldworks#47). It is still a {@link ShapelessRecipe}, so whatever reads one, a recipe viewer or Craftworks,
 * reads this.
 */
public final class NoRemainderShapelessRecipe extends ShapelessRecipe {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Fieldworks.MOD_ID);

    private static final MapCodec<NoRemainderShapelessRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(ShapelessRecipe::result),
                    Codec.lazyInitialized(() -> Ingredient.CODEC.listOf(1, ShapedRecipePattern.getMaxHeight() * ShapedRecipePattern.getMaxWidth()))
                            .fieldOf("ingredients").forGetter(o -> o.ingredients))
            .apply(i, NoRemainderShapelessRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, NoRemainderShapelessRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
            ItemStackTemplate.STREAM_CODEC, ShapelessRecipe::result,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
            NoRemainderShapelessRecipe::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<NoRemainderShapelessRecipe>> SERIALIZER =
            SERIALIZERS.register("crafting_shapeless_no_remainder", () -> new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

    /** Kept for the codecs: vanilla's own list is private. */
    private final List<Ingredient> ingredients;

    private NoRemainderShapelessRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ItemStackTemplate result, List<Ingredient> ingredients) {
        super(commonInfo, bookInfo, result, ingredients);
        this.ingredients = ingredients;
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @Override
    public RecipeSerializer<ShapelessRecipe> getSerializer() {
        // Vanilla's return type forces the cast; the serializer only ever encodes this class.
        @SuppressWarnings("unchecked")
        RecipeSerializer<ShapelessRecipe> serializer = (RecipeSerializer<ShapelessRecipe>) (RecipeSerializer<?>) SERIALIZER.get();
        return serializer;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }
}
