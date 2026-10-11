// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github._5thlayer.fieldworks.FieldworksTags;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Kind;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.YieldCondition;
import io.github._5thlayer.fieldworks.oil.OilWellBlock;
import io.github._5thlayer.fieldworks.oil.WellYield;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * One Harvest Recipe as a recipe viewer lists it: its source blocks, result, duration at speed ×1, minimum tier
 * (#10) and Drilling Fluid, the fluids that serve and the mB an operation takes (#11). A Default recipe is listed too, though no file writes it, unless a recipe replaces it; only a written
 * recipe can be held.
 *
 * <p>The result is an item stack, or {@code crude} mB for a well (#12). A well's Default recipe lists the crude of a
 * cycle at a normal well and, in {@code crudeAtFloor}, what its yield decays to; a harvestable block's lists no result
 * at all, since it breaks for its loot, which the loot table decides ({@link #breaksForLoot}). A written well recipe
 * lists its {@code yieldCondition}, and one that restores a share of its well's starting yield lists that share in
 * {@code restores} and, in {@code crude}, what it restores a normal well to.
 *
 * <p>EMI and JEI both list from {@link #all}, over the recipes the client was sent, so neither names a Harvester
 * rule of its own.
 */
public record HarvestRecipeView(Identifier id, boolean isDefault, List<Block> sources, ItemStack result, int duration,
        Optional<Identifier> minimumTier, List<Fluid> fluids, int fluidAmount, Optional<YieldCondition> yieldCondition,
        int crude, int crudeAtFloor, double restores) {

    public HarvestRecipeView {
        sources = List.copyOf(sources);
        fluids = List.copyOf(fluids);
    }

    /** Every Default recipe a recipe in {@code recipes} leaves in place, then every Harvest Recipe in it. */
    public static List<HarvestRecipeView> all(RecipeMap recipes) {
        List<RecipeHolder<HarvestRecipe>> written = List.copyOf(recipes.byType(HarvestRecipes.TYPE.get()));
        List<OperationChoice.Recipe<HarvestRecipe.Result>> choices = written.stream()
                .map(holder -> holder.value().forChoice(holder.id().identifier(), tier -> OptionalInt.empty()))
                .toList();
        List<HarvestRecipeView> views = new ArrayList<>();
        for (OreResource resource : OreResource.values()) {
            OreBlock ore = FieldworksRegistries.ore(resource).get();
            Source source = HarvestSources.of(ore.defaultBlockState(), false);
            if (choices.stream().noneMatch(recipe -> recipe.replacesDefaultOf(source))) {
                views.add(new HarvestRecipeView(defaultId(ore), true, List.of(ore), HarvesterBlockEntity.drop(ore),
                        OperationChoice.ORE_DRAW_TICKS, Optional.empty(), List.of(), 0, Optional.empty(), 0, 0, 0));
            }
        }
        OilWellBlock well = FieldworksRegistries.OIL_WELL.get();
        Source wellSource = HarvestSources.of(well.defaultBlockState(), false);
        WellYield yield = WellYield.fromCorpus();
        if (choices.stream().noneMatch(recipe -> recipe.replacesDefaultOf(wellSource))) {
            views.add(new HarvestRecipeView(defaultId(well), true, List.of(well), ItemStack.EMPTY, OperationChoice.WELL_PUMP_TICKS,
                    Optional.empty(), List.of(), 0, Optional.empty(),
                    yield.cycle(yield.normal(), yield.normal(), 0).crude(), yield.cycle(yield.minimum(), yield.minimum(), 0).crude(), 0));
        }
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(FieldworksTags.HARVESTABLE)) {
            Block block = holder.value();
            Source source = HarvestSources.of(block.defaultBlockState(), false);
            // Ore blocks and the well are listed above; a block with no item has nothing to show.
            if (source.kind() != Kind.HARVESTABLE || block.asItem() == Items.AIR) {
                continue;
            }
            if (choices.stream().noneMatch(recipe -> recipe.replacesDefaultOf(source))) {
                views.add(new HarvestRecipeView(defaultId(block), true, List.of(block), ItemStack.EMPTY, OperationChoice.BREAK_TICKS,
                        Optional.empty(), List.of(), 0, Optional.empty(), 0, 0, 0));
            }
        }
        for (RecipeHolder<HarvestRecipe> holder : written) {
            HarvestRecipe recipe = holder.value();
            int crude = switch (recipe.result()) {
                case HarvestRecipe.Result.Crude fixed -> fixed.millibuckets();
                case HarvestRecipe.Result.Restored restored -> yield.restored(yield.normal(), restored.fraction());
                case HarvestRecipe.Result.Item item -> 0;
            };
            views.add(new HarvestRecipeView(holder.id().identifier(), false, recipe.source().stream().map(Holder::value).toList(),
                    recipe.result().stack(), recipe.duration(), recipe.minimumTier(),
                    recipe.drillingFluid().map(input -> servingFluids(input)).orElse(List.of()),
                    recipe.drillingFluid().map(HarvestRecipe.FluidInput::amount).orElse(0), recipe.yieldCondition(),
                    crude, 0, recipe.result() instanceof HarvestRecipe.Result.Restored restored ? restored.fraction() : 0));
        }
        return List.copyOf(views);
    }

    /** Whether the recipe breaks its source for its loot, listing no result of its own: a harvestable block's Default. */
    public boolean breaksForLoot() {
        return result.isEmpty() && crude == 0;
    }

    /**
     * What a viewer draws for each source: its item, or the crude oil bucket for an oil well, which has no item of
     * its own.
     */
    public List<ItemStack> sourceIcons() {
        return sources.stream().map(block -> block instanceof OilWellBlock ? new ItemStack(CrudeOil.BUCKET.get()) : new ItemStack(block)).toList();
    }

    /** The Drilling Fluids a recipe's input names, in their source form: a viewer shows one of each, not its flowing twin. */
    private static List<Fluid> servingFluids(HarvestRecipe.FluidInput input) {
        return input.serving().stream().map(Holder::value).filter(fluid -> fluid.defaultFluidState().isSource()).toList();
    }

    /**
     * The id a viewer lists a block's Default recipe by, which no recipe file can take: EMI's convention for a recipe
     * it is handed rather than read, a path starting with {@code /}.
     */
    public static Identifier defaultId(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "/default/" + blockId.getNamespace() + "/" + blockId.getPath());
    }
}
