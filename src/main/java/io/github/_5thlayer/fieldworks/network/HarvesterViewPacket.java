// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.network;

import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatus;
import io.github._5thlayer.fieldworks.harvester.HeldRecipeView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What an open Harvester's menu shows: its status, in the codec Jade's tooltip uses, and its Held recipe, which the
 * client has no recipe to read from (#13). Sent when the menu opens and whenever either changes while it is up.
 * {@code containerId} lets one that arrives after the player closed the menu land on nothing.
 */
public record HarvesterViewPacket(int containerId, HarvesterStatus status, Optional<HeldRecipeView> held) implements CustomPacketPayload {

    public static final Type<HarvesterViewPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HarvesterViewPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HarvesterViewPacket::containerId,
            HarvesterStatus.STREAM_CODEC, HarvesterViewPacket::status,
            ByteBufCodecs.optional(HeldRecipeView.STREAM_CODEC), HarvesterViewPacket::held,
            HarvesterViewPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HarvesterViewPacket packet, IPayloadContext context) {
        if (context.player().containerMenu instanceof HarvesterMenu menu && menu.containerId == packet.containerId()) {
            menu.show(packet.status(), packet.held());
        }
    }

    /** Sends the packet if the player's connection opened its channel; a game test's mock player negotiates none. */
    public static void send(ServerPlayer player, HarvesterViewPacket packet) {
        if (player.connection != null && player.connection.hasChannel(packet)) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }
}
