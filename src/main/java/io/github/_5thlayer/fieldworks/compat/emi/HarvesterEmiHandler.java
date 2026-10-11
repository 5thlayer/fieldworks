// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat.emi;

import java.util.List;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import io.github._5thlayer.fieldworks.compat.HarvestRecipeLines;
import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.github._5thlayer.fieldworks.network.HoldRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * EMI's {@code +} with a Harvester open: it sets the Held recipe and moves no items, so it is lit whatever the
 * inventory holds (#10). A Default recipe is not offered: it runs without being held.
 */
final class HarvesterEmiHandler implements EmiRecipeHandler<HarvesterMenu> {

    /** Built here, not asked for: {@code EmiPlayerInventory.of} dispatches back to this method. */
    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<HarvesterMenu> screen) {
        Player player = Minecraft.getInstance().player;
        return player == null ? new EmiPlayerInventory(List.of()) : new EmiPlayerInventory(player);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe instanceof HarvestingEmiRecipe harvesting && !harvesting.isDefault();
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<HarvesterMenu> context) {
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<HarvesterMenu> context) {
        return List.of(ClientTooltipComponent.create(HarvestRecipeLines.hold().getVisualOrderText()));
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<HarvesterMenu> context) {
        ClientPacketDistributor.sendToServer(new HoldRecipePacket(recipe.getId()));
        return true;
    }
}
