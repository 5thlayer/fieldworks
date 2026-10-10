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

    private static final Source IRON = Source.oreBlock("fieldworks:iron_ore");
    private static final Source COPPER = Source.oreBlock("fieldworks:copper_ore");
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

    @Test
    void aHeldRecipeForOreIdlesWhileAWellIsUnderTheFootprint() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, WELL, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1));
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

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1));
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

        assertEquals(40, ((Operation) OperationChoice.choose(layer, 0)).ticks());
    }

    @Test
    void aWellUnderTheFootprintExcludesEveryHarvestableBlock() {
        List<Source> layer = List.of(DIAMOND, STONE_COAL, NONE, NONE, WELL, NONE, NONE, IRON, NONE);

        assertEquals(List.of(4, 4, 4), positions(layer, 0, 3));
    }

    @Test
    void aRecipeMatchesAHarvestableBlockByIdAndByTag() {
        List<Source> layer = List.of(DIAMOND, STONE_COAL, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe byId = new Recipe("test:diamond", Set.of("minecraft:diamond_ore"), Set.of(), 60, OptionalInt.empty(), true);
        Recipe byTag = new Recipe("test:ores", Set.of(), Set.of("c:ores"), 60, OptionalInt.empty(), false);

        // The id recipe replaces the diamond's Default, so only the coal is left in the round.
        assertEquals(List.of(1, 1), positions(layer, 0, 2, List.of(byId)));
        assertEquals(List.of(0, 1, 0), positions(layer, 0, 3, List.of(byTag)));
        Operation held = (Operation) OperationChoice.choose(layer, 0, List.of(byId), Optional.of(byId), 1);
        assertEquals(0, held.position());
        assertEquals(60, held.ticks());
    }

    @Test
    void aHarvestableBlockIdlesForTheSameReasonsAsAnOreBlock() {
        List<Source> layer = List.of(DIAMOND, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe replacesDiamond = new Recipe("test:diamond", Set.of("minecraft:diamond_ore"), Set.of(), 60, OptionalInt.empty(), true);
        Recipe muddyDiamond = new Recipe("test:muddy_diamond", Set.of("minecraft:diamond_ore"), Set.of(), 60, OptionalInt.empty(), false,
                Optional.of(new DrillingFluid(Set.of("fieldworks:drilling_mud"), 250)));

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_TO_WORK),
                OperationChoice.choose(layer, 0, List.of(replacesDiamond), Optional.empty(), Machine.ready(1)));
        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), Machine.ready(1).withFits(operation -> false)));
        assertEquals(new OperationChoice.Idle(IdleReason.NO_POWER),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), Machine.ready(1).withPower(false)));
        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.of(MUDDY_IRON), Machine.ready(1)));
        assertEquals(new OperationChoice.Idle(IdleReason.NO_FLUID),
                OperationChoice.choose(layer, 0, List.of(muddyDiamond), Optional.of(muddyDiamond), Machine.ready(1)));
    }

    @Test
    void aHeldRecipeWorksOnlyTheSourcesItMatchesInTurnAndPassesOverTheRest() {
        Source taggedCopper = Source.oreBlock("fieldworks:copper_ore", Set.of("c:ores/copper"));
        List<Source> layer = List.of(IRON, COPPER, NONE, taggedCopper, IRON, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:copper", Set.of("fieldworks:copper_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(List.of(1, 3, 1, 3), heldPositions(layer, held, 4));
    }

    @Test
    void aHeldRecipeOnATagMatchesEveryBlockInTheTagAndNoOther() {
        Source taggedIron = Source.oreBlock("fieldworks:iron_ore", Set.of("c:ores/iron"));
        List<Source> layer = List.of(COPPER, taggedIron, COPPER, NONE, NONE, NONE, NONE, NONE, taggedIron);
        Recipe held = new Recipe("test:iron_tag", Set.of(), Set.of("c:ores/iron"), 40, OptionalInt.empty(), false);

        assertEquals(List.of(1, 8, 1), heldPositions(layer, held, 3));
    }

    @Test
    void aHeldRecipeRunsOnASourceWhoseDefaultItReplaces() {
        List<Source> layer = List.of(COPPER, IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe fluidOnly = new Recipe("test:iron_only", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), true);

        assertEquals(List.of(1, 1), heldPositions(layer, fluidOnly, 2));
    }

    private static final Recipe MUDDY_IRON = new Recipe("test:muddy_iron", Set.of("fieldworks:iron_ore"), Set.of(), 40,
            OptionalInt.empty(), false, Optional.of(new DrillingFluid(Set.of("fieldworks:drilling_mud"), 250)));

    @Test
    void aHeldRecipeWithADrillingFluidIdlesWithNoFluidWhenTheTankIsEmpty() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.NO_FLUID), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), Machine.ready(1).withTank(Tank.EMPTY)));
    }

    @Test
    void aHeldRecipeWithADrillingFluidIdlesWithNoFluidOnTooLittleOrTheWrongFluid() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.NO_FLUID), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), Machine.ready(1).withTank(new Tank("fieldworks:drilling_mud", 249))));
        assertEquals(new OperationChoice.Idle(IdleReason.NO_FLUID), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), Machine.ready(1).withTank(new Tank("minecraft:water", 1000))));
    }

    @Test
    void aHeldRecipeWithADrillingFluidRunsWithEnoughOfItInTheTank() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.of(MUDDY_IRON),
                Machine.ready(1).withTank(new Tank("fieldworks:drilling_mud", 250))).getClass());
    }

    @Test
    void aHeldRecipeMatchingNothingSaysSoEvenWithNoFluid() {
        List<Source> layer = List.of(COPPER, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_MATCHING), OperationChoice.choose(layer, 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), Machine.ready(1).withTank(Tank.EMPTY)));
    }

    @Test
    void anOperationWhoseResultHasNoRoomInTheOutputBufferIdlesWithOutputFull() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), Machine.ready(1).withFits(operation -> false)));
        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), Machine.ready(1).withFits(operation -> false)));
    }

    @Test
    void roomIsAskedForTheOperationChosenSoAFullBufferForOneResultIsNotFullForAnother() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = new Recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);
        Machine noRoomForDefaults = Machine.ready(1).withFits(operation -> operation.recipe().isPresent());

        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(held), Optional.empty(), noRoomForDefaults));
        assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), noRoomForDefaults).getClass());
    }

    @Test
    void anOperationWithNoPowerToSpendIdlesWithNoPower() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.NO_POWER),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), Machine.ready(1).withPower(false)));
    }

    @Test
    void aFullOutputIsTheReasonGivenOverNoPowerSinceFuelIsNotLitForAResultWithNowhereToGo() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL), OperationChoice.choose(layer, 0, List.of(), Optional.empty(),
                Machine.ready(1).withFits(operation -> false).withPower(false)));
    }

    @Test
    void whatTheLayerOffersIsTheReasonGivenOverPowerOrRoom() {
        List<Source> empty = List.of(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Machine stuck = Machine.ready(1).withFits(operation -> false).withPower(false);

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_TO_WORK), OperationChoice.choose(empty, 0, List.of(), Optional.empty(), stuck));
        assertEquals(new OperationChoice.Idle(IdleReason.NO_FLUID), OperationChoice.choose(List.of(IRON), 0, List.of(MUDDY_IRON),
                Optional.of(MUDDY_IRON), stuck));
    }

    @Test
    void anOperationOfAHeldRecipeNamesTheDrillingFluidItConsumes() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        Operation operation = (Operation) OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.of(MUDDY_IRON),
                Machine.ready(1).withTank(new Tank("fieldworks:drilling_mud", 1_000)));

        assertEquals(Optional.of(new DrillingFluid(Set.of("fieldworks:drilling_mud"), 250)), operation.drillingFluid());
    }

    @Test
    void aFluidRecipeNotHeldIsNeverRunSoNothingIsConsumedHoweverMuchIsInTheTank() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Machine full = Machine.ready(1).withTank(new Tank("fieldworks:drilling_mud", 8_000));

        Operation operation = (Operation) OperationChoice.choose(layer, 0, List.of(MUDDY_IRON), Optional.empty(), full);

        assertEquals(Optional.empty(), operation.recipe());
        assertEquals(Optional.empty(), operation.drillingFluid());
    }

    @Test
    void aHeldRecipeWithNoDrillingFluidConsumesNone() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe plain = new Recipe("test:plain", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);

        Operation operation = (Operation) OperationChoice.choose(layer, 0, List.of(plain), Optional.of(plain),
                Machine.ready(1).withTank(new Tank("fieldworks:drilling_mud", 8_000)));

        assertEquals(Optional.empty(), operation.drillingFluid());
    }

    @Test
    void aWellWhoseCycleHasNoRoomInTheProductTankIdlesWithOutputFull() {
        List<Source> layer = List.of(WELL, IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe pump = new Recipe("test:pump", Set.of("fieldworks:oil_well"), Set.of(), 40, OptionalInt.empty(), false);

        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), Machine.ready(1).withFits(operation -> false)));
        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(pump), Optional.of(pump), Machine.ready(1).withFits(operation -> false)));
    }

    @Test
    void aFullProductTankIsTheReasonGivenOverNoPowerForAWell() {
        List<Source> layer = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL), OperationChoice.choose(layer, 0, List.of(), Optional.empty(),
                Machine.ready(1).withFits(operation -> false).withPower(false)));
        assertEquals(new OperationChoice.Idle(IdleReason.NO_POWER),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), Machine.ready(1).withPower(false)));
    }

    @Test
    void aFullOutputBufferStillIdlesWithOutputFullForABlockSource() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.OUTPUT_FULL),
                OperationChoice.choose(layer, 0, List.of(), Optional.empty(), Machine.ready(1).withFits(operation -> false)));
    }

    private static Recipe wellRecipe(Optional<YieldCondition> condition, boolean replacesDefault) {
        return new Recipe("test:crude", Set.of("fieldworks:oil_well"), Set.of(), 20, OptionalInt.empty(), replacesDefault,
                Optional.empty(), condition);
    }

    @Test
    void aHeldRecipeConditionedAtTheFloorWorksAWellAtItsFloor() {
        List<Source> layer = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), false);

        Operation operation = (Operation) OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1);

        assertEquals(held, operation.recipe().orElseThrow());
    }

    @Test
    void aHeldRecipeConditionedAtTheFloorIdlesOnAWellAboveItAndNeverFallsBackToTheDefault() {
        List<Source> layer = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe held = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), false);

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1));
    }

    @Test
    void aHeldRecipeConditionedAboveTheFloorWorksAWellAboveItAndIdlesOnAFlooredOne() {
        Recipe held = wellRecipe(Optional.of(YieldCondition.ABOVE_FLOOR), false);
        List<Source> above = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        List<Source> floored = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(held, ((Operation) OperationChoice.choose(above, 0, List.of(held), Optional.of(held), 1)).recipe().orElseThrow());
        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(floored, 0, List.of(held), Optional.of(held), 1));
    }

    @Test
    void aHeldRecipeWithNoYieldConditionWorksAWellAboveItsFloorAndAtIt() {
        Recipe held = wellRecipe(Optional.empty(), false);

        for (Source well : List.of(WELL, FLOORED_WELL)) {
            List<Source> layer = List.of(well, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
            assertEquals(Operation.class, OperationChoice.choose(layer, 0, List.of(held), Optional.of(held), 1).getClass());
        }
    }

    @Test
    void aYieldConditionedRecipeNotHeldNeverRunsAndALoneWellIsPumpedByItsDefault() {
        List<Source> layer = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe atFloor = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), false);

        Operation operation = (Operation) OperationChoice.choose(layer, 0, List.of(atFloor), Optional.empty(), 1);

        assertEquals(Optional.empty(), operation.recipe());
        assertEquals(OperationChoice.WELL_PUMP_TICKS, operation.ticks());
    }

    @Test
    void aRecipeThatReplacesTheDefaultReplacesItOnlyWhereItsYieldConditionHolds() {
        Recipe replaces = wellRecipe(Optional.of(YieldCondition.AT_FLOOR), true);
        List<Source> floored = List.of(FLOORED_WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        List<Source> above = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_TO_WORK),
                OperationChoice.choose(floored, 0, List.of(replaces), Optional.empty(), 1));
        assertEquals(Operation.class, OperationChoice.choose(above, 0, List.of(replaces), Optional.empty(), 1).getClass());
    }

    @Test
    void aYieldConditionMatchesNoSourceButAWell() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe odd = new Recipe("test:odd", Set.of("fieldworks:iron_ore"), Set.of(), 20, OptionalInt.empty(), false,
                Optional.empty(), Optional.of(YieldCondition.ABOVE_FLOOR));

        assertEquals(new OperationChoice.Idle(IdleReason.NOTHING_MATCHING),
                OperationChoice.choose(layer, 0, List.of(odd), Optional.of(odd), 1));
    }

    private static List<Integer> heldPositions(List<Source> layer, Recipe held, int count) {
        List<Integer> positions = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < count; i++) {
            Operation operation = (Operation) OperationChoice.choose(layer, cursor, List.of(held), Optional.of(held), 1);
            positions.add(operation.position());
            cursor = operation.nextCursor();
        }
        return positions;
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
