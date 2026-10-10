// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** That the frozen data the mod loads parses and carries the numbers the patches rest on. */
class OreCorpusTest {

    @Test
    void theAlphabetIsSix() {
        assertEquals(Set.of("iron", "copper", "coal", "uranium", "stone", "sulfur"),
                OreCorpus.get().resources().keySet());
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
