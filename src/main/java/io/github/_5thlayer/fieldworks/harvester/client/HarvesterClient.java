// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester.client;

import io.github._5thlayer.fieldworks.harvester.Harvesters;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The Harvester on the client: its menu's screen and the renderer of its drill heads and legs. */
public final class HarvesterClient {

    private HarvesterClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(HarvesterClient::registerScreens);
        modBus.addListener(HarvesterClient::registerRenderers);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Harvesters.MENU.get(), HarvesterScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Harvesters.BLOCK_ENTITY.get(), context -> new HarvesterRenderer());
    }
}
