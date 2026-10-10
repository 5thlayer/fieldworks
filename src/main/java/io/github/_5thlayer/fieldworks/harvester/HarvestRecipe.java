// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A Harvest Recipe as the recipe manager holds it: the {@code fieldworks:harvesting} type (GLOSSARY.md). It
 * draws one unit from a source block, or from any block of a block tag, and yields {@code result} in
 * {@code duration} ticks at speed x1, which a tier's speed divides.
 *
 * <p>{@code minimum_tier} names a Harvester block, compared by the tier's rank (ADR 0005), and
 * {@code replaces_default}, false when omitted, removes its sources' Default recipes (a fluid-only resource).
 *
 * <p>A result is a template, not a stack: {@code ItemStack.CODEC} refuses an item whose components are not
 * bound yet, which they are not during the datapack load that reads recipes.
 *
 * <p>{@link #matches} is false: nothing looks one up by its inputs. A Harvester reads them by id.
 */
public record HarvestRecipe(HolderSet<Block> source, ItemStackTemplate result, int duration,
        Optional<Identifier> minimumTier, boolean replacesDefault) implements Recipe<RecipeInput> {

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return result.create();
    }

    /** A Harvest Recipe has no item ingredient, so it is special: vanilla warns at each load of one that isn't. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<HarvestRecipe> getSerializer() {
        return HarvestRecipes.SERIALIZER.get();
    }

    @Override
    public RecipeType<HarvestRecipe> getType() {
        return HarvestRecipes.TYPE.get();
    }

    /** The recipe as the choice of operation reads it: plain ids, and the minimum tier as the rank it needs. */
    OperationChoice.Recipe forChoice(Identifier id, Function<Identifier, OptionalInt> rankOf) {
        Set<String> blocks = source.unwrap().map(tag -> Set.<String>of(), holders -> {
            List<String> ids = new ArrayList<>();
            holders.forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.add(key.identifier().toString())));
            return Set.copyOf(ids);
        });
        Set<String> tags = source.unwrap().map(tag -> Set.of(tag.location().toString()), holders -> Set.<String>of());
        // A minimum tier whose file is missing can be met by no rank, so its recipe never runs.
        OptionalInt minimum = minimumTier.map(tier -> OptionalInt.of(rankOf.apply(tier).orElse(Integer.MAX_VALUE)))
                .orElse(OptionalInt.empty());
        return new OperationChoice.Recipe(id.toString(), blocks, tags, duration, minimum, replacesDefault);
    }

    private static DataResult<HarvestRecipe> check(HarvestRecipe recipe) {
        if (recipe.duration < 1) {
            return DataResult.error(() -> "a Harvest Recipe's duration must be at least 1 tick, not " + recipe.duration);
        }
        if (recipe.source.unwrap().right().filter(List::isEmpty).isPresent()) {
            return DataResult.error(() -> "a Harvest Recipe's source must name at least one block");
        }
        if (recipe.minimumTier.isPresent() && Stream.of(TierBlock.values()).noneMatch(tier -> tier.id().equals(recipe.minimumTier.get()))) {
            return DataResult.error(() -> "a Harvest Recipe's minimum_tier must be a Harvester block, one of "
                    + Stream.of(TierBlock.values()).map(tier -> tier.id().toString()).toList() + ", not " + recipe.minimumTier.get());
        }
        return DataResult.success(recipe);
    }

    static final MapCodec<HarvestRecipe> CODEC = RecordCodecBuilder.<HarvestRecipe>mapCodec(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("source").forGetter(HarvestRecipe::source),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(HarvestRecipe::result),
            Codec.INT.fieldOf("duration").forGetter(HarvestRecipe::duration),
            Identifier.CODEC.optionalFieldOf("minimum_tier").forGetter(HarvestRecipe::minimumTier),
            Codec.BOOL.optionalFieldOf("replaces_default", false).forGetter(HarvestRecipe::replacesDefault)
    ).apply(instance, HarvestRecipe::new)).flatXmap(HarvestRecipe::check, HarvestRecipe::check);

    static final StreamCodec<RegistryFriendlyByteBuf, HarvestRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderSet(Registries.BLOCK), HarvestRecipe::source,
            ItemStackTemplate.STREAM_CODEC, HarvestRecipe::result,
            ByteBufCodecs.VAR_INT, HarvestRecipe::duration,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), HarvestRecipe::minimumTier,
            ByteBufCodecs.BOOL, HarvestRecipe::replacesDefault,
            HarvestRecipe::new);

    static RecipeSerializer<HarvestRecipe> serializer() {
        return new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }
}
