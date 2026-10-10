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
    public static final int WIDTH = 120;
    public static final int HEIGHT = 44;

    private HarvestRecipeLines() {
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

    /** Why a viewer's transfer is not offered on a Default recipe. */
    public static Component defaultNotHeld() {
        return Component.translatable("fieldworks.harvesting.default_not_held");
    }

    public static Component hold() {
        return Component.translatable("fieldworks.harvesting.hold");
    }
}
