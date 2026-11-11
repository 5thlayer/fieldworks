// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * What an ore block no patch placed starts with: the block data map {@code fieldworks:default_amount}, which the jar
 * ships and a pack overrides (ADR 0013). Kept on the server. A block left without an entry starts at 1, the floor a
 * patch block already has, and each reload names it. Nothing stores what a block started with, so blocks already in
 * the world follow a change.
 */
public final class OreDefaultAmount {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DataMapType<Block, Integer> TYPE = DataMapType.builder(
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "default_amount"), Registries.BLOCK, ExtraCodecs.POSITIVE_INT)
            .build();

    private OreDefaultAmount() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> event.register(TYPE));
        NeoForge.EVENT_BUS.addListener(OreDefaultAmount::onUpdated);
    }

    public static int of(OreBlock ore) {
        return DefaultAmountFloor.orFloor(ore.builtInRegistryHolder().getData(TYPE));
    }

    private static @Nullable Integer entry(OreResource resource) {
        return FieldworksRegistries.ore(resource).get().builtInRegistryHolder().getData(TYPE);
    }

    private static void onUpdated(DataMapsUpdatedEvent event) {
        if (event.getCause() != DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD) {
            return;
        }
        event.ifRegistry(Registries.BLOCK, blocks -> {
            for (OreResource resource : DefaultAmountFloor.missing(OreDefaultAmount::entry)) {
                LOGGER.warn("{} has no {} entry; a block of it no patch placed starts at {}",
                        FieldworksRegistries.ore(resource).getId(), TYPE.id(), DefaultAmountFloor.FLOOR);
            }
        });
    }
}
