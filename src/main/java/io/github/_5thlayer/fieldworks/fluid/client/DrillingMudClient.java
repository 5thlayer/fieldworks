// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid.client;

import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** How drilling mud draws in the world, a tank or a pipe: vanilla water's sprites under a clay-brown tint. */
public final class DrillingMudClient {

    // Water's sprites are drawn to be tinted; crude oil's near-black one would hide the tint.
    private static final Material STILL = new Material(Identifier.withDefaultNamespace("block/water_still"));
    private static final Material FLOWING = new Material(Identifier.withDefaultNamespace("block/water_flow"));

    private static final int TINT = 0xFFB08A5C;

    private DrillingMudClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(DrillingMudClient::registerFluidModels);
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(STILL, FLOWING, null, FluidTintSources.constant(TINT)),
                DrillingMud.SOURCE, DrillingMud.FLOWING);
    }
}
