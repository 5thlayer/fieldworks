// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import io.github._5thlayer.fieldworks.gametest.FieldworksGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The Library's entry point. It registers the game tests, which exist only when game tests are
 * enabled, and nothing else yet.
 */
@Mod(Fieldworks.MOD_ID)
public final class Fieldworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "fieldworks";

    public Fieldworks(IEventBus modBus) {
        FieldworksGameTests.register(modBus);
    }
}
