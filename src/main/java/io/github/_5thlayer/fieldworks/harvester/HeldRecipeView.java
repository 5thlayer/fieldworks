// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The Held recipe as a menu or a Jade tooltip shows it (#13): its id and what it makes, empty when the recipe's result
 * is not an item. The client has no Harvest Recipe to read this from at the time it is drawn, so the server sends it.
 */
public record HeldRecipeView(Identifier id, ItemStack result) {

    public static final StreamCodec<RegistryFriendlyByteBuf, HeldRecipeView> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, HeldRecipeView::id,
            ItemStack.OPTIONAL_STREAM_CODEC, HeldRecipeView::result,
            HeldRecipeView::new);
}
