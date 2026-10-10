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
 * The menu's clear button, pressed with a Harvester's menu open: drop its Held recipe, which returns it to its Default
 * recipes (#13). It carries nothing; which Harvester is the server's call, the one the player has open.
 */
public record ClearHeldRecipePacket() implements CustomPacketPayload {

    public static final ClearHeldRecipePacket INSTANCE = new ClearHeldRecipePacket();

    public static final Type<ClearHeldRecipePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "clear_held_recipe"));

    public static final StreamCodec<ByteBuf, ClearHeldRecipePacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearHeldRecipePacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            receive(player, packet);
        }
    }

    /** Clears the Held recipe of the Harvester whose menu the player has open, and says whether it did. */
    public static boolean receive(ServerPlayer player, ClearHeldRecipePacket packet) {
        return player.containerMenu instanceof HarvesterMenu menu && menu.clearHeld();
    }
}
