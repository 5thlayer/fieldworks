// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid.client;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** How drilling mud draws in the world, a tank or a pipe: crude oil's placeholder sprite under a brown tint. */
public final class DrillingMudClient {

    private static final Material SPRITE =
            new Material(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "block/fluid/crude_oil"));

    private static final int TINT = 0xFF805A30;

    private DrillingMudClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(DrillingMudClient::registerFluidModels);
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(SPRITE, SPRITE, null, FluidTintSources.constant(TINT)),
                DrillingMud.SOURCE, DrillingMud.FLOWING);
    }
}
