// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An patch disc's footprint: the spot's cone plus a ragged edge. */
class PatchShapeTest {

    private static final PatchLaw IRON = PatchLaw.of(OreResource.IRON);
    private static final PatchShape.Noise FLAT = (x, z) -> 0;
    private static final PatchShape.Land LAND = (x, z) -> true;

    @Test
    void theRadiusIsCappedAt32() {
        assertEquals(32, IRON.radius(100, 1600), 1e-9);
    }

    @Test
    void theSizeFactorIsDrawnUniformlyBetweenTheSpotSizeBounds() {
        assertEquals(0.25, IRON.sizeFactor(0), 1e-9);
        assertEquals(2.0, IRON.sizeFactor(1), 1e-9);
        assertEquals(1.125, IRON.sizeFactor(0.5), 1e-9);
    }

    @Test
    void aFlatEdgeIsTheConeLessTheOffsetTimesTheAmplitude() {
        double distance = 3000;
        PatchShape shape = PatchShape.of(IRON, 1.0, 3000, 0, FLAT, LAND);
        double radius = IRON.radius(1.0, distance);
        double height = IRON.height(1.0, distance);
        // cone h(1 - r/R) meets (0 - 1/3) × amplitude
        double edge = radius * (1 - IRON.blobAmplitude(distance) / 3 / height);

        assertFalse(shape.isEmpty());
        assertTrue(shape.contains(3000, 0));
        for (int dx = -40; dx <= 40; dx++) {
            for (int dz = -40; dz <= 40; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(r - edge) > 1e-6) {
                    assertEquals(r < edge, shape.contains(3000 + dx, dz), "at " + dx + ", " + dz);
                }
            }
        }
    }

    @Test
    void theBlockCountIsTheColumnsTheShapeContains() {
        PatchShape shape = PatchShape.of(IRON, 1.0, 3000, 0, FLAT, LAND);
        int counted = 0;
        for (int x = 3000 - shape.reach(); x <= 3000 + shape.reach(); x++) {
            for (int z = -shape.reach(); z <= shape.reach(); z++) {
                counted += shape.contains(x, z) ? 1 : 0;
            }
        }
        assertEquals(counted, shape.blockCount());
        assertEquals(Math.PI * Math.pow(shape.radius() * (1 - IRON.blobAmplitude(3000) / 3 / IRON.height(1.0, 3000)), 2),
                counted, counted * 0.05);
    }

    @Test
    void theEdgeFollowsTheOctavesWeightsAndScales() {
        // Each octave sampled at its own scale: a noise that is 1 only where it is read at the
        // coarsest scale's coordinates pushes the edge out by that octave's weight alone.
        OreCorpus.Octave coarse = OreCorpus.get().edge().octaves().getLast();
        PatchShape.Noise coarseOnly = (x, z) -> Math.abs(x - 3000 * coarse.inputScale()) < 1 ? 1 : 0;
        PatchShape flat = PatchShape.of(IRON, 1.0, 3000, 0, FLAT, LAND);
        PatchShape raised = PatchShape.of(IRON, 1.0, 3000, 0, coarseOnly, LAND);

        double distance = 3000;
        double pushed = IRON.radius(1.0, distance) * (1 + (coarse.outputScale() - 1.0 / 3)
                * IRON.blobAmplitude(distance) / IRON.height(1.0, distance));
        int onAxis = (int) Math.floor(pushed - 1e-6);
        assertTrue(raised.contains(3000, onAxis), "the coarse octave's weight moves the edge to " + pushed);
        assertFalse(flat.contains(3000, onAxis));
        assertTrue(raised.blockCount() > flat.blockCount());
    }

    @Test
    void noColumnOffTheLandHoldsOre() {
        PatchShape everywhere = PatchShape.of(IRON, 1.0, 3000, 0, FLAT, LAND);
        PatchShape westOnly = PatchShape.of(IRON, 1.0, 3000, 0, FLAT, (x, z) -> x < 3000);

        assertTrue(everywhere.contains(3005, 0));
        assertFalse(westOnly.contains(3005, 0));
        assertTrue(westOnly.contains(2995, 0));
        assertTrue(westOnly.blockCount() < everywhere.blockCount());
    }

    @Test
    void theSavedMaskReadsBackTheSameColumns() {
        PatchShape.Noise ragged = (x, z) -> Math.sin(x * 7) * Math.cos(z * 5);
        PatchShape shape = PatchShape.of(IRON, 1.0, 3000, 0, ragged, LAND);
        PatchShape read = PatchShape.of(3000, 0, shape.reach(), shape.mask());

        assertEquals(shape.blockCount(), read.blockCount());
        for (int x = 3000 - shape.reach() - 1; x <= 3000 + shape.reach() + 1; x++) {
            for (int z = -shape.reach() - 1; z <= shape.reach() + 1; z++) {
                assertEquals(shape.contains(x, z), read.contains(x, z), "at " + x + ", " + z);
            }
        }
    }

    @Test
    void nothingGeneratesInsideThePlacementRadius() {
        for (double factor : new double[] {0.25, 1, 2}) {
            assertTrue(PatchShape.of(IRON, factor, 150, 0, FLAT, LAND).isEmpty());
            assertTrue(PatchShape.of(IRON, factor, 0, 0, FLAT, LAND).isEmpty());
            assertTrue(PatchShape.of(IRON, factor, 100, 100, FLAT, LAND).isEmpty());
        }
    }

    @Test
    void aNetherCentreCountsEightTimesItsDistanceSoTheSameLawsServeBothDimensions() {
        PatchLaw quartz = PatchLaw.of(OreResource.QUARTZ);

        assertEquals(PatchDimension.NETHER, quartz.dimension());
        assertEquals(8 * 100.0, quartz.distance(100, 0), 1e-9);
        assertEquals(8 * 50.0 * Math.sqrt(2), quartz.distance(50, 50), 1e-9);
        // A Nether centre 100 blocks out is where an Overworld one 800 blocks out is: the same density and richness.
        assertEquals(PatchLaw.of(OreResource.IRON).density(800), quartz.density(quartz.distance(100, 0)) * 8.0 / 6.0, 1e-9);
        assertEquals(IRON.richness(800), quartz.richness(quartz.distance(100, 0)), 1e-9);
    }

    @Test
    void nothingGeneratesBelowAResourcesMinimumDistance() {
        PatchLaw diamond = PatchLaw.of(OreResource.DIAMOND);

        assertTrue(PatchShape.of(diamond, 2, 1400, 0, FLAT, LAND).isEmpty());
        assertFalse(PatchShape.of(diamond, 2, 1500, 0, FLAT, LAND).isEmpty());
        assertFalse(PatchShape.of(IRON, 2, 400, 0, FLAT, LAND).isEmpty(), "the starting resources have no minimum");
    }

    @Test
    void aNetherResourcesMinimumDistanceIsMeasuredInNetherBlocksTimesEight() {
        PatchLaw netherite = PatchLaw.of(OreResource.NETHERITE);

        // 2000 Overworld-equivalent blocks is 250 Nether blocks.
        assertTrue(PatchShape.of(netherite, 2, 249, 0, FLAT, LAND).isEmpty());
        assertFalse(PatchShape.of(netherite, 2, 250, 0, FLAT, LAND).isEmpty());
        assertTrue(PatchShape.of(netherite, 2, 2000, 0, FLAT, LAND).isEmpty() == false,
                "past the gate in the Nether the disc is placed as ever, there being no cap");
    }

    @Test
    void aDiscIsSmallUntil450() {
        assertTrue(PatchShape.of(IRON, 0.25, 150, 1, FLAT, LAND).isEmpty(),
                "a radius that rounds below one block does not generate");
        assertTrue(IRON.radius(1, 300) < IRON.radius(1, 450));
        assertTrue(IRON.radius(1, 450) < IRON.radius(1, 1600));
        assertEquals(IRON.radius(1, 1600), IRON.radius(1, 5000), 1e-9);
    }
}
