// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.oil;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluid;
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

    /**
     * Every source fluid in {@code c:crude_oil}, which is every fluid that sets as tar: flowing crude never does. Read
     * from the bound tag, so only once the tags are loaded, as a recipe viewer's plugin is.
     */
    public static List<Fluid> crudeSources() {
        List<Fluid> sources = new ArrayList<>();
        for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(FieldworksTags.CRUDE_OIL)) {
            Fluid fluid = holder.value();
            if (fluid.defaultFluidState().isSource()) {
                sources.add(fluid);
            }
        }
        return sources;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TarInteraction.register(modBus);
    }
}
