// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github._5thlayer.fieldworks.compat.HarvestRecipeLines;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;

/** One Harvest Recipe, written or Default, as EMI draws it: the source blocks, an arrow timed to it, the result. */
final class HarvestingEmiRecipe extends BasicEmiRecipe {

    private final HarvestRecipeView view;

    /** EMI keys the recipe by the view's id, the recipe's own for a written one, which the transfer sends. */
    HarvestingEmiRecipe(EmiRecipeCategory category, HarvestRecipeView view) {
        super(category, view.id(), HarvestRecipeLines.WIDTH, HarvestRecipeLines.HEIGHT);
        this.view = view;
        inputs.add(EmiIngredient.of(view.sources().stream().map(EmiStack::of).toList()));
        if (!view.fluids().isEmpty()) {
            inputs.add(EmiIngredient.of(view.fluids().stream().map(fluid -> (EmiIngredient) EmiStack.of(fluid, view.fluidAmount())).toList()));
        }
        outputs.add(EmiStack.of(view.result()));
    }

    boolean isDefault() {
        return view.isDefault();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int shift = HarvestRecipeLines.shift(view);
        widgets.addSlot(inputs.get(0), 0, 4);
        if (inputs.size() > 1) {
            widgets.addSlot(inputs.get(1), HarvestRecipeLines.FLUID_SHIFT, 4);
        }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 24 + shift, 5);
        widgets.addFillingArrow(24 + shift, 5, Math.max(view.duration(), 1) * 50);
        widgets.addSlot(outputs.get(0), 54 + shift, 4).recipeContext(this);
        widgets.addText(HarvestRecipeLines.seconds(view), 0, 24, 0xFF808080, false);
        widgets.addText(HarvestRecipeLines.note(view), 0, 35, 0xFF808080, false);
    }
}
