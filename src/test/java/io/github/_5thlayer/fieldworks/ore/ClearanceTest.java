// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The clearance rule on plain numbers: which of two starts too close together stays. */
class ClearanceTest {

    private static final int CLEARANCE = 16;

    private static final Clearance.Kind OIL = new Clearance.Kind("oil", PatchDimension.OVERWORLD, 47, 0);
    private static final Clearance.Kind IRON = new Clearance.Kind("iron", PatchDimension.OVERWORLD, 42, 1);
    private static final Clearance.Kind COPPER = new Clearance.Kind("copper", PatchDimension.OVERWORLD, 42, 2);
    private static final Clearance.Kind SULFUR = new Clearance.Kind("sulfur", PatchDimension.OVERWORLD, 97, 3);
    private static final Clearance.Kind QUARTZ = new Clearance.Kind("quartz", PatchDimension.NETHER, 42, 4);

    private static Clearance.Start start(Clearance.Kind kind, int x, int reach) {
        return new Clearance.Start(kind, x, 0, reach, true);
    }

    private static Clearance rule(Clearance.Start... starts) {
        List<Clearance.Start> all = List.of(starts);
        return new Clearance(List.of(OIL, IRON, COPPER, SULFUR, QUARTZ), CLEARANCE, 60,
                (kind, x, z, range) -> all.stream().filter(s -> s.kind() == kind).toList());
    }

    @Test
    void theRarerOfTwoStartsTooCloseStaysAndTheCommonerYields() {
        Clearance.Start iron = start(IRON, 0, 20);
        Clearance.Start sulfur = start(SULFUR, 50, 20); // edges 10 apart, under 16
        Clearance clearance = rule(iron, sulfur);

        assertTrue(clearance.stays(sulfur));
        assertFalse(clearance.stays(iron));
    }

    @Test
    void edgesExactlyAClearanceApartKeepBoth() {
        Clearance.Start iron = start(IRON, 0, 20);
        Clearance.Start sulfur = start(SULFUR, 56, 20); // edges 16 apart
        Clearance clearance = rule(iron, sulfur);

        assertTrue(clearance.stays(sulfur));
        assertTrue(clearance.stays(iron));
    }

    @Test
    void equalSpacingGoesByTheFixedOrder() {
        Clearance.Start iron = start(IRON, 0, 20);
        Clearance.Start copper = start(COPPER, 50, 20);
        Clearance clearance = rule(iron, copper);

        assertTrue(clearance.stays(iron));
        assertFalse(clearance.stays(copper));
    }

    @Test
    void anOilFieldIsRarerThanAPatchAndFirstAmongEquals() {
        Clearance.Start oil = start(OIL, 0, 20);
        Clearance.Start iron = start(IRON, 50, 20);
        assertTrue(rule(oil, iron).stays(oil));
        assertFalse(rule(oil, iron).stays(iron));

        Clearance.Start equalOil = start(new Clearance.Kind("oil", PatchDimension.OVERWORLD, 42, 0), 0, 20);
        assertTrue(rule(equalOil, iron).stays(equalOil));
    }

    @Test
    void theMiddleOfAChainIsPushedOutAndSoNoLongerPushesOutTheThird() {
        Clearance.Start oil = start(OIL, 0, 20);          // rarest of the three
        Clearance.Start iron = start(IRON, 50, 20);       // too close to oil
        Clearance.Start copper = start(COPPER, 100, 20);  // too close to iron, but iron is out
        Clearance clearance = rule(oil, iron, copper);

        assertTrue(clearance.stays(oil));
        assertFalse(clearance.stays(iron));
        assertTrue(clearance.stays(copper));
    }

    @Test
    void aNeighbourThatWouldNotGenerateDoesNotCount() {
        Clearance.Start iron = start(IRON, 0, 20);
        Clearance.Start switchedOff = new Clearance.Start(SULFUR, 50, 0, 20, false);

        assertTrue(rule(iron, switchedOff).stays(iron));
    }

    @Test
    void startsOfOneKindAreLeftToTheirSpacing() {
        Clearance.Start iron = start(IRON, 0, 20);
        Clearance.Start other = start(IRON, 45, 20);

        assertTrue(rule(iron, other).stays(iron));
    }

    @Test
    void startsInAnotherDimensionDoNotCount() {
        Clearance.Start iron = start(IRON, 0, 20);
        Clearance.Start quartz = start(QUARTZ, 5, 20);

        assertTrue(rule(iron, quartz).stays(iron));
        assertTrue(rule(iron, quartz).stays(quartz));
    }
}
