// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

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
     */
    public static Choice choose(List<Source> layer, int cursor) {
        return choose(layer, cursor, List.of(), Optional.empty(), 0);
    }

    /**
     * As {@link #choose(List, int)}, over the Harvest Recipes on offer. With no Held recipe only Default
     * recipes run, and a source whose Default recipe a recipe in {@code recipes} replaces is skipped.
     * {@code tierRank} is the rank of the Harvester Tier doing the work.
     */
    public static Choice choose(List<Source> layer, int cursor, List<Recipe> recipes, Optional<Recipe> held, int tierRank) {
        return choose(layer, cursor, recipes, held, Machine.ready(tierRank));
    }

    /**
     * As {@link #choose(List, int, List, Optional, int)}, for a Harvester in the state {@code machine} reads. A
     * Held recipe works only the sources it matches, and when it cannot run the Harvester idles with the reason
     * and never falls back to the Default recipes (#10).
     */
    public static Choice choose(List<Source> layer, int cursor, List<Recipe> recipes, Optional<Recipe> held, Machine machine) {
        if (held.isPresent() && held.get().minimumRank().isPresent() && machine.tierRank() < held.get().minimumRank().getAsInt()) {
            return new Idle(IdleReason.TIER_TOO_LOW);
        }
        // Any well under the footprint excludes every block source (#1).
        boolean wells = layer.stream().anyMatch(source -> source.kind() == Kind.WELL);
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
                        return new Idle(IdleReason.NO_FLUID);
                    }
                    return machine.run(new Operation(position, held.get().duration(), next, held), source);
                }
            } else if (recipes.stream().noneMatch(recipe -> recipe.replacesDefaultOf(source))) {
                return machine.run(new Operation(position, defaultTicks(source.kind()), next, Optional.empty()), source);
            }
        }
        return new Idle(held.isPresent() ? IdleReason.NOTHING_MATCHING : IdleReason.NOTHING_TO_WORK);
    }

    /**
     * What the choice reads of the Harvester itself: its tier's rank, its Drilling Fluid tank, whether its output
     * buffer has room for an operation's result, and whether it has a tick of power to spend.
     */
    public record Machine(int tierRank, Tank tank, Predicate<Operation> fits, boolean powered) {

        public Machine {
            Objects.requireNonNull(tank);
            Objects.requireNonNull(fits);
        }

        /** A powered Harvester of this rank with an empty tank and room for anything. */
        public static Machine ready(int tierRank) {
            return new Machine(tierRank, Tank.EMPTY, operation -> true, true);
        }

        public Machine withTank(Tank tank) {
            return new Machine(tierRank, tank, fits, powered);
        }

        public Machine withFits(Predicate<Operation> fits) {
            return new Machine(tierRank, tank, fits, powered);
        }

        public Machine withPower(boolean powered) {
            return new Machine(tierRank, tank, fits, powered);
        }

        /**
         * The operation on {@code source}, or why the Harvester cannot run it now: a result with nowhere to go comes
         * before power. A well's crude goes to the product tank, every other result to the output buffer (#13).
         */
        Choice run(Operation operation, Source source) {
            if (!fits.test(operation)) {
                return new Idle(source.kind() == Kind.WELL ? IdleReason.PRODUCT_TANK_FULL : IdleReason.OUTPUT_FULL);
            }
            return powered ? operation : new Idle(IdleReason.NO_POWER);
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

    /** One position of the layer under the footprint: what stands there, its block id and the block tags it is in. */
    public record Source(Kind kind, String block, Set<String> tags) {

        public static final Source NONE = new Source(Kind.NONE, "", Set.of());

        public Source {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(block);
            tags = Set.copyOf(tags);
        }

        public static Source oreBlock(String block) {
            return oreBlock(block, Set.of());
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
     * Default recipes, and the Drilling Fluid it consumes, if any.
     */
    public record Recipe(String id, Set<String> blocks, Set<String> tags, int duration, OptionalInt minimumRank,
            boolean replacesDefault, Optional<DrillingFluid> drillingFluid) {

        public Recipe {
            Objects.requireNonNull(id);
            blocks = Set.copyOf(blocks);
            tags = Set.copyOf(tags);
            Objects.requireNonNull(drillingFluid);
        }

        /** A recipe that takes no Drilling Fluid. */
        public Recipe(String id, Set<String> blocks, Set<String> tags, int duration, OptionalInt minimumRank, boolean replacesDefault) {
            this(id, blocks, tags, duration, minimumRank, replacesDefault, Optional.empty());
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
        /** With no Held recipe, no source in the layer has a Default recipe to run. */
        NOTHING_TO_WORK,
        /** The Held recipe matches no source in the layer (#10); it never falls back to the Default recipes. */
        NOTHING_MATCHING,
        /** The Held recipe names a minimum tier above the Harvester's rank. */
        TIER_TOO_LOW,
        /** The Held recipe's Drilling Fluid is not in the tank, or not enough of it for one operation. */
        NO_FLUID,
        /** No tick of power to spend: no burn left and no fuel to light. */
        NO_POWER,
        /** The output buffer has no room for the operation's result. */
        OUTPUT_FULL,
        /** The product tank has no room for another cycle of the well's crude (#13). */
        PRODUCT_TANK_FULL
    }

    /** What {@link #choose} returns: an operation, or why there is none. */
    public sealed interface Choice permits Operation, Idle {
    }

    /**
     * Work the source at {@code position} for {@code ticks} at speed ×1, then save {@code nextCursor}
     * so the round goes on from the position after it. {@code recipe} is the Harvest Recipe it runs, empty
     * for a Default recipe.
     */
    public record Operation(int position, int ticks, int nextCursor, Optional<Recipe> recipe) implements Choice {

        /** The Drilling Fluid the operation consumes when it completes: only a Held recipe's, never a Default's (#11). */
        public Optional<DrillingFluid> drillingFluid() {
            return recipe.flatMap(Recipe::drillingFluid);
        }
    }

    public record Idle(IdleReason reason) implements Choice {
    }
}
