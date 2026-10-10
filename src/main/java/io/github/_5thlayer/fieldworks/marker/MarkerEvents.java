// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import java.util.List;

import io.github._5thlayer.fieldworks.network.PatchMarkersPacket;
import io.github._5thlayer.fieldworks.ore.PatchLedgerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Finds patches as chunks are sent, and keeps every player's map current (ADR-0003). */
public final class MarkerEvents {

    /** FTB Chunks starts a client's map from its own login packet, and drops what beats it. */
    private static final int LOGIN_GRACE_TICKS = 40;

    private static final int TICKS_PER_SEND = 4;

    /** A mined patch's amount reaches the maps this often; a marker is a snapshot, not a live count. */
    private static final int TICKS_PER_REFRESH = 100;

    private MarkerEvents() {
    }

    /** A chunk sent to a player holds the centre of any patch that starts in it, which is then found. */
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        if (event.getChunk().getAllStarts().isEmpty()) {
            return;
        }
        ServerLevel level = event.getLevel();
        List<PatchMarker> found = FoundPatchMarkers.centredIn(level, event.getChunk());
        if (!found.isEmpty()) {
            PatchMarkerData.get(level.getServer()).found(level.dimension().identifier().toString(), found);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int ticks = server.getTickCount();
        if (ticks % TICKS_PER_SEND != 0) {
            return;
        }
        PatchMarkerData data = PatchMarkerData.get(server);
        MarkerDelivery.Amounts amounts = amounts(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount < LOGIN_GRACE_TICKS) {
                continue;
            }
            ServerLevel level = player.level();
            String dimension = level.dimension().identifier().toString();
            data.observe(player.getUUID(), dimension);
            if (ticks % TICKS_PER_REFRESH == 0) {
                data.refresh(dimension);
            }
            List<MarkerDelivery.Update> markers = data.takeMarkers(player.getUUID(), amounts);
            if (!markers.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new PatchMarkersPacket(level.dimension(), markers));
            }
        }
    }

    public static void onLogout(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PatchMarkerData.get(player.level().getServer()).logout(player.getUUID());
        }
    }

    /** What is left in each patch: the ledger's for an ore patch, the field's summed yield for oil. */
    public static MarkerDelivery.Amounts amounts(MinecraftServer server) {
        PatchLedgerData ledger = PatchLedgerData.get(server);
        return (dimension, marker) ->
                marker.isOil() ? marker.total() : ledger.remaining(marker.id(dimension), marker.total());
    }
}
