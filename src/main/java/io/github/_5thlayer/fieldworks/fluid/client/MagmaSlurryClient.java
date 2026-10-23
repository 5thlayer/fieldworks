// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid.client;

import io.github._5thlayer.fieldworks.fluid.MagmaSlurry;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** How magma slurry draws in the world, a tank or a pipe: vanilla lava's sprites under a darker, redder tint than lava itself. */
public final class MagmaSlurryClient {

    private static final Material STILL = new Material(Identifier.withDefaultNamespace("block/lava_still"));
    private static final Material FLOWING = new Material(Identifier.withDefaultNamespace("block/lava_flow"));

    private static final int TINT = 0xFF9C4A3C;

    private MagmaSlurryClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MagmaSlurryClient::registerFluidModels);
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(STILL, FLOWING, null, FluidTintSources.constant(TINT)),
                MagmaSlurry.SOURCE, MagmaSlurry.FLOWING);
    }
}
