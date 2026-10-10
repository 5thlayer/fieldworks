// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.List;
import java.util.Objects;

/**
 * The choice of the Harvester's next operation: given the sources in the layer under its footprint, in
 * a fixed order, and the round-robin cursor the Harvester saves, the next operation or why there is
 * none. It reads no game state, so it is tested on a plain JVM; the block entity reads the world into
 * {@link Source}s and carries the operation out.
 */
public final class OperationChoice {

    /** An ore block's Default recipe: one draw, in this many ticks at speed ×1. */
    static final int ORE_DRAW_TICKS = 20;

    private OperationChoice() {
    }

    /**
     * The next operation from {@code cursor} round the layer: the first source at or after it that can
     * be worked, wrapping at the end. Positions are the layer's indices, so a source that goes, an ore
     * block turned to stone, leaves the others' places in the round as they were.
     */
    public static Choice choose(List<Source> layer, int cursor) {
        int size = layer.size();
        for (int step = 0; step < size; step++) {
            int position = Math.floorMod(cursor + step, size);
            if (layer.get(position).kind() == Kind.ORE_BLOCK) {
                return new Operation(position, ORE_DRAW_TICKS, (position + 1) % size);
            }
        }
        return new Idle(IdleReason.NOTHING_TO_WORK);
    }

    /** What stands at one position of the layer. */
    public enum Kind {
        /** Nothing the Harvester works. */
        NONE,
        ORE_BLOCK
    }

    /** One position of the layer under the footprint: what stands there, and its block id. */
    public record Source(Kind kind, String block) {

        public static final Source NONE = new Source(Kind.NONE, "");

        public Source {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(block);
        }

        public static Source oreBlock(String block) {
            return new Source(Kind.ORE_BLOCK, block);
        }
    }

    /** Why the Harvester has no operation to run. */
    public enum IdleReason {
        NOTHING_TO_WORK
    }

    /** What {@link #choose} returns: an operation, or why there is none. */
    public sealed interface Choice permits Operation, Idle {
    }

    /**
     * Work the source at {@code position} for {@code ticks} at speed ×1, then save {@code nextCursor}
     * so the round goes on from the position after it.
     */
    public record Operation(int position, int ticks, int nextCursor) implements Choice {
    }

    public record Idle(IdleReason reason) implements Choice {
    }
}
