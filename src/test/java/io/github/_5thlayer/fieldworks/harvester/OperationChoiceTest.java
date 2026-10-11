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
import io.github._5thlayer.fieldworks.harvester.OperationChoice.DrillingFluid;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Machine;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Recipe;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Tank;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.YieldCondition;
import org.junit.jupiter.api.Test;

/** The choice of the Harvester's next operation over the layer under its footprint. */
class OperationChoiceTest {

    private static final Source IRON = Source.oreBlock("fieldworks:iron_ore", Set.of());
    private static final Source COPPER = Source.oreBlock("fieldworks:copper_ore", Set.of());
    private static final Source STONE_COAL = Source.harvestable("minecraft:coal_ore", Set.of("c:ores"));
    private static final Source DIAMOND = Source.harvestable("minecraft:diamond_ore", Set.of("c:ores"));
    private static final Source NONE = Source.NONE;
    private static final Source WELL = Source.well("fieldworks:oil_well");
    private static final Source FLOORED_WELL = Source.flooredWell("fieldworks:oil_well");

    @Test
    void oreBlocksAreWorkedInTurnInTheLayersOrder() {
        List<Source> layer = List.of(NONE, IRON, NONE, NONE, COPPER, NONE, NONE, IRON, NONE);

        assertEquals(List.of(1, 4, 7, 1, 4), positions(layer, 0, 5));
    }

    @Test
    void theSavedCursorResumesTheRoundWhereItStopped() {
        List<Source> layer = List.of(NONE, IRON, NONE, NONE, COPPER, NONE, NONE, IRON, NONE);
        Operation<String> first = (Operation<String>) defaults(layer, 0);

        // The cursor is what is saved: handed back, the round goes on to the next ore block.
        Operation<String> resumed = (Operation<String>) defaults(layer, first.nextCursor());

        assertEquals(1, first.position());
        assertEquals(4, resumed.position());
    }

