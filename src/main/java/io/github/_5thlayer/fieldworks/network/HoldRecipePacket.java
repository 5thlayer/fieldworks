// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.network;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A recipe viewer's transfer, pressed with a Harvester's menu open: hold this Harvest Recipe (#10). It carries the
 * recipe and nothing else; whether the open Harvester takes it is the server's call.
 */
public record HoldRecipePacket(Identifier recipe) implements CustomPacketPayload {

    public static final Type<HoldRecipePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "hold_recipe"));

    public static final StreamCodec<ByteBuf, HoldRecipePacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, HoldRecipePacket::recipe,
            HoldRecipePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoldRecipePacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            receive(player, packet);
        }
    }

    /** Holds the recipe on the Harvester whose menu the player has open, and says whether it did. */
    public static boolean receive(ServerPlayer player, HoldRecipePacket packet) {
        return player.containerMenu instanceof HarvesterMenu menu && menu.hold(player, packet.recipe());
    }
}
