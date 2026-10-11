// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat.jei;

import io.github._5thlayer.fieldworks.compat.HarvestRecipeLines;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** One Harvest Recipe, written or Default, as JEI draws it, laid out as in EMI: the source blocks, a timed arrow, the result. */
final class HarvestingJeiCategory implements IRecipeCategory<HarvestRecipeView> {

    private final IDrawable icon;

    HarvestingJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(Harvesters.item(TierBlock.HARVESTER_1).get());
    }

    @Override
    public IRecipeType<HarvestRecipeView> getRecipeType() {
        return HarvestingJeiPlugin.TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("emi.category.fieldworks.harvesting");
    }

    @Override
    public int getWidth() {
        return HarvestRecipeLines.WIDTH;
    }

    @Override
    public int getHeight() {
        return HarvestRecipeLines.HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HarvestRecipeView view, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 5)
                .setStandardSlotBackground()
                .addItemStacks(view.sourceIcons());
        int shift = HarvestRecipeLines.shift(view);
        if (!view.fluids().isEmpty()) {
            IRecipeSlotBuilder fluid = builder.addSlot(RecipeIngredientRole.INPUT, 1 + HarvestRecipeLines.FLUID_SHIFT, 5)
                    .setStandardSlotBackground();
            view.fluids().forEach(each -> fluid.addFluidStack(each, view.fluidAmount()));
        }
        IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, 55 + shift, 5).setOutputSlotBackground();
        // Crude for a well, in mB; a harvestable block's loot is the loot table's, so it lists none (#12).
        if (view.crude() > 0) {
            output.addFluidStack(CrudeOil.SOURCE.get(), view.crude());
        } else if (!view.breaksForLoot()) {
            output.add(view.result());
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, HarvestRecipeView view, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(Math.max(view.duration(), 1)).setPosition(24 + HarvestRecipeLines.shift(view), 5);
        builder.addText(HarvestRecipeLines.seconds(view), getWidth(), 10).setPosition(0, 24).setColor(0xFF808080);
        builder.addText(HarvestRecipeLines.note(view), getWidth(), 10).setPosition(0, 34).setColor(0xFF808080);
        builder.addText(HarvestRecipeLines.detail(view), getWidth(), 10).setPosition(0, 45).setColor(0xFF808080);
        builder.addText(HarvestRecipeLines.restores(view), getWidth(), 10).setPosition(0, 56).setColor(0xFF808080);
    }

    @Override
    public @Nullable Identifier getIdentifier(HarvestRecipeView view) {
        return view.id();
    }
}
