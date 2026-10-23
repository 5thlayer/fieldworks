// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An patch disc's per-block amount. */
class PatchAmountTest {

    private static final PatchLaw IRON = PatchLaw.of(OreResource.IRON);

    @Test
    void theDiscsBlocksShareOneAmountThatAddsUpToTheDiscsTotal() {
        PatchDisc disc = new PatchDisc(OreResource.IRON, 1.5, 1200, 3000, 400);
        double total = IRON.quantity(1.5, disc.distance()) * IRON.richness(disc.distance());

        long dealt = (long) disc.amountPerBlock() * disc.blockCount();

        assertTrue(dealt <= total && dealt > total - disc.blockCount(),
                "the disc holds its total less the floor's remainder: " + dealt + " of " + total);
    }

    @Test
    void theAmountIsReadAtTheDiscCentresDistanceFromOrigin() {
        PatchDisc east = new PatchDisc(OreResource.IRON, 1.0, 1000, 5000, 0);
        PatchDisc diagonal = new PatchDisc(OreResource.IRON, 1.0, 1000, 3000, 4000);

        assertEquals(5000, diagonal.distance(), 1e-9);
        assertEquals(east.amountPerBlock(), diagonal.amountPerBlock());
        // 1e6 / 2.5 × 16 × (1000 + 5000) / 2600 over 1000 blocks
        assertEquals(14_769, east.amountPerBlock());
    }

    @Test
    void aNetherDiscIsReadAtEightTimesItsDistanceFromOrigin() {
        PatchDisc nether = new PatchDisc(OreResource.QUARTZ, 1.0, 1000, 375, 500);

        assertEquals(5000, nether.distance(), 1e-9);
        assertEquals(PatchLaw.of(OreResource.QUARTZ).total(1.0, 5000) / 1000, nether.amountPerBlock(), 1);
    }

    @Test
    void theSpotStopsGrowingAt1600AndRichnessKeepsRisingWithNoCap() {
        assertEquals(IRON.quantity(1.0, 1600), IRON.quantity(1.0, 5000), 1e-6);
        assertEquals(IRON.quantity(1.0, 1600), IRON.quantity(1.0, 1_000_000), 1e-6);
        assertTrue(IRON.quantity(1.0, 1599) < IRON.quantity(1.0, 1600));

        assertEquals(1.0, IRON.richness(1600), 1e-9);
        assertEquals((1000 + 1_000_000) / 2600.0, IRON.richness(1_000_000), 1e-9);

        PatchDisc near = new PatchDisc(OreResource.IRON, 1.0, 1000, 10_000, 0);
        PatchDisc far = new PatchDisc(OreResource.IRON, 1.0, 1000, 1_000_000, 0);
        assertTrue(far.amountPerBlock() > 90 * near.amountPerBlock(),
                "richness is linear in distance forever");
    }

    @Test
    void nothingIsDealtInsideThePlacementRadius() {
        assertEquals(0.0, IRON.quantity(2.0, 150), 1e-9);
        assertTrue(IRON.quantity(2.0, 151) > 0);
    }

    @Test
    void uraniumTakesItsOwnLaw() {
        PatchLaw uranium = PatchLaw.of(OreResource.URANIUM);

        // 1e6 / 1.25 × 0.9 × 2
        assertEquals(1_440_000, uranium.quantity(1.0, 1600), 1e-6);
        assertEquals(1_440, new PatchDisc(OreResource.URANIUM, 1.0, 1000, 1600, 0).amountPerBlock());
    }
}
