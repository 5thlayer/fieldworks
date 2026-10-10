// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The per-block amount, which is a quotient and never a typed constant.
 *
 * <p>The patch total is the invariant and the per-block amount is that total over the blocks the
 * field holds, so a patch the generator resized re-derives rather than drifting.
 */
class OreFieldTest {

    @Test
    void theAmountIsTheTotalOverTheBlocks() {
        OreField field = new OreField("iron", 400_000, 400);

        assertEquals(1_000, field.amountPerBlock());
    }

    @Test
    void aFieldNeverPaysOutMoreThanItsTotal() {
        OreField field = new OreField("copper", 320_000, 383);

        assertTrue((long) field.amountPerBlock() * 383 <= 320_000,
                "the quotient rounds down, so the field is never richer than the total");
    }

    @Test
    void everyBlockCarriesAtLeastOneUnit() {
        OreField field = new OreField("stone", 10, 400);

        assertEquals(1, field.amountPerBlock(),
                "a block that holds nothing is a block that breaks on the first hit for no ore");
    }

    @Test
    void aFieldWithNoBlocksHasNoAmountRatherThanADivisionByZero() {
        OreField field = new OreField("coal", 320_000, 0);

        assertEquals(0, field.amountPerBlock());
    }
}
