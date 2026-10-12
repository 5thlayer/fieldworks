// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiWorldInteractionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipes;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.harvester.client.HarvesterScreen;
import io.github._5thlayer.fieldworks.oil.Tar;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Puts {@code fieldworks:harvesting} in EMI, Default recipes and written ones in one tab, with every Harvester as a
 * workstation, and its {@code +} on an open Harvester setting the Held recipe (#10). Crude meeting lava shows as a
 * world interaction making tar, with the lava left in place (#22).
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing outside this package names an
 * EMI type, so the Library loads without it. The recipes are the ones the server sends ({@link HarvestRecipes}).
 */
@EmiEntrypoint
public final class HarvestingEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory category = new EmiRecipeCategory(HarvestRecipes.TYPE.getId(),
                EmiStack.of(Harvesters.item(TierBlock.HARVESTER_1).get()));
        registry.addCategory(category);
        for (TierBlock tier : TierBlock.values()) {
            registry.addWorkstation(category, EmiStack.of(Harvesters.item(tier).get()));
        }
        for (HarvestRecipeView view : HarvestRecipeView.all(registry.getRecipeMap())) {
            registry.addRecipe(new HarvestingEmiRecipe(category, view));
        }
        registry.addRecipeHandler(Harvesters.MENU.get(), new HarvesterEmiHandler());
        registry.addStackProvider(HarvesterScreen.class, (screen, x, y) -> screen.ghostAt(x, y)
                .map(ghost -> new EmiStackInteraction(EmiStack.of(ghost.stack()))).orElse(EmiStackInteraction.EMPTY));
        registry.addRecipe(EmiWorldInteractionRecipe.builder()
                .leftInput(EmiIngredient.of(Tar.crudeSources().stream()
                        .map(fluid -> EmiStack.of(fluid, FluidType.BUCKET_VOLUME)).toList()))
                .rightInput(EmiStack.of(Fluids.LAVA, FluidType.BUCKET_VOLUME), true)
                .output(EmiStack.of(Tar.ITEM.get()))
                .id(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "/world/fluid_interaction/tar"))
                .build());
    }

}
