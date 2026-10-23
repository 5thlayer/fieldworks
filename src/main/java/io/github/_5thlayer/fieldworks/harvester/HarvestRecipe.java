// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.fieldworks.FieldworksTags;
import io.github._5thlayer.fieldworks.oil.OilWellBlock;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.material.Fluid;

/**
 * A Harvest Recipe as the recipe manager holds it: the {@code fieldworks:harvesting} type (GLOSSARY.md). It
 * draws one unit from a source block, or from any block of a block tag, and yields {@code result} in
 * {@code duration} ticks at speed x1, which a tier's speed divides.
 *
 * <p>{@code drilling_fluid}, optional, is a fluid, fluid tag or list of fluids and the mB an operation takes of it
 * from the Harvester's Drilling Fluid tank; only a Held recipe takes it (#11).
 *
 * <p>{@code result} is an item stack, or for an oil well {@code {"crude_oil": <mB>}}, the crude one operation pumps
 * in place of the well's own cycle's yield, or {@code {"crude_oil": "cycle"}}, the well's own cycle's yield (the cycle
 * takes its step off the well either way, ADR 0002). A well's floor is final: no recipe lifts a well above it
 * (ADR 0081).
 *
 * <p>{@code minimum_tier} names a Harvester block, compared by the tier's rank (ADR 0005), and
 * {@code replaces_default}, false when omitted, removes its sources' Default recipes (a fluid-only resource).
 *
 * <p>A result is a template, not a stack: {@code ItemStack.CODEC} refuses an item whose components are not
 * bound yet, which they are not during the datapack load that reads recipes.
 *
 * <p>{@link #matches} is false: nothing looks one up by its inputs. A Harvester reads them by id.
 */
