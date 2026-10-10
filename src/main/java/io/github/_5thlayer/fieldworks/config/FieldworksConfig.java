// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.config;

import java.util.EnumMap;
import java.util.Map;

import io.github._5thlayer.fieldworks.ore.OreResource;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code fieldworks-common.toml}: a generation switch per ore resource (ADR-0002). A switch off
 * stops new patches of that resource from generating; patches already in the world stay and can
 * be mined.
 */
public final class FieldworksConfig {

    private static final Map<OreResource, ModConfigSpec.BooleanValue> GENERATE = new EnumMap<>(OreResource.class);
    private static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Whether new patches of each resource generate in newly explored terrain.").push("generate");
        for (OreResource resource : OreResource.values()) {
            GENERATE.put(resource, builder.define(resource.key(), true));
        }
        builder.pop();
        SPEC = builder.build();
    }

    private FieldworksConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    /** The switch itself, so a harness can turn it off and back on. */
    public static ModConfigSpec.BooleanValue generateSwitch(OreResource resource) {
        return GENERATE.get(resource);
    }

    /** Defaults to on before the config has loaded, which only a harness can observe. */
    public static boolean generates(OreResource resource) {
        return !SPEC.isLoaded() || GENERATE.get(resource).get();
    }
}
