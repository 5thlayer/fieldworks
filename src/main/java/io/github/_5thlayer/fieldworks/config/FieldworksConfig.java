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
 * {@code fieldworks-common.toml}: a generation switch per ore resource, one for oil fields, and one over all of
 * them (ADR-0002). A switch off stops new patches or fields from generating; those already in the world stay.
 */
public final class FieldworksConfig {

    private static final ModConfigSpec.BooleanValue GENERATE_ALL;
    private static final Map<OreResource, ModConfigSpec.BooleanValue> GENERATE = new EnumMap<>(OreResource.class);
    private static final ModConfigSpec.BooleanValue GENERATE_OIL_FIELDS;
    private static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Whether new patches of each resource, and new oil fields, generate in newly explored terrain.").push("generate");
        GENERATE_ALL = builder.comment("Off: no patch or oil field generates, whatever the switches below say.").define("all", true);
        for (OreResource resource : OreResource.values()) {
            GENERATE.put(resource, builder.define(resource.key(), true));
        }
        GENERATE_OIL_FIELDS = builder.define("oil_field", true);
        builder.pop();
        SPEC = builder.build();
    }

    private FieldworksConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    /** The switch over all the others, to turn off and back on while the game runs. */
    public static ModConfigSpec.BooleanValue generateAllSwitch() {
        return GENERATE_ALL;
    }

    /** The switch itself, to turn off and back on while the game runs. */
    public static ModConfigSpec.BooleanValue generateSwitch(OreResource resource) {
        return GENERATE.get(resource);
    }

    /** Defaults to on before the config has loaded. */
    public static boolean generates(OreResource resource) {
        return !SPEC.isLoaded() || (generatesAll() && GENERATE.get(resource).get());
    }

    public static ModConfigSpec.BooleanValue generateOilFieldsSwitch() {
        return GENERATE_OIL_FIELDS;
    }

    public static boolean generatesOilFields() {
        return !SPEC.isLoaded() || (generatesAll() && GENERATE_OIL_FIELDS.get());
    }

    private static boolean generatesAll() {
        return GENERATE_ALL.get();
    }
}
