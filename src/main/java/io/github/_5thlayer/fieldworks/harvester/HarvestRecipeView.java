// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.block.Block;

/**
 * One Harvest Recipe as a recipe viewer lists it: its source blocks, result, duration at speed ×1 and minimum tier
 * (#10). A Default recipe is listed too, though no file writes it, unless a recipe replaces it; only a written
 * recipe can be held.
 *
 * <p>EMI and JEI both list from {@link #all}, over the recipes the client was sent, so neither names a Harvester
 * rule of its own.
 */
public record HarvestRecipeView(Identifier id, boolean isDefault, List<Block> sources, ItemStack result, int duration,
        Optional<Identifier> minimumTier) {

    public HarvestRecipeView {
        sources = List.copyOf(sources);
    }

    /** Every Default recipe a recipe in {@code recipes} leaves in place, then every Harvest Recipe in it. */
    public static List<HarvestRecipeView> all(RecipeMap recipes) {
        List<RecipeHolder<HarvestRecipe>> written = List.copyOf(recipes.byType(HarvestRecipes.TYPE.get()));
        List<OperationChoice.Recipe> choices = written.stream()
                .map(holder -> holder.value().forChoice(holder.id().identifier(), tier -> OptionalInt.empty()))
                .toList();
        List<HarvestRecipeView> views = new ArrayList<>();
        for (OreResource resource : OreResource.values()) {
            OreBlock ore = FieldworksRegistries.ore(resource).get();
            Source source = Source.oreBlock(BuiltInRegistries.BLOCK.getKey(ore).toString(), HarvesterBlockEntity.tagsOf(ore));
            if (choices.stream().noneMatch(recipe -> recipe.replacesDefaultOf(source))) {
                views.add(new HarvestRecipeView(defaultId(ore), true, List.of(ore), HarvesterBlockEntity.drop(ore),
                        OperationChoice.ORE_DRAW_TICKS, Optional.empty()));
            }
        }
        for (RecipeHolder<HarvestRecipe> holder : written) {
            HarvestRecipe recipe = holder.value();
            views.add(new HarvestRecipeView(holder.id().identifier(), false, recipe.source().stream().map(Holder::value).toList(),
                    recipe.result().create(), recipe.duration(), recipe.minimumTier()));
        }
        return List.copyOf(views);
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
