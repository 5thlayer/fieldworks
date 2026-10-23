// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A column's look down (ADR 0008): the first block it does not pass, within the tier's reach of the Harvester. */
class ReachTest {

    @Test
    void aColumnWithNoReachWorksOnlyTheBlockDirectlyUnder() {
        assertEquals(1, Reach.firstBlock(0, depth -> false));
        assertEquals(Reach.NONE, Reach.firstBlock(0, depth -> true));
    }

    @Test
    void aColumnPassesWhatItCanAndStopsAtTheFirstBlock() {
        assertEquals(3, Reach.firstBlock(2, depth -> depth < 3));
    }

    @Test
    void aBlockPastReachIsNoSource() {
        assertEquals(Reach.NONE, Reach.firstBlock(1, depth -> depth < 3));
    }

    @Test
    void aBlockOnTheLastBlockOfReachIsTheSource() {
        assertEquals(2, Reach.firstBlock(1, depth -> depth < 2));
    }

    @Test
    void reachIsCountedFromTheHarvesterSoACleanedColumnNeverLooksPastIt() {
        // The block at depth 2 broken: depth 3 is next, but reach 1 stops at depth 2.
        assertEquals(Reach.NONE, Reach.firstBlock(1, depth -> true));
        assertEquals(3, Reach.firstBlock(2, depth -> depth != 3));
    }
}
