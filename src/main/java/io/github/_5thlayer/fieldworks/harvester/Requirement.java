// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.Tags;

/**
 * What a source needs once a Harvest Recipe has taken away its Default recipe (ADR 0010): the one wording the action bar
 * shows a refused hand break and Jade shows under the amount. It is decided on the server, which alone holds the
 * recipes.
 */
public final class Requirement {

    private Requirement() {
    }

    /** What the block in {@code state} needs, or empty while its Default recipe stands. */
    public static Optional<Component> of(ServerLevel level, BlockState state) {
        return replacing(level, state).map(Requirement::of);
    }

    /** The loaded Harvest Recipe that replaces the Default recipe of the block in {@code state}, if any. */
    public static Optional<HarvestRecipe> replacing(ServerLevel level, BlockState state) {
        return OperationChoice.replacingDefaultOf(HarvestRecipes.loaded(level).choices(), HarvestSources.of(state))
                .flatMap(choice -> HarvestRecipes.find(level, Identifier.parse(choice.id())));
    }

    /**
     * "Needs {tier} and {fluid}", "Needs {tier}", and with no minimum tier "a Harvester" in place of the tier. A fluid
     * tag is named by the tag, so a pack adding a fluid to it doesn't change the text; a single fluid by its own name.
     */
    public static Component of(HarvestRecipe recipe) {
        Component tier = recipe.minimumTier()
                .<Component>map(id -> Component.translatable("fieldworks.requirement.the",
                        Component.translatable(id.toLanguageKey("block"))))
                .orElseGet(() -> Component.translatable("fieldworks.requirement.a_harvester"));
        return recipe.drillingFluid()
                .map(input -> Component.translatable("fieldworks.requirement.with_fluid", tier, fluidName(input)))
                .orElseGet(() -> Component.translatable("fieldworks.requirement", tier));
    }

    private static Component fluidName(HarvestRecipe.FluidInput input) {
        return input.fluids().unwrap().map(
                tag -> Component.translatable(Tags.getTagTranslationKey(tag)),
                fluids -> ComponentUtils.formatList(fluids.stream().map(Holder::value).toList(), Requirement::nameOf));
    }

    private static Component nameOf(Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }
}
