// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The choice of the Harvester's next operation: given the sources in the layer under its footprint, in
 * a fixed order, and the round-robin cursor the Harvester saves, the next operation or why there is
 * none. It reads no game state, so it is tested on a plain JVM; the block entity reads the world into
 * {@link Source}s and carries the operation out.
 */
public final class OperationChoice {

    /** An ore block's Default recipe: one draw, in this many ticks at speed ×1. */
    static final int ORE_DRAW_TICKS = 20;

    /** A harvestable block's Default recipe: one break for its loot, in this many ticks at speed ×1. */
    static final int BREAK_TICKS = 40;

    /** A well's Default recipe: one cycle of its yield and decay, in this many ticks at speed ×1. */
    static final int WELL_PUMP_TICKS = 20;

    private OperationChoice() {
    }

    /**
     * The next operation from {@code cursor} round the layer: the first source at or after it that can
     * be worked, wrapping at the end. Ore blocks and harvestable blocks are worked alike, in turn. While any well
     * is in the layer, only wells can be worked. Positions
     * are the layer's indices, so a source that goes, an ore block turned to stone, leaves the others'
     * places in the round as they were.
     *
     * <p>With no Held recipe only Default recipes run, and a source whose Default recipe a recipe in {@code recipes}
     * replaces is skipped. A Held recipe works only the sources it matches, and when it cannot run the Harvester
     * idles with the reason and never falls back to the Default recipes (#10). {@code machine} is the Harvester's
     * own state.
     */
    public static <R> Choice<R> choose(List<Source> layer, int cursor, List<Recipe<R>> recipes, Optional<Recipe<R>> held, Machine<R> machine) {
        if (held.isPresent() && held.get().minimumRank().isPresent() && machine.tierRank() < held.get().minimumRank().getAsInt()) {
            return new Idle<>(IdleReason.TIER_TOO_LOW);
        }
        // Any well under the footprint excludes every block source (#1).
        boolean wells = layer.stream().anyMatch(source -> source.kind() == Kind.WELL);
        boolean replaced = false;
        int size = layer.size();
        for (int step = 0; step < size; step++) {
            int position = Math.floorMod(cursor + step, size);
            Source source = layer.get(position);
            int next = (position + 1) % size;
            if (wells ? source.kind() != Kind.WELL : source.kind() == Kind.NONE || source.kind() == Kind.WELL) {
                continue;
            }
            if (held.isPresent()) {
                if (held.get().matches(source)) {
                    if (!held.get().drillingFluid().map(machine.tank()::holds).orElse(true)) {
                        return new Idle<>(IdleReason.NO_FLUID);
                    }
                    return machine.run(new Operation<>(position, held.get().duration(), next, held));
                }
            } else if (replacingDefaultOf(recipes, source).isEmpty()) {
                return machine.run(new Operation<>(position, defaultTicks(source.kind()), next, Optional.empty()));
            } else {
                replaced = true;
            }
        }
        return new Idle<>(held.isPresent() ? IdleReason.NOTHING_MATCHING : replaced ? IdleReason.RECIPE_NEEDED : IdleReason.NOTHING_TO_WORK);
    }

    /**
     * The recipe that takes away {@code source}'s Default recipe, or empty when its Default recipe stands. The hand
     * draw reads this too (ADR 0010), so the hand and the Harvester can never disagree. Where several replace it, the
     * lowest id answers, so the answer doesn't depend on the order the recipes were loaded in.
     */
    public static <R> Optional<Recipe<R>> replacingDefaultOf(List<Recipe<R>> recipes, Source source) {
        return recipes.stream().filter(recipe -> recipe.replacesDefaultOf(source)).min(Comparator.comparing(Recipe::id));
    }

    /**
     * What the choice reads of the Harvester itself: its tier's rank, its Drilling Fluid tank, whether its output
     * buffer has room for an operation's result, and whether it has a tick of power to spend.
     */
    public record Machine<R>(int tierRank, Tank tank, Predicate<Operation<R>> fits, boolean powered) {

        public Machine {
            Objects.requireNonNull(tank);
            Objects.requireNonNull(fits);
        }

        /** The operation, or why the Harvester cannot run it now: a result with nowhere to go comes before power. */
        Choice<R> run(Operation<R> operation) {
            if (!fits.test(operation)) {
                return new Idle<>(IdleReason.OUTPUT_FULL);
            }
            return powered ? operation : new Idle<>(IdleReason.NO_POWER);
        }
    }

    /** What the Drilling Fluid tank holds: a fluid id and its mB, or nothing. */
    public record Tank(String fluid, int amount) {

