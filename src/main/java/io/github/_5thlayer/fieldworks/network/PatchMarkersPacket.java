// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.network;

import java.util.List;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.marker.MarkerDelivery;
import io.github._5thlayer.fieldworks.marker.PatchMarker;
import io.github._5thlayer.fieldworks.marker.client.PatchMapClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Patch markers and what is left in each, server to client; an amount of zero removes one (ADR-0003). */
public record PatchMarkersPacket(ResourceKey<Level> dimension, List<MarkerDelivery.Update> markers)
        implements CustomPacketPayload {

    public static final Type<PatchMarkersPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_markers"));

    private static final StreamCodec<ByteBuf, PatchMarker> MARKER = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PatchMarker::resource,
            ByteBufCodecs.VAR_INT, PatchMarker::x,
            ByteBufCodecs.VAR_INT, PatchMarker::y,
            ByteBufCodecs.VAR_INT, PatchMarker::z,
            ByteBufCodecs.VAR_LONG, PatchMarker::total,
            PatchMarker::new);

    private static final StreamCodec<ByteBuf, MarkerDelivery.Update> UPDATE = StreamCodec.composite(
            MARKER, MarkerDelivery.Update::marker,
            ByteBufCodecs.VAR_LONG, MarkerDelivery.Update::amount,
            MarkerDelivery.Update::new);

    public static final StreamCodec<ByteBuf, PatchMarkersPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), PatchMarkersPacket::dimension,
            UPDATE.apply(ByteBufCodecs.list()), PatchMarkersPacket::markers,
            PatchMarkersPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PatchMarkersPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> PatchMapClient.receive(packet.dimension(), packet.markers()));
    }
}
