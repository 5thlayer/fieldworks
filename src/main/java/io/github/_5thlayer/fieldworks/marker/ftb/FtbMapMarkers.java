// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker.ftb;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.event.AddMapIconEvent;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftbchunks.api.neoforge.FTBChunksClientEvent;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import io.github._5thlayer.fieldworks.marker.PatchAmount;
import io.github._5thlayer.fieldworks.marker.PatchMarker;
import io.github._5thlayer.fieldworks.marker.client.MapMarkerRenderer;
import io.github._5thlayer.fieldworks.marker.client.PatchMapClient;
import io.github._5thlayer.fieldworks.oil.OilCorpus;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Draws the patch markers as FTB Chunks map icons, through its public icon API. The icons are
 * rebuilt from {@link PatchMapClient} on each refresh. Client only, loaded only when
 * {@code ftbchunks} is.
 */
public final class FtbMapMarkers implements MapMarkerRenderer {

    /** Crude oil is a fluid, so its bucket stands in for it on the map. */
    private static final String OIL_ICON = "fieldworks:crude_oil_bucket";

    private FtbMapMarkers() {
    }

    public static MapMarkerRenderer create() {
        NeoForge.EVENT_BUS.addListener(FtbMapMarkers::onMapIcon);
        return new FtbMapMarkers();
    }

    @Override
    public void markersChanged() {
        FTBChunksAPI.clientApi().requestMinimapIconRefresh();
    }

    private static void onMapIcon(FTBChunksClientEvent.MapIcon event) {
        AddMapIconEvent.Data data = event.getEventData();
        PatchMapClient.markers(data.dimension()).forEach((marker, amount) -> data.add(new PatchIcon(marker, amount)));
    }

    private static final class PatchIcon extends MapIcon.SimpleMapIcon {

        private final Component name;
        private final String amount;
        private final boolean oil;

        private PatchIcon(PatchMarker marker, long amount) {
            super(new Vec3(marker.x() + 0.5, marker.y(), marker.z() + 0.5),
                    ItemIcon.ofItem(BuiltInRegistries.ITEM.getValue(Identifier.parse(icon(marker)))));
            this.name = Component.translatable("map.fieldworks.patch." + marker.resource());
            this.oil = marker.isOil();
            this.amount = oil ? PatchAmount.yield(amount, OilCorpus.get().normal()) : PatchAmount.format(amount);
        }

        private static String icon(PatchMarker marker) {
            return marker.isOil() ? OIL_ICON : OreResource.of(marker.resource()).drop();
        }

        @Override
        public void addTooltip(TooltipList list) {
            list.add(Component.translatable(oil ? "map.fieldworks.patch.yield" : "map.fieldworks.patch.amount",
                    name, amount));
        }
    }
}
