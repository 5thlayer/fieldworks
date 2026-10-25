// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.fluid.MagmaSlurry;
import io.github._5thlayer.fieldworks.fluid.client.CrudeOilClient;
import io.github._5thlayer.fieldworks.fluid.client.DrillingMudClient;
import io.github._5thlayer.fieldworks.fluid.client.MagmaSlurryClient;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.client.HarvesterClient;
import io.github._5thlayer.fieldworks.marker.MarkerEvents;
import io.github._5thlayer.fieldworks.marker.client.PatchMapClient;
import io.github._5thlayer.fieldworks.network.ClearHeldRecipePacket;
import io.github._5thlayer.fieldworks.network.HeldRecipeSyncPacket;
import io.github._5thlayer.fieldworks.network.HoldRecipePacket;
import io.github._5thlayer.fieldworks.network.PatchMarkersPacket;
import io.github._5thlayer.fieldworks.oil.Tar;
import io.github._5thlayer.fieldworks.recipe.NoRemainderShapelessRecipe;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.worldgen.FieldworksWorldgen;
import io.github._5thlayer.libworks.gallery.GalleryPack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Module's entry point: ore patches, oil wells, crude oil and tar, their map markers, and the Harvester.
 * The game tests hook themselves in from the {@code gametest} source set, which never ships (libworks ADR 0011).
 */
@Mod(Fieldworks.MOD_ID)
public final class Fieldworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "fieldworks";

    private static final String NETWORK_VERSION = "1";

    public Fieldworks(IEventBus modBus, ModContainer container) {
        FieldworksConfig.register(container);
        FieldworksRegistries.register(modBus);
        CrudeOil.register(modBus);
        DrillingMud.register(modBus);
        MagmaSlurry.register(modBus);
        NoRemainderShapelessRecipe.register(modBus);
        Tar.register(modBus);
        Harvesters.register(modBus);
        FieldworksWorldgen.register(modBus);
        GalleryPack.register(modBus, MOD_ID);
        modBus.addListener(WorksTabContents::add);
        modBus.addListener(Fieldworks::registerPayloads);
        NeoForge.EVENT_BUS.addListener(OreMining::onBreak);
        NeoForge.EVENT_BUS.addListener(MarkerEvents::onChunkSent);
        NeoForge.EVENT_BUS.addListener(MarkerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(MarkerEvents::onLogout);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            CrudeOilClient.register(modBus);
            DrillingMudClient.register(modBus);
            MagmaSlurryClient.register(modBus);
            HarvesterClient.register(modBus);
            PatchMapClient.register();
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToClient(PatchMarkersPacket.TYPE, PatchMarkersPacket.STREAM_CODEC, PatchMarkersPacket::handle)
                .playToServer(HoldRecipePacket.TYPE, HoldRecipePacket.STREAM_CODEC, HoldRecipePacket::handle)
                .playToServer(ClearHeldRecipePacket.TYPE, ClearHeldRecipePacket.STREAM_CODEC, ClearHeldRecipePacket::handle)
                .playToClient(HeldRecipeSyncPacket.TYPE, HeldRecipeSyncPacket.STREAM_CODEC, HeldRecipeSyncPacket::handle);
    }
}
