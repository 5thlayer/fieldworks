// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.DrillingFluid;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Recipe;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Tank;
import org.junit.jupiter.api.Test;

/** A Harvester's tick: the Held recipe looked up, the operation's progress, and what it commits in what order. */
class HarvesterWorkTest {

    private static final Source IRON = Source.oreBlock("fieldworks:iron_ore", Set.of());
    private static final Source NONE = Source.NONE;
    private static final List<Source> TWO_IRON = List.of(IRON, NONE, NONE, NONE, IRON, NONE, NONE, NONE, NONE);

    private static final DrillingFluid MUD = new DrillingFluid(Set.of("fieldworks:drilling_mud"), 250);
    private static final Recipe<String> MUDDY_IRON = new Recipe<>("test:muddy_iron", Set.of("fieldworks:iron_ore"), Set.of(), 20,
            OptionalInt.empty(), false, Optional.of(MUD), "muddy");
    private static final Recipe<String> NUGGETS = new Recipe<>("test:iron_nuggets", Set.of("fieldworks:iron_ore"), Set.of(), 40,
            OptionalInt.empty(), false, Optional.empty(), "nuggets");
    private static final List<Recipe<String>> RECIPES = List.of(MUDDY_IRON, NUGGETS);
    private static final Map<String, Recipe<String>> BY_ID = Map.of(MUDDY_IRON.id(), MUDDY_IRON, NUGGETS.id(), NUGGETS);

    private final HarvesterWork<String> work = new HarvesterWork<>();
    private final FakePort port = new FakePort();

    @Test
    void aDefaultOperationPaysEachTickAndCompletesOnItsLast() {
        tick(TWO_IRON, Optional.empty(), 19);

        assertEquals(19, work.progress());
        assertEquals(20, work.operationTicks());
        assertEquals(List.of(), port.performed);

        assertTrue(tick(TWO_IRON, Optional.empty()));

        assertEquals(List.of(0), port.performed);
        assertEquals(0, work.progress());
        assertEquals(1, work.cursor());
        assertEquals(Optional.empty(), work.idle());
    }

    @Test
    void eachTickPaysItsPlaceInTheOperation() {
        tick(TWO_IRON, Optional.empty(), 3);

        assertEquals(List.of("pay 0/20", "pay 1/20", "pay 2/20"), port.calls);
    }

    @Test
    void onItsLastTickAnOperationTakesItsFluidThenPaysThenDoesItsWork() {
        port.tank = new Tank("fieldworks:drilling_mud", 1_000);

        tick(TWO_IRON, Optional.of(MUDDY_IRON.id()), 20);

        assertEquals(List.of("take 250", "pay 19/20", "perform 0"), port.calls.subList(19, 22));
        assertEquals(1, port.calls.stream().filter(call -> call.startsWith("take")).count());
    }

    @Test
    void aDefaultOperationTakesNoFluid() {
        port.tank = new Tank("fieldworks:drilling_mud", 1_000);

        tick(TWO_IRON, Optional.empty(), 20);

        assertTrue(port.calls.stream().noneMatch(call -> call.startsWith("take")));
    }

    @Test
    void anOperationRunsItsDurationOverTheTiersSpeed() {
        port.ticks = new HarvesterTier(1, 4.0, new HarvesterTier.Power(true, 0, 1), 0, 0)::ticks;

        tick(TWO_IRON, Optional.of(NUGGETS.id()), 9);
        assertEquals(List.of(), port.performed);
        assertEquals(10, work.operationTicks());

        tick(TWO_IRON, Optional.of(NUGGETS.id()), 1);
        assertEquals(List.of(0), port.performed);
    }

    @Test
    void aHeldRecipeIsLookedUpByItsId() {
        tick(TWO_IRON, Optional.of(NUGGETS.id()), 1);

        assertEquals(40, work.operationTicks());
    }

    /** A reload took the recipe away: the Harvester matches nothing and never falls back to the Default recipes (#10). */
    @Test
    void aHeldRecipeNoLongerLoadedIdlesWithNothingMatching() {
        assertFalse(tick(TWO_IRON, Optional.of("test:gone")));

        assertEquals(Optional.of(IdleReason.NOTHING_MATCHING), work.idle());
        assertEquals(List.of(), port.calls);
    }

