// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** That the frozen data the mod loads parses and carries the numbers the patches rest on. */
class OreCorpusTest {

    @Test
    void theAlphabetIsTwelve() {
        assertEquals(Set.of("iron", "copper", "coal", "uranium", "stone", "sulfur",
                        "redstone", "gold", "diamond", "quartz", "glowstone", "netherite"),
                OreCorpus.get().resources().keySet());
    }

    @Test
    void everyResourceHasADimensionAndAMinimumDistance() {
        Map<String, PatchDimension> dimensions = Map.ofEntries(
                Map.entry("iron", PatchDimension.OVERWORLD), Map.entry("copper", PatchDimension.OVERWORLD),
                Map.entry("coal", PatchDimension.OVERWORLD), Map.entry("stone", PatchDimension.OVERWORLD),
                Map.entry("sulfur", PatchDimension.OVERWORLD), Map.entry("uranium", PatchDimension.OVERWORLD),
                Map.entry("redstone", PatchDimension.OVERWORLD), Map.entry("gold", PatchDimension.OVERWORLD),
                Map.entry("diamond", PatchDimension.OVERWORLD), Map.entry("quartz", PatchDimension.NETHER),
                Map.entry("glowstone", PatchDimension.NETHER), Map.entry("netherite", PatchDimension.NETHER));
        Map<String, Integer> minimums = Map.ofEntries(
                Map.entry("iron", 0), Map.entry("copper", 0), Map.entry("coal", 0), Map.entry("stone", 0),
                Map.entry("sulfur", 300), Map.entry("uranium", 600), Map.entry("redstone", 300), Map.entry("gold", 500),
                Map.entry("diamond", 1500), Map.entry("quartz", 0), Map.entry("glowstone", 0), Map.entry("netherite", 2000));

        for (OreCorpus.Resource resource : OreCorpus.get().resources().values()) {
            assertEquals(dimensions.get(resource.name()), resource.dimension(), resource.name() + "'s dimension");
            assertEquals(minimums.get(resource.name()), resource.minDistance(), resource.name() + "'s minimum distance");
        }
    }

    @Test
    void ironAndCoalHoldTheirSwappedValues() {
        OreCorpus.Resource iron = OreCorpus.get().resource("iron");
        OreCorpus.Resource coal = OreCorpus.get().resource("coal");

        assertEquals(6400, iron.defaultAmount());
        assertEquals(8.0, iron.patch().baseDensity(), 1e-9);
        assertEquals(0.1, iron.patch().rqFactor(), 1e-9);
        assertEquals(8000, coal.defaultAmount());
        assertEquals(10.0, coal.patch().baseDensity(), 1e-9);
        assertEquals(0.11, coal.patch().rqFactor(), 1e-9);
    }

    @Test
    void theRareResourcesAreFewerAndSmallerThanTheCommonOnes() {
        OreCorpus corpus = OreCorpus.get();

        assertTrue(corpus.resource("diamond").defaultAmount() < corpus.resource("gold").defaultAmount());
        assertTrue(corpus.resource("netherite").defaultAmount() < corpus.resource("glowstone").defaultAmount());
        assertTrue(corpus.resource("netherite").patch().baseSpotsPerKm2() < corpus.resource("quartz").patch().baseSpotsPerKm2());
    }

    @Test
    void everyOreRendersThroughEightStages() {
        assertEquals(8, OreCorpus.get().stageCount());
        for (OreCorpus.Resource resource : OreCorpus.get().resources().values()) {
            assertEquals(1.0, resource.stageRatios().get(0), 1e-9,
                    resource.name() + "'s first stage is a full block");
            assertTrue(resource.stageRatios().get(7) < 0.01,
                    resource.name() + "'s last stage is nearly gone");
        }
    }

    @Test
    void theDistanceLawIsFlatWithinSixteenHundredBlocks() {
        OreCorpus.DistanceLaw law = OreCorpus.get().distanceLaw();

        assertEquals(1600, law.flatWithin());
        assertEquals(1.0, law.richnessAt(0), 1e-9);
        assertEquals(1.0, law.richnessAt(1599), 1e-9, "leaving early buys nothing");
        assertTrue(law.richnessAt(5200) > 2.0, "and far out is richer, linearly");
    }

    @Test
    void thePlacementRadiusLeavesTheSpawnAreaBare() {
        assertEquals(150.0, OreCorpus.get().densityLaw().placementRadius(), 1e-9);
    }
}
