// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.FootprintItem;
import io.github._5thlayer.groundworks.FootprintPartBlock;
import io.github._5thlayer.groundworks.FootprintShape;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Harvesters' registrations: per tier block, an origin, a part block and an item standing on a
 * Groundworks footprint of its own, and one block entity type for them all.
 *
 * <p>The footprint is 3x3x3 for every tier, its origin at the bottom centre, so the layer the Harvester
 * works is the 3x3 under it. A part names its footprint, so each tier block has a part block of its own.
 */
public final class Harvesters {

    static final FootprintShape SHAPE = FootprintShape.square(3, 3);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fieldworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fieldworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Fieldworks.MOD_ID);

    private static final Map<TierBlock, Footprint> FOOTPRINTS = new EnumMap<>(TierBlock.class);
    private static final Map<TierBlock, DeferredBlock<HarvesterBlock>> BLOCK_BY_TIER = new EnumMap<>(TierBlock.class);
    private static final Map<TierBlock, DeferredItem<FootprintItem>> ITEM_BY_TIER = new EnumMap<>(TierBlock.class);

    static {
        for (TierBlock tier : TierBlock.values()) {
            DeferredBlock<HarvesterBlock> origin = BLOCKS.registerBlock(tier.blockName(),
                    properties -> new HarvesterBlock(machine(properties), tier));
            DeferredBlock<FootprintPartBlock> part = BLOCKS.registerBlock(tier.partBlockName(),
                    properties -> new FootprintPartBlock(machine(properties).noLootTable(), () -> footprint(tier)));
            DeferredItem<FootprintItem> item = ITEMS.registerItem(tier.blockName(),
                    properties -> new FootprintItem(footprint(tier), properties.useBlockDescriptionPrefix()));
            BLOCK_BY_TIER.put(tier, origin);
            ITEM_BY_TIER.put(tier, item);
            FOOTPRINTS.put(tier, Footprint.declare(SHAPE, origin, part, item));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HarvesterBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("harvester", () -> new BlockEntityType<>(HarvesterBlockEntity::new,
                    BLOCK_BY_TIER.values().stream().map(Supplier::get).toArray(Block[]::new)));

    public static final DeferredHolder<MenuType<?>, MenuType<HarvesterMenu>> MENU =
            MENUS.register("harvester", () -> IMenuTypeExtension.create(HarvesterMenu::new));

    private Harvesters() {
    }

    /** The origin draws the whole machine, and an occluding origin would hold no light to draw it by. */
    private static BlockBehaviour.Properties machine(BlockBehaviour.Properties properties) {
        return properties.strength(3.0f, 6.0f).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        HarvestRecipes.register(modBus);
        modBus.addListener(Harvesters::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(HarvesterTiers::addListener);
    }

    public static Footprint footprint(TierBlock tier) {
        return FOOTPRINTS.get(tier);
    }

    public static DeferredBlock<HarvesterBlock> block(TierBlock tier) {
        return BLOCK_BY_TIER.get(tier);
    }

    public static DeferredItem<FootprintItem> item(TierBlock tier) {
        return ITEM_BY_TIER.get(tier);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // On every face of every block of the footprint: Groundworks' parts forward their lookups here.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BLOCK_ENTITY.get(), (harvester, side) -> harvester.itemFace());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BLOCK_ENTITY.get(), (harvester, side) -> harvester.fluidFace());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BLOCK_ENTITY.get(), (harvester, side) -> harvester.energyFace());
    }
}
