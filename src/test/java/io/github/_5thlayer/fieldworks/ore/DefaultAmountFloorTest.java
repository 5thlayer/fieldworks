// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** An ore block with no {@code fieldworks:default_amount} entry starts at 1, and the reload names it (ADR 0013). */
class DefaultAmountFloorTest {

    @Test
    void aBlockWithNoEntryStartsAtOne() {
        assertEquals(1, DefaultAmountFloor.orFloor(null));
        assertEquals(3, DefaultAmountFloor.orFloor(3));
    }

    @Test
    void theReloadNamesEachBlockWithNoEntry() {
        assertEquals(List.of(OreResource.SULFUR, OreResource.NETHERITE), DefaultAmountFloor.missing(
                resource -> resource == OreResource.SULFUR || resource == OreResource.NETHERITE ? null : 100));
    }
}
