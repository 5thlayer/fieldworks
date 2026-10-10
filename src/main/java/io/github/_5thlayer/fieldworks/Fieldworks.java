// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.client.CrudeOilClient;
import io.github._5thlayer.fieldworks.gametest.FieldworksGameTests;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.worldgen.FieldworksWorldgen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/** The Library's entry point: ore patches, oil wells and crude oil. */
@Mod(Fieldworks.MOD_ID)
public final class Fieldworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "fieldworks";

    public Fieldworks(IEventBus modBus, ModContainer container) {
        FieldworksConfig.register(container);
        FieldworksRegistries.register(modBus);
        CrudeOil.register(modBus);
        FieldworksWorldgen.register(modBus);
        NeoForge.EVENT_BUS.addListener(OreMining::onBreak);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            CrudeOilClient.register(modBus);
        }
        FieldworksGameTests.register(modBus);
    }
}
