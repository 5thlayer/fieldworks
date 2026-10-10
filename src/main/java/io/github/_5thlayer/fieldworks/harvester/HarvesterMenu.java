// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester's menu: what a recipe viewer's transfer targets to set the Held recipe (#10). It holds only the
 * player's inventory for now; the Harvester's own slots, power, tanks and idle reason are #13's.
 */
public final class HarvesterMenu extends AbstractContainerMenu {

    /** Where the player's inventory starts, from the top of the screen. */
    public static final int INVENTORY_Y = 84;

    private final BlockPos pos;
    /** The Harvester on the server; null on the client, which reads what the server sends. */
    private final @Nullable HarvesterBlockEntity harvester;

    HarvesterMenu(int containerId, Inventory inventory, HarvesterBlockEntity harvester) {
        this(containerId, inventory, harvester.getBlockPos(), harvester);
    }

    /** The client's menu, opened at the position the server wrote. */
    public HarvesterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos(), null);
    }

    private HarvesterMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable HarvesterBlockEntity harvester) {
        super(Harvesters.MENU.get(), containerId);
        this.pos = pos;
        this.harvester = harvester;
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    /**
     * Holds {@code recipe} on this Harvester if it is a Harvest Recipe, and says whether it did. A Default recipe has
     * no id the recipe manager knows, so it is never held: with no Held recipe the Default recipes run anyway.
     */
    public boolean hold(ServerPlayer player, Identifier recipe) {
        if (harvester == null || harvester.isRemoved() || HarvestRecipes.find(player.level(), recipe).isEmpty()) {
            return false;
        }
        harvester.setHeldRecipe(recipe);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (harvester != null && harvester.isRemoved()) {
            return false;
        }
        return player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
