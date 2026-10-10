// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid.client;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** How crude oil draws in the world, a tank or a pipe: one sprite under a constant grey tint. */
public final class CrudeOilClient {

    private static final Material SPRITE =
            new Material(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "block/fluid/crude_oil"));

    private static final int TINT = 0xFF7A7A7A;

    private CrudeOilClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CrudeOilClient::registerFluidModels);
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(SPRITE, SPRITE, null, FluidTintSources.constant(TINT)),
                CrudeOil.SOURCE, CrudeOil.FLOWING);
    }
}
