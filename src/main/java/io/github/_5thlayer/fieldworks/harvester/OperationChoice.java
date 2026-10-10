// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

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
        if (held.isPresent() && held.get().minimumRank().isPresent() && tierRank < held.get().minimumRank().getAsInt()) {
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
                    return new Operation(position, held.get().duration(), next, held);
                }
            } else if (recipes.stream().noneMatch(recipe -> recipe.replacesDefaultOf(source))) {
                return new Operation(position, defaultTicks(source.kind()), next, Optional.empty());
            }
        }
        return new Idle(IdleReason.NOTHING_TO_WORK);
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
     * speed ×1, the lowest tier rank that may run it if it names one, and whether it removes its sources'
     * Default recipes.
     */
    public record Recipe(String id, Set<String> blocks, Set<String> tags, int duration, OptionalInt minimumRank,
            boolean replacesDefault) {

        public Recipe {
            Objects.requireNonNull(id);
            blocks = Set.copyOf(blocks);
            tags = Set.copyOf(tags);
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
        NOTHING_TO_WORK,
        /** The Held recipe names a minimum tier above the Harvester's rank. */
        TIER_TOO_LOW
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
    }

    public record Idle(IdleReason reason) implements Choice {
    }
}