public record HarvestRecipe(HolderSet<Block> source, Result result, int duration,
        Optional<Identifier> minimumTier, boolean replacesDefault, Optional<FluidInput> drillingFluid)
        implements Recipe<RecipeInput> {

    /** What one operation yields: an item stack, or crude oil for a well, fixed or the well's own cycle's. */
    public sealed interface Result {

        /** Crude is an object, {@code {"crude_oil": 10}} or {@code {"crude_oil": "cycle"}}; anything else is read as an item stack. */
        Codec<Result> CODEC = Codec.either(Codec.either(Crude.CODEC, Cycle.CODEC), ItemStackTemplate.CODEC).xmap(
                either -> either.map(crude -> crude.map(fixed -> fixed, cycle -> cycle), Item::new),
                Result::toEither);

        StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = ByteBufCodecs.either(
                ByteBufCodecs.either(Crude.STREAM_CODEC, Cycle.STREAM_CODEC), ItemStackTemplate.STREAM_CODEC).map(
                either -> either.map(crude -> crude.map(fixed -> fixed, cycle -> cycle), Item::new),
                Result::toEither);

        private static Either<Either<Crude, Cycle>, ItemStackTemplate> toEither(Result result) {
            return switch (result) {
                case Crude crude -> Either.left(Either.left(crude));
                case Cycle cycle -> Either.left(Either.right(cycle));
                case Item item -> Either.right(item.template());
            };
        }

        /** The item stack, or an empty stack for crude. */
        default ItemStack stack() {
            return this instanceof Item item ? item.template().create() : ItemStack.EMPTY;
        }

        record Item(ItemStackTemplate template) implements Result {
        }

        /** The crude one operation pumps, in mB, in place of the well's own cycle's yield. */
        record Crude(int millibuckets) implements Result {

            private static final Codec<Crude> CODEC = Codec.INT.fieldOf("crude_oil").codec().xmap(Crude::new, Crude::millibuckets);

            private static final StreamCodec<ByteBuf, Crude> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Crude::new, Crude::millibuckets);
        }

        /** The crude of the well's own cycle, which decays to its floor and no further (ADR 0081). */
        record Cycle() implements Result {

            private static final String NAME = "cycle";

            private static final Codec<Cycle> CODEC = Codec.STRING.fieldOf("crude_oil").codec().comapFlatMap(
                    name -> NAME.equals(name) ? DataResult.success(new Cycle())
                            : DataResult.error(() -> "a Harvest Recipe's crude_oil must be a number of mB or \"cycle\", not " + name),
                    cycle -> NAME);

            private static final StreamCodec<ByteBuf, Cycle> STREAM_CODEC = StreamCodec.unit(new Cycle());
        }
    }

    /**
     * The Drilling Fluid a recipe consumes: a fluid, a fluid tag or a list of fluids, and the mB one operation
     * takes. Only the fluids that are in {@code #fieldworks:drilling_fluids} serve; the tag is read when the recipe
     * is used, since a recipe loads before the tags it names are bound, so one naming none of them never runs.
     */
    public record FluidInput(HolderSet<Fluid> fluids, int amount) {

        static final Codec<FluidInput> CODEC = RecordCodecBuilder.<FluidInput>create(instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.FLUID).fieldOf("fluid").forGetter(FluidInput::fluids),
                Codec.INT.fieldOf("amount").forGetter(FluidInput::amount)
        ).apply(instance, FluidInput::new)).validate(FluidInput::check);

        static final StreamCodec<RegistryFriendlyByteBuf, FluidInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderSet(Registries.FLUID), FluidInput::fluids,
                ByteBufCodecs.VAR_INT, FluidInput::amount,
                FluidInput::new);

        private static DataResult<FluidInput> check(FluidInput input) {
            if (input.amount < 1) {
                return DataResult.error(() -> "a Harvest Recipe's drilling_fluid amount must be at least 1 mB, not " + input.amount);
            }
            if (input.fluids.unwrap().right().filter(List::isEmpty).isPresent()) {
                return DataResult.error(() -> "a Harvest Recipe's drilling_fluid must name at least one fluid");
            }
            return DataResult.success(input);
        }

        /** The fluids that serve, by id: those named that are in {@code #fieldworks:drilling_fluids}. */
        public List<Holder<Fluid>> serving() {
            return fluids.stream().filter(fluid -> fluid.is(FieldworksTags.DRILLING_FLUIDS)).toList();
        }
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return result.stack();
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
    OperationChoice.Recipe<Result> forChoice(Identifier id, Function<Identifier, OptionalInt> rankOf) {
        Set<String> blocks = source.unwrap().map(tag -> Set.<String>of(), holders -> {
            List<String> ids = new ArrayList<>();
            holders.forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.add(key.identifier().toString())));
            return Set.copyOf(ids);
        });
        Set<String> tags = source.unwrap().map(tag -> Set.of(tag.location().toString()), holders -> Set.<String>of());
        // A minimum tier whose file is missing can be met by no rank, so its recipe never runs.
        OptionalInt minimum = minimumTier.map(tier -> OptionalInt.of(rankOf.apply(tier).orElse(Integer.MAX_VALUE)))
                .orElse(OptionalInt.empty());
        Optional<OperationChoice.DrillingFluid> fluid = drillingFluid.map(input -> new OperationChoice.DrillingFluid(
                input.serving().stream().flatMap(holder -> holder.unwrapKey().stream()).map(key -> key.identifier().toString())
                        .collect(Collectors.toUnmodifiableSet()), input.amount()));
        return new OperationChoice.Recipe<>(id.toString(), blocks, tags, duration, minimum, replacesDefault, fluid, result);
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
        if (recipe.result instanceof Result.Crude crude && crude.millibuckets() < 1) {
            return DataResult.error(() -> "a Harvest Recipe's crude_oil result must be at least 1 mB, not " + crude.millibuckets());
        }
        // A block tag is read after the load, so only a source named block by block can be checked here (#12).
        List<Holder<Block>> named = recipe.source.unwrap().right().orElse(List.of());
        boolean wells = named.stream().anyMatch(block -> block.value() instanceof OilWellBlock);
        boolean others = named.stream().anyMatch(block -> !(block.value() instanceof OilWellBlock));
        if (!(recipe.result instanceof Result.Item) && others) {
            return DataResult.error(() -> "a Harvest Recipe's crude result is for an oil well, and its source names another block");
        }
        if (recipe.result instanceof Result.Item && wells) {
            return DataResult.error(() -> "a Harvest Recipe's source names an oil well, which yields crude_oil, not an item");
        }
        return DataResult.success(recipe);
    }

    static final MapCodec<HarvestRecipe> CODEC = RecordCodecBuilder.<HarvestRecipe>mapCodec(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("source").forGetter(HarvestRecipe::source),
            Result.CODEC.fieldOf("result").forGetter(HarvestRecipe::result),
            Codec.INT.fieldOf("duration").forGetter(HarvestRecipe::duration),
            Identifier.CODEC.optionalFieldOf("minimum_tier").forGetter(HarvestRecipe::minimumTier),
            Codec.BOOL.optionalFieldOf("replaces_default", false).forGetter(HarvestRecipe::replacesDefault),
            FluidInput.CODEC.optionalFieldOf("drilling_fluid").forGetter(HarvestRecipe::drillingFluid)
    ).apply(instance, HarvestRecipe::new)).flatXmap(HarvestRecipe::check, HarvestRecipe::check);

    static final StreamCodec<RegistryFriendlyByteBuf, HarvestRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderSet(Registries.BLOCK), HarvestRecipe::source,
            Result.STREAM_CODEC, HarvestRecipe::result,
            ByteBufCodecs.VAR_INT, HarvestRecipe::duration,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), HarvestRecipe::minimumTier,
            ByteBufCodecs.BOOL, HarvestRecipe::replacesDefault,
            ByteBufCodecs.optional(FluidInput.STREAM_CODEC), HarvestRecipe::drillingFluid,
            HarvestRecipe::new);

    static RecipeSerializer<HarvestRecipe> serializer() {
        return new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }
}
