// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** That the blocks and the data behind them are the same set: a block without amounts breaks on its first hit. */
class OreResourceTest {

    @Test
    void everyOreBlockHasDataBehindIt() {
        for (OreResource resource : OreResource.values()) {
            assertNotNull(resource.corpus(), resource.key() + " has no data");
        }
    }

    @Test
    void everyResourceInTheDataHasABlock() {
        for (String key : OreCorpus.get().resources().keySet()) {
            assertNotNull(OreResource.of(key), key + " is in the data but nothing places it");
        }
    }

    @Test
    void theDropsAreVanillasOrFieldworksOwn() {
        assertEquals("minecraft:raw_iron", OreResource.IRON.drop());
        assertEquals("minecraft:raw_copper", OreResource.COPPER.drop());
        assertEquals("minecraft:coal", OreResource.COAL.drop());
        assertEquals("minecraft:cobblestone", OreResource.STONE.drop());
        assertEquals("fieldworks:raw_uranium", OreResource.URANIUM.drop());
        assertEquals("fieldworks:sulfur", OreResource.SULFUR.drop());
    }

    @Test
    void theBlockNameIsDerivedFromTheKey() {
        assertEquals("iron_ore", OreResource.IRON.blockName());
        assertEquals("sulfur_ore", OreResource.SULFUR.blockName());
    }
}
