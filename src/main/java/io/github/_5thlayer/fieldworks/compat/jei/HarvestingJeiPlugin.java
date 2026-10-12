// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat.jei;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipes;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.harvester.client.HarvesterScreen;
import io.github._5thlayer.fieldworks.oil.Tar;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Puts {@code fieldworks:harvesting} in JEI, Default recipes and written ones in one tab, with every Harvester as a
 * catalyst, and its {@code +} on an open Harvester setting the Held recipe (#10). Tar and crude oil carry an
 * info page saying how tar is made, since a world interaction is no recipe (#22).
 *
 * <p>JEI finds this by its annotation and loads it only when JEI is installed; nothing outside this package names a
 * JEI type, so the Library loads without it.
 *
 * <p>A Default recipe is no recipe holder, so the tab's type is the view both kinds share. JEI keeps the recipes the
 * server sends ({@link HarvestRecipes}) out of its API, so this keeps its own copy from the same event: JEI listens
 * at the lowest priority and starts after it, so the copy is in place when {@link #registerRecipes} reads it.
 */
@JeiPlugin
public final class HarvestingJeiPlugin implements IModPlugin {

    static final IRecipeType<HarvestRecipeView> TYPE = IRecipeType.create(HarvestRecipes.TYPE.getId(), HarvestRecipeView.class);

    private static final Component TAR_INFO = Component.translatable("info.fieldworks.tar");

    private static volatile RecipeMap received = RecipeMap.EMPTY;

    public HarvestingJeiPlugin() {
        NeoForge.EVENT_BUS.addListener(RecipesReceivedEvent.class, event -> received = event.getRecipeMap());
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new HarvestingJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, HarvestRecipeView.all(received));
        registration.addItemStackInfo(new ItemStack(Tar.ITEM.get()), TAR_INFO);
        addCrudeInfo(registration, registration.getJeiHelpers().getPlatformFluidHelper());
    }

    /**
     * Every crude as a fluid, not its bucket, a bucket each: JEI's fluid type is the platform's, so it is named only
     * through the helper.
     */
    private static <T> void addCrudeInfo(IRecipeRegistration registration, IPlatformFluidHelper<T> fluids) {
        List<T> crude = Tar.crudeSources().stream()
                .map(fluid -> fluids.create(fluid.builtInRegistryHolder(), fluids.bucketVolume())).toList();
        registration.addIngredientInfo(crude, fluids.getFluidIngredientType(), TAR_INFO);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (TierBlock tier : TierBlock.values()) {
            registration.addCraftingStation(TYPE, new ItemStack(Harvesters.item(tier).get()));
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(HarvesterScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    IClickableIngredientFactory factory, HarvesterScreen screen, double mouseX, double mouseY) {
                return screen.ghostAt(mouseX, mouseY).flatMap(ghost -> factory.createBuilder(ghost.stack())
                        .buildWithArea(ghost.x(), ghost.y(), 16, 16));
            }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new HarvesterTransferHandler(registration.getTransferHelper()), TYPE);
    }
}
