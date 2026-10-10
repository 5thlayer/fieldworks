// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;

/** That a patch is found once, per dimension, and stays found across a save. */
class FoundPatchesTest {

    private static final PatchMarker IRON = new PatchMarker("iron", 200, 64, 300, 5000);
    private static final PatchMarker OIL = new PatchMarker(PatchMarker.CRUDE_OIL, 900, 66, 900, 640_000);

    @Test
    void aPatchIsFoundOnce() {
        FoundPatches found = new FoundPatches();

        assertTrue(found.add("minecraft:overworld", IRON));
        assertFalse(found.add("minecraft:overworld", IRON));
        assertEquals(Set.of(IRON), found.in("minecraft:overworld"));
        assertTrue(found.in("minecraft:the_nether").isEmpty());
    }

    @Test
    void whatIsFoundSurvivesASave() {
        FoundPatches found = new FoundPatches();
        found.add("minecraft:overworld", IRON);
        found.add("minecraft:overworld", OIL);
        found.add("minecraft:the_nether", IRON);

        var json = FoundPatches.CODEC.encodeStart(JsonOps.INSTANCE, found).getOrThrow();
        FoundPatches restored = FoundPatches.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(Set.of(IRON, OIL), restored.in("minecraft:overworld"));
        assertEquals(Set.of(IRON), restored.in("minecraft:the_nether"));
    }

    @Test
    void aMarkerKnowsItsPatch() {
        assertEquals("iron", IRON.id("minecraft:overworld").resource());
        assertEquals(200, IRON.id("minecraft:overworld").x());
        assertTrue(OIL.isOil());
        assertFalse(IRON.isOil());
    }
}
