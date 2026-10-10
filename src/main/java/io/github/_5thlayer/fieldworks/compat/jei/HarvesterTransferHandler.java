// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat.jei;

import java.util.Optional;

import io.github._5thlayer.fieldworks.compat.HarvestRecipeLines;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipeView;
import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.network.HoldRecipePacket;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * JEI's {@code +} on a Harvest Recipe with a Harvester open: it sets the Held recipe and moves no items (#10). On a
 * Default recipe it is greyed, with why: a Default runs without being held.
 */
final class HarvesterTransferHandler implements IRecipeTransferHandler<HarvesterMenu, HarvestRecipeView> {

    private final IRecipeTransferHandlerHelper helper;

    HarvesterTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends HarvesterMenu> getContainerClass() {
        return HarvesterMenu.class;
    }

    @Override
    public Optional<MenuType<HarvesterMenu>> getMenuType() {
        return Optional.of(Harvesters.MENU.get());
    }

    @Override
    public IRecipeType<HarvestRecipeView> getRecipeType() {
        return HarvestingJeiPlugin.TYPE;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(HarvesterMenu menu, HarvestRecipeView view, IRecipeSlotsView slots,
            Player player, boolean maxTransfer, boolean doTransfer) {
        if (view.isDefault()) {
            return helper.createUserErrorWithTooltip(HarvestRecipeLines.defaultNotHeld());
        }
        if (doTransfer) {
            ClientPacketDistributor.sendToServer(new HoldRecipePacket(view.id()));
        }
        return null;
    }
}
