// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.github._5thlayer.fieldworks.oil.OilWellBlock;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreCodecs;
import io.github._5thlayer.fieldworks.ore.OreDelta;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The ore blocks, the oil well, the items the ores pay out, and the chunk attachment a draw writes. */
public final class FieldworksRegistries {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fieldworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fieldworks.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Fieldworks.MOD_ID);

    private static final Map<OreResource, DeferredBlock<OreBlock>> ORES = new EnumMap<>(OreResource.class);
    private static final Map<OreResource, DeferredItem<BlockItem>> ORE_ITEMS = new EnumMap<>(OreResource.class);

    static {
        for (OreResource resource : OreResource.values()) {
            DeferredBlock<OreBlock> block = BLOCKS.registerBlock(resource.blockName(),
                    props -> new OreBlock(resource, props));
            ORES.put(resource, block);
            ORE_ITEMS.put(resource, ITEMS.registerSimpleBlockItem(block));
        }
    }

    /** A well is placed only by worldgen, so it has no item, and nothing breaks it. */
    public static final DeferredBlock<OilWellBlock> OIL_WELL = BLOCKS.registerBlock("oil_well", OilWellBlock::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilWellBlockEntity>> OIL_WELL_ENTITY =
            BLOCK_ENTITIES.register("oil_well",
                    () -> new BlockEntityType<>(OilWellBlockEntity::new, Set.of(OIL_WELL.get())));

    public static final DeferredItem<Item> RAW_URANIUM = ITEMS.registerSimpleItem("raw_uranium");
    public static final DeferredItem<Item> SULFUR = ITEMS.registerSimpleItem("sulfur");
    public static final DeferredItem<Item> URANIUM_INGOT = ITEMS.registerSimpleItem("uranium_ingot");

    /** What has been drawn out of the ore blocks in one chunk: sparse, so an untouched patch costs nothing. */
    public static final Supplier<AttachmentType<OreDelta>> ORE_DELTA = ATTACHMENTS.register(
            "ore_delta",
            () -> AttachmentType.builder(OreDelta::new)
                    .serialize(OreCodecs.DELTA.fieldOf("delta"))
                    .build());

    private FieldworksRegistries() {
    }

    public static DeferredBlock<OreBlock> ore(OreResource resource) {
        return ORES.get(resource);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(FieldworksRegistries::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            ORE_ITEMS.values().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(RAW_URANIUM.get());
            event.accept(SULFUR.get());
            event.accept(URANIUM_INGOT.get());
        }
    }
}
