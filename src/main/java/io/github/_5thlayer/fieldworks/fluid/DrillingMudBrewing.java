// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Drilling mud brewed in a vanilla brewing stand (#11): each bottle in the stand becomes a mud bottle. Vanilla builds
 * its brewing table in code, so each pairing is added through {@link RegisterBrewingRecipesEvent}; the EMI plugin
 * shows the same pairings (#29).
 */
public final class DrillingMudBrewing {

    /** Each bottle a brewing stand turns into a mud bottle, and what it is brewed with. */
    public static final List<Pairing> PAIRINGS = List.of(
            new Pairing(Potions.WATER, Items.CLAY_BALL),
            new Pairing(Potions.WATER, Items.SLIME_BALL));

    private DrillingMudBrewing() {
    }

    static void register() {
        // A game bus event, posted on the server and again on a client's login.
        NeoForge.EVENT_BUS.addListener(DrillingMudBrewing::registerBrewing);
    }

    private static void registerBrewing(RegisterBrewingRecipesEvent event) {
        for (Pairing pairing : PAIRINGS) {
            event.getBuilder().addRecipe(pairing.bottleIngredient(), Ingredient.of(pairing.ingredient()),
                    new ItemStack(DrillingMud.BOTTLE.get()));
        }
    }

    /** A bottle of {@code bottle} brewed with {@code ingredient}. */
    public record Pairing(Holder<Potion> bottle, Item ingredient) {

        /** The bottle as it stands in a brewing stand's bottle slot. */
        public ItemStack bottleStack() {
            return PotionContents.createItemStack(Items.POTION, bottle);
        }

        Ingredient bottleIngredient() {
            return DataComponentIngredient.of(DataComponents.POTION_CONTENTS, new PotionContents(bottle), Items.POTION);
        }
    }
}
