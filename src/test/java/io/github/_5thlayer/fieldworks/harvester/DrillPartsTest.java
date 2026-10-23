// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github._5thlayer.fieldworks.harvester.DrillParts.Box;
import io.github._5thlayer.fieldworks.harvester.DrillParts.Kind;
import org.junit.jupiter.api.Test;

/** The drill heads and legs laid out in plain Java, ahead of the renderer that draws them. */
class DrillPartsTest {

    private static int[] columns(int... overrides) {
        int[] depths = new int[DrillParts.COLUMNS];
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            depths[overrides[i]] = overrides[i + 1];
        }
        return depths;
    }

    private static List<Box> of(Kind kind, List<Box> boxes) {
        return boxes.stream().filter(box -> box.kind() == kind).toList();
    }

    @Test
    void aColumnWithNoSourceDrawsNothing() {
        assertTrue(DrillParts.of(columns()).isEmpty());
    }

    @Test
    void aSourceDirectlyUnderDrawsItsHeadInTheBottomBlocksCellAndNoLegs() {
        List<Box> boxes = DrillParts.of(columns(4, 1));

        assertEquals(2, boxes.size());
        assertTrue(boxes.stream().allMatch(box -> box.dx() == 0 && box.dy() == 0 && box.dz() == 0));
        assertEquals(1, of(Kind.BODY, boxes).size());
        assertEquals(1, of(Kind.BIT, boxes).size());
    }

    @Test
    void aSourceTwoDeepHasItsHeadInTheCellUnderTheBottomBlockAndNoLegs() {
        List<Box> boxes = DrillParts.of(columns(4, 2));

        assertTrue(boxes.stream().allMatch(box -> box.dy() == -1));
        assertTrue(of(Kind.LEG, boxes).isEmpty());
    }

    @Test
    void aSourceFurtherDownHasLegsInEachCellBetweenTheBottomBlockAndItsHead() {
        List<Box> boxes = DrillParts.of(columns(4, 4));

        assertTrue(of(Kind.BODY, boxes).stream().allMatch(box -> box.dy() == -3));
        List<Box> legs = of(Kind.LEG, boxes);
        assertEquals(8, legs.size());
        assertEquals(List.of(-2, -1), legs.stream().map(Box::dy).distinct().sorted().toList());
    }

    @Test
    void aColumnStandsAtItsOwnOffsetFromTheOriginBlock() {
        List<Box> boxes = DrillParts.of(columns(0, 1, 8, 1));

        assertEquals(List.of(-1, 1), boxes.stream().map(Box::dx).distinct().sorted().toList());
        assertEquals(List.of(-1, 1), boxes.stream().map(Box::dz).distinct().sorted().toList());
    }

    @Test
    void everyPartStaysInsideItsCell() {
        for (Box box : DrillParts.of(columns(0, 1, 1, 2, 2, 5, 3, 7))) {
            assertTrue(box.x0() >= 0 && box.x1() <= 1 && box.y0() >= 0 && box.y1() <= 1 && box.z0() >= 0 && box.z1() <= 1, box.toString());
            assertTrue(box.x0() < box.x1() && box.y0() < box.y1() && box.z0() < box.z1(), box.toString());
        }
    }

    @Test
    void legsStandClearOfTheHousingsEdgeSoNoFacesShareAPlane() {
        Box body = of(Kind.BODY, DrillParts.of(columns(4, 3))).getFirst();
        for (Box leg : of(Kind.LEG, DrillParts.of(columns(4, 3)))) {
            assertTrue(leg.x0() > body.x0() && leg.x1() < body.x1() && leg.z0() > body.z0() && leg.z1() < body.z1(), leg.toString());
        }
    }

    @Test
    void theColumnsOffsetsFollowTheCursorsOrder() {
        assertEquals(-1, DrillParts.dx(0));
        assertEquals(-1, DrillParts.dz(0));
        assertEquals(-1, DrillParts.dx(2));
        assertEquals(1, DrillParts.dz(2));
        assertEquals(1, DrillParts.dx(8));
        assertEquals(0, DrillParts.dx(4));
        assertEquals(0, DrillParts.dz(4));
    }

    @Test
    void anotherNumberOfColumnsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DrillParts.of(new int[3]));
    }
}
