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
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/** The choice of the Harvester's next operation over the layer under its footprint. */
class OperationChoiceTest {

    private static final Source IRON = Source.oreBlock("fieldworks:iron_ore", Set.of());
    private static final Source COPPER = Source.oreBlock("fieldworks:copper_ore", Set.of());
    private static final Source STONE_COAL = Source.harvestable("minecraft:coal_ore", Set.of("c:ores"));
    private static final Source DIAMOND = Source.harvestable("minecraft:diamond_ore", Set.of("c:ores"));
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
    void theReplacingRecipeOfASourceIsTheOneThatReplacesItsDefault() {
        Recipe<String> keepsIron = recipe("test:iron_keeps", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), false);
        Recipe<String> replacesIron = recipe("test:iron", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), true);
        Recipe<String> replacesCopper = recipe("test:copper", Set.of("fieldworks:copper_ore"), Set.of(), 40, OptionalInt.empty(), true);

        assertEquals(Optional.of(replacesIron), OperationChoice.replacingDefaultOf(List.of(keepsIron, replacesCopper, replacesIron), IRON));
        assertEquals(Optional.empty(), OperationChoice.replacingDefaultOf(List.of(keepsIron, replacesCopper), IRON));
        assertEquals(Optional.empty(), OperationChoice.replacingDefaultOf(List.of(), IRON));
        assertEquals(Optional.empty(), OperationChoice.replacingDefaultOf(List.of(replacesIron), NONE));
    }

    @Test
    void whereSeveralRecipesReplaceADefaultTheLowestIdIsTheReplacingRecipe() {
        Recipe<String> b = recipe("test:b", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), true);
        Recipe<String> a = recipe("test:a", Set.of("fieldworks:iron_ore"), Set.of(), 40, OptionalInt.empty(), true);

        assertEquals(Optional.of(a), OperationChoice.replacingDefaultOf(List.of(b, a), IRON));
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

        assertEquals(new OperationChoice.Idle<>(IdleReason.RECIPE_NEEDED),
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

    @Test
    void aWrittenRecipeThatReplacesTheDefaultLeavesAWellNeedingItHeld() {
        List<Source> layer = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> crude = shipped("crude_from_oil_well");

        assertEquals(new OperationChoice.Idle<>(IdleReason.RECIPE_NEEDED),
                OperationChoice.choose(layer, 0, List.of(crude), Optional.empty(), ready(2)));
        assertEquals(crude, ((Operation<String>) OperationChoice.choose(layer, 0, List.of(crude), Optional.of(crude), ready(2))).recipe().orElseThrow());
    }

    @Test
    void theShippedOilWellRecipeNeedsTheElectricHarvesterAndNoFluid() {
        List<Source> layer = List.of(WELL, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        Recipe<String> crude = shipped("crude_from_oil_well");

        assertEquals(OptionalInt.of(2), crude.minimumRank());
        assertEquals(Optional.empty(), crude.drillingFluid());
        assertEquals(new OperationChoice.Idle<>(IdleReason.TIER_TOO_LOW),
                OperationChoice.choose(layer, 0, List.of(crude), Optional.of(crude), ready(1)));
    }

    @Test
    void theBurnerIsRefusedOnTheGatedOresAndTheElectricNeedsItsFluid() {
        Recipe<String> uranium = shipped("uranium_from_ore");
        Recipe<String> diamond = shipped("diamond_from_ore");
        Recipe<String> netherite = shipped("netherite_scrap_from_ore");
        Machine<String> electricWithMud = machine(2, new Tank("fieldworks:drilling_mud", 10));
        Machine<String> electricWithSlurry = machine(2, new Tank("fieldworks:magma_slurry", 25));

        for (Recipe<String> gated : List.of(uranium, diamond, netherite)) {
            List<Source> layer = List.of(Source.oreBlock(gated.blocks().iterator().next(), Set.of()), NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

            assertEquals(OptionalInt.of(2), gated.minimumRank());
            assertEquals(new OperationChoice.Idle<>(IdleReason.TIER_TOO_LOW),
                    OperationChoice.choose(layer, 0, List.of(gated), Optional.of(gated), ready(1)), gated.id());
            assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID),
                    OperationChoice.choose(layer, 0, List.of(gated), Optional.of(gated), ready(2)), gated.id());
            // Without the recipe held, the Default recipe it replaces is not there to run, so the recipe must be held.
            assertEquals(new OperationChoice.Idle<>(IdleReason.RECIPE_NEEDED),
                    OperationChoice.choose(layer, 0, List.of(gated), Optional.empty(), electricWithMud), gated.id());
        }
        assertEquals(10, shipped("uranium_from_ore").drillingFluid().orElseThrow().amount());
        assertEquals(10, diamond.drillingFluid().orElseThrow().amount());
        assertEquals(25, netherite.drillingFluid().orElseThrow().amount());
        assertEquals(20, uranium.duration());
        assertEquals(60, diamond.duration());
        assertEquals(100, netherite.duration());

        List<Source> diamondOre = List.of(Source.oreBlock("fieldworks:diamond_ore", Set.of()), NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        List<Source> netheriteOre = List.of(Source.oreBlock("fieldworks:netherite_ore", Set.of()), NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        assertEquals(diamond, ((Operation<String>) OperationChoice.choose(diamondOre, 0, List.of(diamond), Optional.of(diamond), electricWithMud)).recipe().orElseThrow());
        assertEquals(netherite, ((Operation<String>) OperationChoice.choose(netheriteOre, 0, List.of(netherite), Optional.of(netherite), electricWithSlurry)).recipe().orElseThrow());
    }

    @Test
    void theOverworldFluidDoesNotServeTheNetherRecipeNorTheNetherFluidTheOverworldOne() {
        Recipe<String> netherite = shipped("netherite_scrap_from_ore");
        Recipe<String> diamond = shipped("diamond_from_ore");
        List<Source> netheriteOre = List.of(Source.oreBlock("fieldworks:netherite_ore", Set.of()), NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        List<Source> diamondOre = List.of(Source.oreBlock("fieldworks:diamond_ore", Set.of()), NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);

        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID), OperationChoice.choose(netheriteOre, 0, List.of(netherite),
                Optional.of(netherite), machine(2, new Tank("fieldworks:drilling_mud", 1_000))));
        assertEquals(new OperationChoice.Idle<>(IdleReason.NO_FLUID), OperationChoice.choose(diamondOre, 0, List.of(diamond),
                Optional.of(diamond), machine(2, new Tank("fieldworks:magma_slurry", 1_000))));
    }

    @Test
    void theCommonResourcesKeepTheirDefaultRecipeForTheBurnerAndNoGatedRecipeTouchesThem() {
        List<Source> layer = List.of(IRON, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        List<Recipe<String>> shipped = List.of(shipped("uranium_from_ore"), shipped("diamond_from_ore"),
                shipped("netherite_scrap_from_ore"), shipped("crude_from_oil_well"));

        Operation<String> operation = (Operation<String>) OperationChoice.choose(layer, 0, shipped, Optional.empty(), ready(1));

        assertEquals(Optional.empty(), operation.recipe());
        assertEquals(OperationChoice.ORE_DRAW_TICKS, operation.ticks());
    }

    private static Machine<String> machine(int tierRank, Tank tank) {
        return new Machine<>(tierRank, tank, any -> true, true);
    }

    /** A shipped Harvest Recipe as the choice reads it, from its file: its block, its minimum tier's rank and its fluid's tag. */
    private static Recipe<String> shipped(String name) {
        JsonObject json = json("/data/fieldworks/recipe/" + name + ".json");
        OptionalInt minimum = OptionalInt.empty();
        if (json.has("minimum_tier")) {
            String tier = json.get("minimum_tier").getAsString();
            minimum = OptionalInt.of(json("/data/fieldworks/harvester_tier/" + tier.substring(tier.indexOf(':') + 1) + ".json").get("rank").getAsInt());
        }
        Optional<DrillingFluid> fluid = Optional.empty();
        if (json.has("drilling_fluid")) {
            JsonObject input = json.getAsJsonObject("drilling_fluid");
            String tag = input.get("fluid").getAsString();
            Set<String> fluids = new java.util.HashSet<>();
            json("/data/fieldworks/tags/fluid/" + tag.substring(tag.indexOf(':') + 1) + ".json").getAsJsonArray("values")
                    .forEach(value -> fluids.add(value.getAsString()));
            fluid = Optional.of(new DrillingFluid(fluids, input.get("amount").getAsInt()));
        }
        return new Recipe<>(name, Set.of(json.get("source").getAsString()), Set.of(), json.get("duration").getAsInt(), minimum,
                json.has("replaces_default") && json.get("replaces_default").getAsBoolean(), fluid, name);
    }

    private static JsonObject json(String path) {
        try (var stream = OperationChoiceTest.class.getResourceAsStream(path)) {
            return new Gson().fromJson(new java.io.InputStreamReader(java.util.Objects.requireNonNull(stream, path)), JsonObject.class);
        } catch (java.io.IOException broken) {
            throw new java.io.UncheckedIOException(broken);
        }
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
            boolean replacesDefault, Optional<DrillingFluid> drillingFluid) {
        return new Recipe<>(id, blocks, tags, duration, minimumRank, replacesDefault, drillingFluid, id);
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
