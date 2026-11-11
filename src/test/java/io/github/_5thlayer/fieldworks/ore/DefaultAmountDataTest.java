// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/** That the shipped {@code fieldworks:default_amount} data map holds each ore block's default amount (ADR 0013). */
class DefaultAmountDataTest {

    private static final String PATH = "/data/fieldworks/data_maps/block/default_amount.json";

    @Test
    void everyOreBlockHasItsDefaultAmount() throws Exception {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("iron", 6400), Map.entry("copper", 6400), Map.entry("coal", 8000), Map.entry("stone", 3200),
                Map.entry("sulfur", 2400), Map.entry("uranium", 1440), Map.entry("redstone", 4800), Map.entry("gold", 3200),
                Map.entry("diamond", 480), Map.entry("quartz", 4800), Map.entry("glowstone", 2400), Map.entry("netherite", 160));
        JsonObject values;
        try (var reader = new InputStreamReader(DefaultAmountDataTest.class.getResourceAsStream(PATH), StandardCharsets.UTF_8)) {
            values = new Gson().fromJson(reader, JsonObject.class).getAsJsonObject("values");
        }

        assertEquals(OreResource.values().length, values.size());
        for (OreResource resource : OreResource.values()) {
            assertEquals(expected.get(resource.key()), values.get("fieldworks:" + resource.blockName()).getAsInt(), resource.key());
        }
    }
}
