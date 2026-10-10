// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.Choice;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import org.junit.jupiter.api.Test;

/** The choice of the Harvester's next operation over the layer under its footprint. */
class OperationChoiceTest {

    private static final Source IRON = Source.oreBlock("fieldworks:iron_ore");
    private static final Source COPPER = Source.oreBlock("fieldworks:copper_ore");
    private static final Source NONE = Source.NONE;
    private static final Source WELL = Source.well("fieldworks:oil_well");

    @Test
    void oreBlocksAreWorkedInTurnInTheLayersOrder() {
        List<Source> layer = List.of(NONE, IRON, NONE, NONE, COPPER, NONE, NONE, IRON, NONE);

        assertEquals(List.of(1, 4, 7, 1, 4), positions(layer, 0, 5));
    }

    @Test
    void theSavedCursorResumesTheRoundWhereItStopped() {
        List<Source> layer = List.of(NONE, IRON, NONE, NONE, COPPER, NONE, NONE, IRON, NONE);
        Operation first = (Operation) OperationChoice.choose(layer, 0);

        // The cursor is what is saved: handed back, the round goes on to the next ore block.
        Operation resumed = (Operation) OperationChoice.choose(layer, first.nextCursor());

        assertEquals(1, first.position());
        assertEquals(4, resumed.position());
    }

    @Test
    void aCursorPastTheLayerWrapsToItsStart() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, COPPER);

        assertEquals(0, ((Operation) OperationChoice.choose(layer, 9)).position());
        assertEquals(8, ((Operation) OperationChoice.choose(layer, -1)).position());
    }

    @Test
    void anOreBlockGoneFromTheLayerIsSkippedAndTheRoundGoesOn() {
        List<Source> layer = new ArrayList<>(List.of(IRON, IRON, IRON, NONE, NONE, NONE, NONE, NONE, NONE));
        Operation first = (Operation) OperationChoice.choose(layer, 0);
        layer.set(1, NONE);

        assertEquals(0, first.position());
        assertEquals(2, ((Operation) OperationChoice.choose(layer, first.nextCursor())).position());
    }

    @Test
    void aLayerWithNoOreBlockLeavesTheHarvesterIdle() {
        List<Source> layer = List.of(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_TO_WORK), OperationChoice.choose(layer, 3));
    }

    @Test
    void anOreBlocksDefaultRecipeDrawsInTwentyTicksAtSpeedOne() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(20, ((Operation) OperationChoice.choose(layer, 0)).ticks());
    }

    @Test
    void aWellUnderTheFootprintExcludesEveryOreBlock() {
        List<Source> layer = List.of(IRON, IRON, NONE, NONE, WELL, NONE, NONE, COPPER, NONE);

        assertEquals(List.of(4, 4, 4), positions(layer, 0, 3));
    }

    @Test
    void wellsAreWorkedInTurn() {
        List<Source> layer = List.of(WELL, IRON, NONE, NONE, NONE, NONE, IRON, NONE, WELL);

        assertEquals(List.of(0, 8, 0, 8), positions(layer, 0, 4));
    }

    @Test
    void aWellsDefaultRecipePumpsOneCycleInTwentyTicksAtSpeedOne() {
        List<Source> layer = List.of(NONE, NONE, NONE, NONE, WELL, NONE, NONE, NONE, NONE);

        assertEquals(20, ((Operation) OperationChoice.choose(layer, 0)).ticks());
    }

    /** The positions of {@code count} operations in a row, each starting from the cursor the last one left. */
    private static List<Integer> positions(List<Source> layer, int cursor, int count) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Choice choice = OperationChoice.choose(layer, cursor);
            Operation operation = (Operation) choice;
            positions.add(operation.position());
            cursor = operation.nextCursor();
        }
        return positions;
    }
}
