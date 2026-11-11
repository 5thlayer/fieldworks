// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Stage {@code i} begins at {@code 0.5^i} of the starting amount (ADR 0014). */
class OreStageTest {

    private static final int INITIAL = 1280;

    @Test
    void anUntouchedBlockShowsTheFirstStage() {
        assertEquals(0, OreStage.stage(INITIAL, INITIAL, 0.5));
        assertEquals(0, OreStage.stage(INITIAL / 2 + 1, INITIAL, 0.5), "just above half is still whole");
    }

    @Test
    void eachStageBeginsAtHalfThePreviousOne() {
        for (int stage = 1; stage < OreStage.COUNT; stage++) {
            int edge = INITIAL >> stage;
            assertEquals(stage, OreStage.stage(edge, INITIAL, 0.5), "at 1/" + (1 << stage));
            assertEquals(stage, OreStage.stage(edge - 1, INITIAL, 0.5), "just below 1/" + (1 << stage));
            assertEquals(stage - 1, OreStage.stage(edge + 1, INITIAL, 0.5), "just above 1/" + (1 << stage));
        }
    }

    @Test
    void anExhaustedBlockShowsTheLastStage() {
        assertEquals(OreStage.COUNT - 1, OreStage.stage(1, INITIAL, 0.5));
        assertEquals(OreStage.COUNT - 1, OreStage.stage(0, INITIAL, 0.5));
    }
}
