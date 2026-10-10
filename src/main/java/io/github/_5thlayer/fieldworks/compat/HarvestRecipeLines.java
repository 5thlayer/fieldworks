// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat;

import java.util.Locale;

import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** The lines of text EMI and JEI both draw under a Harvest Recipe: its duration, and its minimum tier or that it is a Default. */
public final class HarvestRecipeLines {

    /** A recipe's width in a viewer: source, arrow, result, and room for its lines. */
    public static final int WIDTH = 140;
    public static final int HEIGHT = 56;

    /** How far a recipe with a Drilling Fluid moves its arrow and result right, to make room for the fluid's slot. */
    public static final int FLUID_SHIFT = 20;

    private HarvestRecipeLines() {
    }

    public static int shift(HarvestRecipeView view) {
        return view.fluids().isEmpty() ? 0 : FLUID_SHIFT;
    }

    public static Component seconds(HarvestRecipeView view) {
        return Component.translatable("fieldworks.harvesting.seconds", String.format(Locale.ROOT, "%.1f", view.duration() / 20F));
    }

    /** The tier it needs by its block's name, "Default recipe" for a Default, or nothing for a written recipe any tier runs. */
    public static Component note(HarvestRecipeView view) {
        if (view.isDefault()) {
            return Component.translatable("fieldworks.harvesting.default");
        }
        return view.minimumTier()
                .map(tier -> (Component) Component.translatable("fieldworks.harvesting.minimum_tier", BuiltInRegistries.BLOCK.getValue(tier).getName()))
                .orElse(Component.empty());
    }

    /**
     * A third line for what the first two do not say (#12): a well Default's crude falling to its floor, a harvestable
     * block breaking for its loot, or the floor state a well recipe needs. Empty for any other recipe.
     */
    public static Component detail(HarvestRecipeView view) {
        if (view.crudeAtFloor() > 0) {
            return Component.translatable("fieldworks.harvesting.falls_to", view.crudeAtFloor());
        }
        if (view.breaksForLoot()) {
            return Component.translatable("fieldworks.harvesting.loot");
        }
        return view.yieldCondition().map(condition -> (Component) Component.translatable("fieldworks.harvesting." + condition.id()))
                .orElse(Component.empty());
    }

    /** Why a viewer's transfer is not offered on a Default recipe. */
    public static Component defaultNotHeld() {
        return Component.translatable("fieldworks.harvesting.default_not_held");
    }

    public static Component hold() {
        return Component.translatable("fieldworks.harvesting.hold");
    }
}