    @Test
    void withNoPowerTheOperationKeepsItsProgressAndPaysNothing() {
        tick(TWO_IRON, Optional.empty(), 5);
        port.powered = false;

        assertFalse(tick(TWO_IRON, Optional.empty()));

        assertEquals(Optional.of(IdleReason.NO_POWER), work.idle());
        assertEquals(5, work.progress());
        assertEquals(5, port.calls.size());
    }

    /**
     * Nineteen ticks into a 20-tick operation the mud is gone: the last tick idles with no fluid, pays nothing and keeps
     * its progress and its place in the round, and with mud back the operation completes on the same block (#11).
     */
    @Test
    void anOperationWhoseFluidIsGoneWhenItCompletesKeepsItsPlaceAndProgress() {
        port.tank = new Tank("fieldworks:drilling_mud", 250);
        tick(TWO_IRON, Optional.of(MUDDY_IRON.id()), 19);
        port.tank = Tank.EMPTY;

        assertFalse(tick(TWO_IRON, Optional.of(MUDDY_IRON.id())));
        assertEquals(Optional.of(IdleReason.NO_FLUID), work.idle());
        assertEquals(19, work.progress());
        assertEquals(19, port.calls.size());

        port.tank = new Tank("fieldworks:drilling_mud", 250);
        assertTrue(tick(TWO_IRON, Optional.of(MUDDY_IRON.id())));
        assertEquals(List.of(0), port.performed);
        assertEquals(1, work.cursor());
    }

    @Test
    void withTheOutputFullTheOperationKeepsItsProgress() {
        tick(TWO_IRON, Optional.empty(), 5);
        port.fits = false;

        assertFalse(tick(TWO_IRON, Optional.empty()));

        assertEquals(Optional.of(IdleReason.OUTPUT_FULL), work.idle());
        assertEquals(5, work.progress());
    }

    @Test
    void theRoundGoesOnFromTheCursorAfterEachOperation() {
        tick(TWO_IRON, Optional.empty(), 60);

        assertEquals(List.of(0, 4, 0), port.performed);
    }

    @Test
    void aSavedProgressAndCursorAreTakenUp() {
        work.restore(15, 2);

        tick(TWO_IRON, Optional.empty(), 5);

        assertEquals(List.of(4), port.performed);
        assertEquals(5, work.cursor());
    }

    @Test
    void restartingLosesTheProgress() {
        tick(TWO_IRON, Optional.empty(), 5);

        work.restart();

        assertEquals(0, work.progress());
    }

    /** Today's rule, which #11's follow-up changes: progress is the Harvester's, so a new operation takes it up. */
    @Test
    void progressCarriesOverWhenTheChosenOperationChanges() {
        List<Source> layer = new ArrayList<>(TWO_IRON);
        tick(layer, Optional.empty(), 15);
        layer.set(0, NONE);

        tick(layer, Optional.empty(), 5);

        assertEquals(List.of(4), port.performed);
    }

    private boolean tick(List<Source> layer, Optional<String> held) {
        return work.tick(layer, RECIPES, BY_ID, held, port);
    }

    private void tick(List<Source> layer, Optional<String> held, int times) {
        for (int i = 0; i < times; i++) {
            tick(layer, held);
        }
    }

    /** A Harvester of rank 1 at speed ×1 with room and power, recording what it is asked to commit. */
    private static final class FakePort implements HarvesterWork.Port<String> {

        Tank tank = Tank.EMPTY;
        IntUnaryOperator ticks = duration -> duration;
        boolean fits = true;
        boolean powered = true;
        final List<String> calls = new ArrayList<>();
        final List<Integer> performed = new ArrayList<>();

        @Override
        public int tierRank() {
            return 1;
        }

        @Override
        public int ticks(int duration) {
            return ticks.applyAsInt(duration);
        }

        @Override
        public Tank tank() {
            return tank;
        }

        @Override
        public boolean fits(Operation<String> operation) {
            return fits;
        }

        @Override
        public boolean powered() {
            return powered;
        }

        @Override
        public void takeFluid(DrillingFluid need) {
            calls.add("take " + need.amount());
        }

        @Override
        public void pay(int progress, int ticks) {
            calls.add("pay " + progress + "/" + ticks);
        }

        @Override
        public void perform(Operation<String> operation) {
            calls.add("perform " + operation.position());
            performed.add(operation.position());
        }
    }
}
