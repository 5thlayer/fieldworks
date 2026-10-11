// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** A Harvester's slots, which mark its block entity changed when an insert or extract commits. */
final class HarvesterSlots extends ItemStacksResourceHandler {

    private final Runnable changed;

    HarvesterSlots(int size, Runnable changed) {
        super(size);
        this.changed = changed;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        changed.run();
    }
}
