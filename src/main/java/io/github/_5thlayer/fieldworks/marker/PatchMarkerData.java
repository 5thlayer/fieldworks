// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The patches the server has found, saved with the world on the overworld's data storage, and what
 * each online player's map has been sent of them. What a player was sent is not saved: the client
 * forgets its markers at logout.
 */
public final class PatchMarkerData extends SavedData {

    public static final SavedDataType<PatchMarkerData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_markers"),
            PatchMarkerData::new,
            FoundPatches.CODEC.xmap(PatchMarkerData::new, data -> data.found));

    private final FoundPatches found;
    private final MarkerDelivery delivery = new MarkerDelivery();

    public PatchMarkerData() {
        this(new FoundPatches());
    }

    private PatchMarkerData(FoundPatches found) {
        this.found = found;
    }

    public static PatchMarkerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Records the markers, and queues the ones not found before for every player in the dimension. */
    public void found(String dimension, Collection<PatchMarker> markers) {
        for (PatchMarker marker : markers) {
            if (found.add(dimension, marker)) {
                delivery.found(dimension, marker);
                setDirty();
            }
        }
    }

    public void observe(UUID player, String dimension) {
        delivery.observe(player, dimension, found.in(dimension));
    }

    /** Queues every marker sent in the dimension again, so a changed amount reaches the maps. */
    public void refresh(String dimension) {
        delivery.refresh(dimension);
    }

    public List<MarkerDelivery.Update> takeMarkers(UUID player, MarkerDelivery.Amounts amounts) {
        return delivery.take(player, amounts);
    }

    public void logout(UUID player) {
        delivery.logout(player);
    }
}