    @Test
    void aCursorPastTheLayerWrapsToItsStart() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, COPPER);

        assertEquals(0, ((Operation<String>) defaults(layer, 9)).position());
        assertEquals(8, ((Operation<String>) defaults(layer, -1)).position());
    }

    @Test
    void anOreBlockGoneFromTheLayerIsSkippedAndTheRoundGoesOn() {
        List<Source> layer = new ArrayList<>(List.of(IRON, IRON, IRON, NONE, NONE, NONE, NONE, NONE, NONE));
        Operation<String> first = (Operation<String>) defaults(layer, 0);
        layer.set(1, NONE);

        assertEquals(0, first.position());
        assertEquals(2, ((Operation<String>) defaults(layer, first.nextCursor())).position());
    }

    @Test
    void aLayerWithNoOreBlockLeavesTheHarvesterIdle() {
        List<Source> layer = List.of(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_TO_WORK), defaults(layer, 3));
    }

    @Test
    void anOreBlocksDefaultRecipeDrawsInTwentyTicksAtSpeedOne() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(20, ((Operation<String>) defaults(layer, 0)).ticks());
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

        assertEquals(20, ((Operation<String>) defaults(layer, 0)).ticks());
    }

    @Test
    void aHeldRecipeForOreIdlesWhileAWellIsUnderTheFootprint() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, WELL, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(1)));
    }

    @Test
    void aSourceWhoseDefaultARecipeReplacesIsSkippedAndTheRestOfTheLayerIsWorked() {
        List<Source> layer = List.of(IRON, COPPER, IRON, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> replacesIron = recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), true);

        assertEquals(List.of(1, 1, 1), positions(layer, 0, 3, List.of(replacesIron)));
    }

    @Test
    void aTagSourceReplacesTheDefaultOfEveryBlockInTheTag() {
        Source taggedIron = Source.oreBlock("fieldworks:iron_ore", Set.of("c:ores/iron"));
        List<Source> layer = List.of(taggedIron, COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> replacesTag = recipe("test:tagged", Set.of(), Set.of("c:ores/iron"), 40, OptionalInt.empty(), true);

        assertEquals(List.of(1, 1), positions(layer, 0, 2, List.of(replacesTag)));
    }

    @Test
    void aRecipeThatKeepsTheDefaultLeavesItsSourceInTheRound() {
        List<Source> layer = List.of(IRON, COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> bonus = recipe("test:bonus", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(List.of(0, 1, 0), positions(layer, 0, 3, List.of(bonus)));
    }

    @Test
    void aHeldRecipeRunsForItsDurationNotTheDefaults() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:slow_iron", Set.of("fieldworks:iron_ore"), Set.of(), 90, OptionalInt.empty(), false);

        Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(1));

        assertEquals(90, operation.ticks());
        assertEquals(held, operation.recipe().orElseThrow());
    }

    @Test
    void aHeldRecipeBelowItsMinimumTierIsNeverChosenAndNothingElseRunsInsteadOfIt() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:uranium_grade", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.of(2), false);

        assertEquals(new OperationChoice.Idle<>(IdleReason.TIER_TOO_LOW),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(1)));
    }

    @Test
    void aMinimumTierIsComparedByRankSoEveryTierAtOrAboveItRunsTheRecipe() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:ranked", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.of(2), false);

        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(2)).getClass());
        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(5)).getClass());
    }

    @Test
    void aHeldRecipeMatchingNothingInTheLayerIdlesRatherThanFallingBackToTheDefaults() {
        List<Source> layer = List.of(COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:iron_only", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(1)));
    }

    @Test
    void oreBlocksAndHarvestableBlocksAreWorkedInTurn() {
        List<Source> layer = List.of(IRON, NONE, STONE_COAL, NONE, DIAMOND, NONE, NONE, COPPER, NONE);

        assertEquals(List.of(0, 2, 4, 7, 0), positions(layer, 0, 5));
    }

    @Test
    void aBlockInNeitherAnOreBlockNorTheHarvestableTagIsSkipped() {
        // Source.NONE is what the layer reads for a block that is neither (dirt, a chest, air).
        List<Source> layer = List.of(NONE, DIAMOND, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(List.of(1, 1), positions(layer, 0, 2));
    }

    @Test
    void aHarvestableBlocksDefaultRecipeBreaksItInFortyTicksAtSpeedOne() {
        List<Source> layer = List.of(DIAMOND, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(40, ((Operation<String>) defaults(layer, 0)).ticks());
    }

    @Test
    void aWellUnderTheFootprintExcludesEveryHarvestableBlock() {
        List<Source> layer = List.of(DIAMOND, STONE_COAL, NONE, NONE, WELL, NONE, NONE, IRON, NONE);

        assertEquals(List.of(4, 4, 4), positions(layer, 0, 3));
    }

    @Test
    void aRecipeMatchesAHarvestableBlockByIdAndByTag() {
        List<Source> layer = List.of(DIAMOND, STONE_COAL, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> byId = recipe("test:diamond", Set.of("minecraft:diamond_ore"), Set.of(), 60, OptionalInt.empty(), true);
        Recipe<String> byTag = recipe("test:ores", Set.of(), Set.of("c:ores"), 60, OptionalInt.empty(), false);

        // The id recipe replaces the diamond's Default, so only the coal is left in the round.
        assertEquals(List.of(1, 1), positions(layer, 0, 2, List.of(byId)));
        assertEquals(List.of(0, 1, 0), positions(layer, 0, 3, List.of(byTag)));
        Operation<String> held = (Operation<String>) OperationChoice.choose(layer, 0, List.of(byId), Optional.of(byId), ready(1));
        assertEquals(0, held.position());
        assertEquals(60, held.ticks());
    }

    @Test
    void aHarvestableBlockIdlesForTheSameReasonsAsAnOreBlock() {
        List<Source> layer = List.of(DIAMOND, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> replacesDiamond = recipe("test:diamond", Set.of("minecraft:diamond_ore"), Set.of(), 60, OptionalInt.empty(), true);
        Recipe<String> muddyDiamond = recipe("test:muddy_diamond", Set.of("minecraft:diamond_ore"), Set.of(), 60, OptionalInt.empty(), false,
                Optional.of(new DrillingFluid(Set.of("fieldworks:drilling_mud"), 250)));

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_TO_WORK),
                OperationChoice.choose(layer, 0, List.of(replacesDiamond), Optional.empty(), ready(1)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), new Machine<String>(1, Tank.EMPTY, any -> false, true)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_POWER),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), new Machine<String>(1, Tank.EMPTY, any -> true, false)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.of(MUDDY_IRON), ready(1)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID),
                OperationChoice.choose(layer, 0, List.of(muddyDiamond), Optional.of(muddyDiamond), ready(1)));
    }

    @Test
    void aHeldRecipeWorksOnlyTheSourcesItMatchesInTurnAndPassesOverTheRest() {
        Source taggedCopper = Source.oreBlock("fieldworks:copper_ore", Set.of("c:ores/copper"));
        List<Source> layer = List.of(IRON, COPPER, NONE, taggedCopper, IRON, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:copper", Set.of("fieldworks:copper_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(List.of(1, 3, 1, 3), heldPositions(layer, held, 4));
    }

    @Test
    void aHeldRecipeOnATagMatchesEveryBlockInTheTagAndNoOther() {
        Source taggedIron = Source.oreBlock("fieldworks:iron_ore", Set.of("c:ores/iron"));
        List<Source> layer = List.of(COPPER, taggedIron, COPPER, NONE, NONE, NONE, NONE, NONE, taggedIron);
        Recipe<String> held = recipe("test:iron_tag", Set.of(), Set.of("c:ores/iron"), 40, OptionalInt.empty(), false);

        assertEquals(List.of(1, 8, 1), heldPositions(layer, held, 3));
    }

    @Test
    void aHeldRecipeRunsOnASourceWhoseDefaultItReplaces() {
        List<Source> layer = List.of(COPPER, IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> fluidOnly = recipe("test:iron_only", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), true);

        assertEquals(List.of(1, 1), heldPositions(layer, fluidOnly, 2));
    }

    private static final Recipe<String> MUDDY_IRON = recipe("test:muddy_iron", Set.of("fieldworks:iron_ore"), Set.of(), 40,
            OptionalInt.empty(), false, Optional.of(new DrillingFluid(Set.of("fieldworks:drilling_mud"), 250)));

    @Test
    void aHeldRecipeWithADrillingFluidIdlesWithNoFluidWhenTheTankIsEmpty() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), new Machine<String>(1, Tank.EMPTY, any -> true, true)));
    }

    @Test
    void aHeldRecipeWithADrillingFluidIdlesWithNoFluidOnTooLittleOrTheWrongFluid() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), new Machine<String>(1, new Tank("fieldworks:drilling_mud", 249), any -> true, true)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), new Machine<String>(1, new Tank("minecraft:water", 1000), any -> true, true)));
    }

    @Test
    void aHeldRecipeWithADrillingFluidRunsWithEnoughOfItInTheTank() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.of(MUDDY_IRON),
                new Machine<String>(1, new Tank("fieldworks:drilling_mud", 250), any -> true, true)).getClass());
    }

    @Test
    void aHeldRecipeMatchingNothingSaysSoEvenWithNoFluid() {
        List<Source> layer = List.of(COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_MATCHING), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), new Machine<String>(1, Tank.EMPTY, any -> true, true)));
    }

    @Test
    void anOperationWhoseResultHasNoRoomInTheOutputBufferIdlesWithOutputFull() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), new Machine<String>(1, Tank.EMPTY, any -> false, true)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), new Machine<String>(1, Tank.EMPTY, any -> false, true)));
    }

    @Test
    void roomIsAskedForTheOperationChosenSoAFullBufferForOneResultIsNotFullForAnother() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);
        Machine<String> noRoomForDefaults = new Machine<String>(1, Tank.EMPTY, operation -> operation.recipe().isPresent(), true);

        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(held), Optional.empty(), noRoomForDefaults));
        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), noRoomForDefaults).getClass());
    }

    @Test
    void anOperationWithNoPowerToSpendIdlesWithNoPower() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_POWER),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), new Machine<String>(1, Tank.EMPTY, any -> true, false)));
    }

    @Test
    void aFullOutputIsTheReasonGivenOverNoPowerSinceFuelIsNotLitForAResultWithNowhereToGo() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL), OperationChoice.choose(layer, 0, List.of(), Optional.empty(),
                new Machine<String>(1, Tank.EMPTY, any -> false, false)));
    }

    @Test
    void whatTheLayerOffersIsTheReasonGivenOverPowerOrRoom() {
        List<Source> empty = List.of(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Machine<String> stuck = new Machine<String>(1, Tank.EMPTY, any -> false, false);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_TO_WORK), OperationChoice.choose(empty, 0, List.of(), Optional.empty(), stuck));
        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID), OperationChoice.choose(List.of(IRON), 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), stuck));
    }

    @Test
    void anOperationOfAHeldRecipeNamesTheDrillingFluidItConsumes() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.of(MUDDY_IRON),
                new Machine<String>(1, new Tank("fieldworks:drilling_mud", 1_000), any -> true, true));

        assertEquals(Optional.of(new DrillingFluid(Set.of("fieldworks:drilling_mud"), 250)), operation.drillingFluid());
    }

    @Test
    void aFluidRecipeNotHeldIsNeverRunSoNothingIsConsumedHoweverMuchIsInTheTank() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Machine<String> full = new Machine<String>(1, new Tank("fieldworks:drilling_mud", 8_000), any -> true, true);

        Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.empty(), full);

        assertEquals(Optional.empty(), operation.recipe());
        assertEquals(Optional.empty(), operation.drillingFluid());
    }

    @Test
    void aHeldRecipeWithNoDrillingFluidConsumesNone() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> plain = recipe("test:plain", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, 0, List.of(plain), Optional.of(plain),
                new Machine<String>(1, new Tank("fieldworks:drilling_mud", 8_000), any -> true, true));

        assertEquals(Optional.empty(), operation.drillingFluid());
    }

    @Test
    void aWellWhoseCycleHasNoRoomInTheProductTankIdlesWithOutputFull() {
        List<Source> layer = List.of(WELL, IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> pump = recipe("test:pump", Set.of("fieldworks:oil_well"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), new Machine<String>(1, Tank.EMPTY, any -> false, true)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(pump), Optional.of(pump), new Machine<String>(1, Tank.EMPTY, any -> false, true)));
    }

    @Test
    void aFullProductTankIsTheReasonGivenOverNoPowerForAWell() {
        List<Source> layer = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL), OperationChoice.choose(layer, 0, List.of(), Optional.empty(),
                new Machine<String>(1, Tank.EMPTY, any -> false, false)));
        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_POWER),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), new Machine<String>(1, Tank.EMPTY, any -> true, false)));
    }

    @Test
    void aFullOutputBufferStillIdlesWithOutputFullForABlockSource() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), new Machine<String>(1, Tank.EMPTY, any -> false, true)));
    }

    private static Recipe<String> wellRecipe(Optional<YieldCondition> condition, boolean replacesDefault) {
        return recipe("test:crude", Set.of("fieldworks:oil_well"), Set.of(), 20, OptionalInt.empty(), replacesDefault,
                Optional.empty(), condition);
    }

    @Test
    void aHeldRecipeConditionedAtTheFloorWorksAWellAtItsFloor() {
        List<Source> layer = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), false);

        Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(1));

        assertEquals(held, operation.recipe().orElseThrow());
    }

    @Test
    void aHeldRecipeConditionedAtTheFloorIdlesOnAWellAboveItAndNeverFallsBackToTheDefault() {
        List<Source> layer = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> held = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), false);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(1)));
    }

    @Test
    void aHeldRecipeConditionedAboveTheFloorWorksAWellAboveItAndIdlesOnAFlooredOne() {
        Recipe<String> held = wellRecipe(Optional.of(YieldCondition.ABOVE_FLOOR), false);
        List<Source> above = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        List<Source> floored = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(held, ((Operation<String>) OperationChoice.choose(above, 0, List.of(held), Optional.of(held), ready(1))).recipe().orElseThrow());
        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(floored, 0, List.of(held), Optional.of(held), ready(1)));
    }

    @Test
    void aHeldRecipeWithNoYieldConditionWorksAWellAboveItsFloorAndAtIt() {
        Recipe<String> held = wellRecipe(Optional.empty(), false);

        for (Source well : List.of(WELL, FLOORED_WELL)) {
            List<Source> layer = List.of(well, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
            assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), ready(1)).getClass());
        }
    }

    @Test
    void aYieldConditionedRecipeNotHeldNeverRunsAndALoneWellIsPumpedByItsDefault() {
        List<Source> layer = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> atFloor = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), false);

        Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, 0, List.of(atFloor), Optional.empty(), ready(1));

        assertEquals(Optional.empty(), operation.recipe());
        assertEquals(OperationChoice.WELL_PUMP_TICKS, operation.ticks());
    }

    @Test
    void aRecipeThatReplacesTheDefaultReplacesItOnlyWhereItsYieldConditionHolds() {
        Recipe<String> replaces = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), true);
        List<Source> floored = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        List<Source> above = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_TO_WORK),
                OperationChoice.choose(floored, 0, List.of(replaces), Optional.empty(), ready(1)));
        assertEquals(Operation.class, OperationChoice.choose(above, 0, List.of(replaces), Optional.empty(), ready(1)).getClass());
    }

    @Test
    void aYieldConditionMatchesNoSourceButAWell() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> odd = recipe("test:odd", Set.of("fieldworks:iron_ore"), Set.of(), 20, OptionalInt.empty(), false,
                Optional.empty(), Optional.of(YieldCondition.ABOVE_FLOOR));

        assertEquals(new OperationChoice.Idle<>(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(odd), Optional.of(odd), ready(1)));
    }

    /** A powered Harvester of this rank with an empty tank and room for anything. */
    private static Machine<String> ready(int tierRank) {
        return new Machine<>(tierRank, Tank.EMPTY, any -> true, true);
    }

    /** The choice over the Default recipes alone, by a Harvester of rank 1 ready to work. */
    private static Choice<String> defaults(List<Source> layer, int cursor) {
        return OperationChoice.choose(layer, cursor, List.of(), Optional.empty(), ready(1));
    }

    /** A recipe whose result, which the choice only carries, is its id. */
    private static Recipe<String> recipe(String id, Set<String> blocks, Set<String> tags, int duration, OptionalInt minimumRank,
            boolean replacesDefault, Optional<DrillingFluid> drillingFluid, Optional<YieldCondition> yieldCondition) {
        return new Recipe<>(id, blocks, tags, duration, minimumRank, replacesDefault, drillingFluid, yieldCondition, id);
    }

    private static Recipe<String> recipe(String id, Set<String> blocks, Set<String> tags, int duration, OptionalInt minimumRank,
            boolean replacesDefault, Optional<DrillingFluid> drillingFluid) {
        return recipe(id, blocks, tags, duration, minimumRank, replacesDefault, drillingFluid, Optional.empty());
    }

    private static Recipe<String> recipe(String id, Set<String> blocks, Set<String> tags, int duration, OptionalInt minimumRank,
            boolean replacesDefault) {
        return recipe(id, blocks, tags, duration, minimumRank, replacesDefault, Optional.empty());
    }

    private static List<Integer> heldPositions(List<Source> layer, Recipe<String> held, int count) {
        List<Integer> positions = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < count; i++) {
            Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, cursor, List.of(held), Optional.of(held), ready(1));
            positions.add(operation.position());
            cursor = operation.nextCursor();
        }
        return positions;
    }

    /** The positions of {@code count} operations in a row, each starting from the cursor the last one left. */
    private static List<Integer> positions(List<Source> layer, int cursor, int count) {
        return positions(layer, cursor, count, List.of());
    }

    private static List<Integer> positions(List<Source> layer, int cursor, int count, List<Recipe<String>> recipes) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Choice<String> choice = OperationChoice.choose(layer, cursor, recipes, Optional.empty(), ready(1));
            Operation<String> operation = (Operation<String>) choice;
            positions.add(operation.position());
            cursor = operation.nextCursor();
        }
        return positions;
    }
}
