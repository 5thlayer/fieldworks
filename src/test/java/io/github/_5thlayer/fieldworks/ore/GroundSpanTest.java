// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A disc lies on ground a Harvester can work: no 3×3 square of its columns spans more than 3 (ADR 0011). */
class GroundSpanTest {

    private static final PatchLaw IRON = PatchLaw.of(OreResource.IRON);
    private static final PatchShape.Noise FLAT = (x, z) -> 0;
    private static final PatchShape.Land LAND = (x, z) -> true;
    private static final int CENTRE_X = 3000;

    private static PatchShape disc(PatchShape.Land land) {
        PatchShape shape = PatchShape.of(IRON, 1.0, CENTRE_X, 0, FLAT, land);
        assertTrue(shape.reach() > 6, "the disc is wide enough to cross a step and a ramp");
        return shape;
    }

    private static PatchShape disc() {
        return disc(LAND);
    }

    @Test
    void flatGroundIsWorkable() {
        assertTrue(GroundSpan.workable(disc(), (x, z) -> 64));
    }

    @Test
    void aStepOfThreeAcrossTheDiscIsWorkable() {
        assertTrue(GroundSpan.workable(disc(), (x, z) -> x >= CENTRE_X ? 67 : 64));
    }

    @Test
    void aStepOfFourAcrossTheDiscIsNot() {
        assertFalse(GroundSpan.workable(disc(), (x, z) -> x >= CENTRE_X ? 68 : 64));
    }

    @Test
    void aStepDownOfFourIsNoMoreWorkableThanOneUp() {
        assertFalse(GroundSpan.workable(disc(), (x, z) -> x >= CENTRE_X ? 60 : 64));
    }

    @Test
    void oneColumnAtTheRaggedEdgeFourAboveItsNeighboursIsNotWorkable() {
        PatchShape shape = disc();
        int edge = edgeOnTheAxis(shape);
        assertFalse(GroundSpan.workable(shape, (x, z) -> x == edge && z == 0 ? 68 : 64));
    }

    @Test
    void aRampRisingOneABlockIsWorkableThoughItRisesFarMoreThanThreeAcrossTheDisc() {
        assertTrue(GroundSpan.workable(disc(), (x, z) -> 64 + (x - CENTRE_X)));
    }

    @Test
    void aRampRisingTwoABlockIsNot() {
        assertFalse(GroundSpan.workable(disc(), (x, z) -> 64 + 2 * (x - CENTRE_X)));
    }

    @Test
    void aDiagonalRampIsMeasuredAcrossTheSquareCornerToCorner() {
        // One a block along each axis: a 3×3 square's corners are 4 apart.
        assertFalse(GroundSpan.workable(disc(), (x, z) -> 64 + (x - CENTRE_X) + z));
    }

    @Test
    void columnsOffTheDiscDoNotCount() {
        // A cliff where the land drops five rows of the disc: either side is flat, and no square holds both.
        PatchShape shape = disc((x, z) -> Math.abs(x - CENTRE_X) > 2);
        assertTrue(GroundSpan.workable(shape, (x, z) -> Math.abs(x - CENTRE_X) <= 2 ? 200 : x > CENTRE_X ? 70 : 64));
    }

    @Test
    void twoColumnsThreeApartShareNoSquare() {
        // Steps of three every three blocks along x: any 3×3 square crosses one step at most.
        assertTrue(GroundSpan.workable(disc(), (x, z) -> 64 + 3 * Math.floorDiv(x - CENTRE_X, 3)));
    }

    /** The disc's farthest column along +x from the centre, on the z = 0 row. */
    private static int edgeOnTheAxis(PatchShape shape) {
        int edge = CENTRE_X;
        while (shape.contains(edge + 1, 0)) {
            edge++;
        }
        return edge;
    }
}
