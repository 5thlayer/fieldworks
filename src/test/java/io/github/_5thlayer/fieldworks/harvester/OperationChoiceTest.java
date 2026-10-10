// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.Choice;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Recipe;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import org.junit.jupiter.api.Test;

/** The choice of the Harvester's next operation over the layer under its footprint. */
class OperationChoiceTest {

    private static final Source IRON = Source.oreBlock("fieldworks:iron_ore");
    private static final Source COPPER = Source.oreBlock("fieldworks:copper_ore");
    private static final Source NONE = Source.NONE;

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
    void aSourceWhoseDefaultARecipeReplacesIsSkippedAndTheRestOfTheLayerIsWorked() {
        List<Source> layer = List.of(IRON, COPPER, IRON, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe replacesIron = new Recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), true);

        assertEquals(List.of(1, 1, 1), positions(layer, 0, 3, List.of(replacesIron)));
    }

    @Test
    void aTagSourceReplacesTheDefaultOfEveryBlockInTheTag() {
        Source taggedIron = Source.oreBlock("fieldworks:iron_ore", Set.of("c:ores/iron"));
        List<Source> layer = List.of(taggedIron, COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe replacesTag = new Recipe("test:tagged", Set.of(), Set.of("c:ores/iron"), 40, OptionalInt.empty(), true);

        assertEquals(List.of(1, 1), positions(layer, 0, 2, List.of(replacesTag)));
    }

    @Test
    void aRecipeThatKeepsTheDefaultLeavesItsSourceInTheRound() {
        List<Source> layer = List.of(IRON, COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe bonus = new Recipe("test:bonus", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(List.of(0, 1, 0), positions(layer, 0, 3, List.of(bonus)));
    }

    @Test
    void aHeldRecipeRunsForItsDurationNotTheDefaults() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:slow_iron", Set.of("fieldworks:iron_ore"), Set.of(), 90, OptionalInt.empty(), false);

        Operation operation = (Operation) OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1);

        assertEquals(90, operation.ticks());
        assertEquals(held, operation.recipe().orElseThrow());
    }

    @Test
    void aHeldRecipeBelowItsMinimumTierIsNeverChosenAndNothingElseRunsInsteadOfIt() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:uranium_grade", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.of(2), false);

        assertEquals(new OperationChoice.Idle(IdleReason.TIER_TOO_LOW),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1));
    }

    @Test
    void aMinimumTierIsComparedByRankSoEveryTierAtOrAboveItRunsTheRecipe() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:ranked", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.of(2), false);

        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 2).getClass());
        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 5).getClass());
    }

    @Test
    void aHeldRecipeMatchingNothingInTheLayerIdlesRatherThanFallingBackToTheDefaults() {
        List<Source> layer = List.of(COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:iron_only", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_TO_WORK),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1));
    }

    /** The positions of {@code count} operations in a row, each starting from the cursor the last one left. */
    private static List<Integer> positions(List<Source> layer, int cursor, int count) {
        return positions(layer, cursor, count, List.of());
    }

    private static List<Integer> positions(List<Source> layer, int cursor, int count, List<Recipe> recipes) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Choice choice = OperationChoice.choose(layer, cursor, recipes, Optional.empty(), 1);
            Operation operation = (Operation) choice;
            positions.add(operation.position());
            cursor = operation.nextCursor();
        }
        return positions;
    }
}
