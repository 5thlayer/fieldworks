// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester.client;

import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Harvester's screen: a plain panel over the player's inventory, where a recipe viewer's transfer sets the Held
 * recipe (#10). What the Harvester shows of itself is #13's.
 */
public final class HarvesterScreen extends AbstractContainerScreen<HarvesterMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;

    public HarvesterScreen(HarvesterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, HarvesterMenu.INVENTORY_Y + 82);
        inventoryLabelY = HarvesterMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 16, y, SLOT_DARK);
            graphics.fill(x - 1, y, x, y + 16, SLOT_DARK);
            graphics.fill(x, y + 16, x + 17, y + 17, SLOT_LIGHT);
            graphics.fill(x + 16, y, x + 17, y + 16, SLOT_LIGHT);
            graphics.fill(x, y, x + 16, y + 16, SLOT);
        }
    }
}