        public static final Tank EMPTY = new Tank("", 0);

        public Tank {
            Objects.requireNonNull(fluid);
        }

        boolean holds(DrillingFluid need) {
            return need.fluids().contains(fluid) && amount >= need.amount();
        }
    }

    /** The Drilling Fluid a Harvest Recipe consumes: the fluids that serve, by id, and the mB per operation. */
    public record DrillingFluid(Set<String> fluids, int amount) {

        public DrillingFluid {
            fluids = Set.copyOf(fluids);
        }
    }

    private static int defaultTicks(Kind kind) {
        return switch (kind) {
            case WELL -> WELL_PUMP_TICKS;
            case HARVESTABLE -> BREAK_TICKS;
            case ORE_BLOCK, NONE -> ORE_DRAW_TICKS;
        };
    }

    /** What stands at one position of the layer. */
    public enum Kind {
        /** Nothing the Harvester works. */
        NONE,
        ORE_BLOCK,
        /** A block in {@code fieldworks:harvestable} that is not an ore block: broken for its loot. */
        HARVESTABLE,
        WELL
    }

    /**
     * One position of the layer under the footprint: what stands there, its block id and the block tags it is in.
     */
    public record Source(Kind kind, String block, Set<String> tags) {

        public static final Source NONE = new Source(Kind.NONE, "", Set.of());

        public Source {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(block);
            tags = Set.copyOf(tags);
        }

        public static Source oreBlock(String block, Set<String> tags) {
            return new Source(Kind.ORE_BLOCK, block, tags);
        }

        public static Source harvestable(String block, Set<String> tags) {
            return new Source(Kind.HARVESTABLE, block, tags);
        }

        public static Source well(String block) {
            return new Source(Kind.WELL, block, Set.of());
        }
    }

    /**
     * A Harvest Recipe as the choice reads it: the blocks and block tags it matches, its duration in ticks at
     * speed ×1, the lowest tier rank that may run it if it names one, whether it removes its sources'
     * Default recipes, the Drilling Fluid it consumes, if any.
     * {@code result} is what it yields, which the choice carries to the operation and never reads.
     */
    public record Recipe<R>(String id, Set<String> blocks, Set<String> tags, int duration, OptionalInt minimumRank,
            boolean replacesDefault, Optional<DrillingFluid> drillingFluid, R result) {

        public Recipe {
            Objects.requireNonNull(id);
            blocks = Set.copyOf(blocks);
            tags = Set.copyOf(tags);
            Objects.requireNonNull(drillingFluid);
            Objects.requireNonNull(result);
        }

        boolean matches(Source source) {
            return source.kind() != Kind.NONE
                    && (blocks.contains(source.block()) || source.tags().stream().anyMatch(tags::contains));
        }

        boolean replacesDefaultOf(Source source) {
            return replacesDefault && matches(source);
        }
    }

    /** Why the Harvester has no operation to run. */
    public enum IdleReason {
        /** With no Held recipe, no source in the layer has a Default recipe to run, nor one a written recipe replaces. */
        NOTHING_TO_WORK,
        /**
         * With no Held recipe, the layer's only sources are ones whose Default recipe a written recipe replaces, so
         * that recipe must be held to work them, as netherite's must.
         */
        RECIPE_NEEDED,
        /** The Held recipe matches no source in the layer (#10); it never falls back to the Default recipes. */
        NOTHING_MATCHING,
        /** The Held recipe names a minimum tier above the Harvester's rank. */
        TIER_TOO_LOW,
        /** The Held recipe's Drilling Fluid is not in the tank, or not enough of it for one operation. */
        NO_FLUID,
        /** No tick of power to spend: no burn left and no fuel to light. */
        NO_POWER,
        /** The output buffer, or for a well's crude the product tank, has no room for the operation's result. */
        OUTPUT_FULL
    }

    /** What {@link #choose} returns: an operation, or why there is none. */
    public sealed interface Choice<R> permits Operation, Idle {
    }

    /**
     * Work the source at {@code position} for {@code ticks} at speed ×1, then save {@code nextCursor}
     * so the round goes on from the position after it. {@code recipe} is the Harvest Recipe it runs, empty
     * for a Default recipe.
     */
    public record Operation<R>(int position, int ticks, int nextCursor, Optional<Recipe<R>> recipe) implements Choice<R> {

        /** The Drilling Fluid the operation consumes when it completes: only a Held recipe's, never a Default's (#11). */
        public Optional<DrillingFluid> drillingFluid() {
            return recipe.flatMap(Recipe::drillingFluid);
        }
    }

    public record Idle<R>(IdleReason reason) implements Choice<R> {
    }
}
