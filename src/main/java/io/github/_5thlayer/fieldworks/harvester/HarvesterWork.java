// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.Choice;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.DrillingFluid;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Idle;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Machine;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Recipe;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Tank;
import org.jspecify.annotations.Nullable;

/**
 * What a Harvester does on a tick: the Held recipe looked up, the next operation chosen by {@link OperationChoice},
 * and, with one chosen, a tick of it paid for and, on its last tick, its Drilling Fluid taken and its work done. It
 * keeps the operation's progress, which a stall keeps and turning to another source or recipe loses, the round's
 * cursor and why the last tick did no work. It reads no game state, so it is tested on a plain JVM; the block entity
 * reads the world through a {@link Port} and saves the progress and cursor.
 */
final class HarvesterWork<R> {

    /**
     * The Harvester as its work reads and changes it. The choice is trusted: an operation it names has room, power and
     * its Drilling Fluid, so the commits that follow it cannot fail.
     */
    interface Port<R> {

        int tierRank();

        /** How long an operation of {@code duration} ticks at speed ×1 takes on the Harvester's tier. */
        int ticks(int duration);

        /** The Drilling Fluid tank, as the choice reads it. */
        Tank tank();

        /** Whether the output has room for what {@code operation} yields. */
        boolean fits(Operation<R> operation);

        /** Whether a tick of work could be paid for now, spending nothing. */
        boolean powered();

        /** Takes one operation's Drilling Fluid from the tank. */
        void takeFluid(DrillingFluid need);

        /** Pays for the tick at {@code progress} of an operation {@code ticks} long. */
        void pay(int progress, int ticks);

        /** Does {@code operation}'s work: the draw, the break or the pump, its result to the output. */
        void perform(Operation<R> operation);
    }

    private int progress;
    /**
     * The operation {@link #progress} belongs to, or null after a load, when the first operation chosen takes it up: no
     * save keeps it.
     */
    private @Nullable Underway underway;
    private int cursor;
    /** Ticks the operation under way takes on the tier, for the menu's progress bar; not saved, set as it works (#13). */
    private int operationTicks;
    /** Why the last tick did no work, or null if it worked; read again every tick, so never saved. */
    private @Nullable IdleReason idle;

    /**
     * One tick: the operation the choice names over {@code layer} advanced by a tick, or the reason there is none.
     * {@code held} is the Held recipe's id, looked up in {@code byId}; one a reload removed matches nothing, and never
     * falls back to the Default recipes (#10). Returns whether the tick was paid for and progressed.
     */
    boolean tick(List<Source> layer, List<Recipe<R>> recipes, Map<String, Recipe<R>> byId, Optional<String> held, Port<R> port) {
        Optional<Recipe<R>> heldRecipe = held.map(byId::get);
        if (held.isPresent() && heldRecipe.isEmpty()) {
            idle = IdleReason.NOTHING_MATCHING;
            return false;
        }
        Machine<R> machine = new Machine<>(port.tierRank(), port.tank(), port::fits, port.powered());
        Choice<R> choice = OperationChoice.choose(layer, cursor, recipes, heldRecipe, machine);
        if (!(choice instanceof Operation<R> operation)) {
            idle = ((Idle<R>) choice).reason();
            return false;
        }
        Underway chosen = Underway.of(operation);
        if (underway != null && !underway.equals(chosen)) {
            progress = 0;
        }
        underway = chosen;
        int ticks = port.ticks(operation.ticks());
        boolean completes = progress + 1 >= ticks;
        if (completes) {
            operation.drillingFluid().ifPresent(port::takeFluid);
        }
        port.pay(progress, ticks);
        idle = null;
        progress++;
        operationTicks = ticks;
        if (completes) {
            port.perform(operation);
            progress = 0;
            cursor = operation.nextCursor();
        }
        return true;
    }

    /** An operation as its progress knows it: the position it works and the recipe it runs, empty for a Default. */
    private record Underway(int position, Optional<String> recipe) {

        static Underway of(Operation<?> operation) {
            return new Underway(operation.position(), operation.recipe().map(Recipe::id));
        }
    }

    /** Ticks into the operation under way. */
    int progress() {
        return progress;
    }

    /** Where the round goes on from: the position after the last operation completed. */
    int cursor() {
        return cursor;
    }

    /** Ticks the operation under way takes on the tier; 0 before the first tick of work. */
    int operationTicks() {
        return operationTicks;
    }

    /** Why the last tick did no work, or empty if it worked. */
    Optional<IdleReason> idle() {
        return Optional.ofNullable(idle);
    }

    /** Takes up the progress and cursor a save kept. */
    void restore(int progress, int cursor) {
        this.progress = progress;
        underway = null;
        this.cursor = cursor;
    }

    /** Loses the progress on the operation under way: the Held recipe changed. */
    void restart() {
        progress = 0;
    }
}
