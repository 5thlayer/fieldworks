// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.network;

import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
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
 * An open Harvester's Held recipe, for its menu to name: sent when the menu opens and whenever the recipe changes
 * while it is up (#13). {@code containerId} lets one that arrives after the player closed the menu land on nothing.
 */
public record HeldRecipeSyncPacket(int containerId, Optional<HeldRecipeView> held) implements CustomPacketPayload {

    public static final Type<HeldRecipeSyncPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "held_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HeldRecipeSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HeldRecipeSyncPacket::containerId,
            ByteBufCodecs.optional(HeldRecipeView.STREAM_CODEC), HeldRecipeSyncPacket::held,
            HeldRecipeSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeldRecipeSyncPacket packet, IPayloadContext context) {
        if (context.player().containerMenu instanceof HarvesterMenu menu && menu.containerId == packet.containerId()) {
            menu.show(packet.held());
        }
    }

    /** Sends the packet if the player's connection opened its channel; a game test's mock player negotiates none. */
    public static void send(ServerPlayer player, HeldRecipeSyncPacket packet) {
        if (player.connection != null && player.connection.hasChannel(packet)) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }
}
