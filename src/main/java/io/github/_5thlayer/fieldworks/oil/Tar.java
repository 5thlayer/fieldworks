// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.oil;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tar (#22): the block a crude source sets as where it touches lava ({@link TarInteraction}), and the item it
 * drops. A piston breaks the block rather than pushing it, and the block drops one tar, in {@code c:tar}, which
 * is not placeable. Nothing else makes tar.
 */
public final class Tar {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fieldworks.MOD_ID);

    public static final DeferredBlock<Block> BLOCK = BLOCKS.registerSimpleBlock("tar", props -> props
            .mapColor(MapColor.COLOR_BLACK)
            .strength(0.5F)
            .sound(SoundType.MUD)
            .pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<Item> ITEM = ITEMS.registerSimpleItem("tar");

    private Tar() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TarInteraction.register(modBus);
    }
}
