// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.fieldworks.marker.MarkerDelivery;
import io.github._5thlayer.fieldworks.marker.PatchMarker;
import io.github._5thlayer.fieldworks.marker.ftb.FtbMapMarkers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The patch markers this client has been sent, and what each patch held when last seen, per
 * dimension. Held in memory only: the server sends them again at each login.
 */
public final class PatchMapClient {

    private static final Map<ResourceKey<Level>, Map<PatchMarker, Long>> MARKERS = new HashMap<>();

    private static MapMarkerRenderer renderer = () -> {
    };

    private PatchMapClient() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PatchMapClient::onLoggingOut);
        if (ModList.get().isLoaded("ftbchunks")) {
            renderer = FtbMapMarkers.create();
        }
    }

    public static void receive(ResourceKey<Level> dimension, List<MarkerDelivery.Update> updates) {
        Map<PatchMarker, Long> markers = MARKERS.computeIfAbsent(dimension, d -> new LinkedHashMap<>());
        for (MarkerDelivery.Update update : updates) {
            if (update.amount() > 0) {
                markers.put(update.marker(), update.amount());
            } else {
                markers.remove(update.marker());
            }
        }
        renderer.markersChanged();
    }

    public static Map<PatchMarker, Long> markers(ResourceKey<Level> dimension) {
        return MARKERS.getOrDefault(dimension, Map.of());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MARKERS.clear();
    }
}
