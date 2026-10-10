// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.client.CrudeOilClient;
import io.github._5thlayer.fieldworks.gametest.FieldworksGameTests;
import io.github._5thlayer.fieldworks.marker.MarkerEvents;
import io.github._5thlayer.fieldworks.marker.client.PatchMapClient;
import io.github._5thlayer.fieldworks.network.PatchMarkersPacket;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.worldgen.FieldworksWorldgen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Library's entry point: ore patches, oil wells and crude oil, and their map markers. */
@Mod(Fieldworks.MOD_ID)
public final class Fieldworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "fieldworks";

    private static final String NETWORK_VERSION = "1";

    public Fieldworks(IEventBus modBus, ModContainer container) {
        FieldworksConfig.register(container);
        FieldworksRegistries.register(modBus);
        CrudeOil.register(modBus);
        FieldworksWorldgen.register(modBus);
        modBus.addListener(Fieldworks::registerPayloads);
        NeoForge.EVENT_BUS.addListener(OreMining::onBreak);
        NeoForge.EVENT_BUS.addListener(MarkerEvents::onChunkSent);
        NeoForge.EVENT_BUS.addListener(MarkerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(MarkerEvents::onLogout);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            CrudeOilClient.register(modBus);
            PatchMapClient.register();
        }
        FieldworksGameTests.register(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToClient(PatchMarkersPacket.TYPE, PatchMarkersPacket.STREAM_CODEC, PatchMarkersPacket::handle);
    }
}
