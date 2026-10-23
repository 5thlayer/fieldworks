// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.util.List;

/**
 * The clearance rule (ADR 0009): a start generates nothing when a start of another kind in the same
 * dimension would leave less than {@code clearance} between the two discs' edges. The rarer kind keeps
 * its place: the larger spacing, and between equals the lower {@link Kind#order}. A neighbour counts
 * only if it would itself generate and is not pushed out by a rarer one; only rarer kinds are walked,
 * so the walk ends. Free of Minecraft: the starts come from a {@link Starts}.
 */
public final class Clearance {

    /** An upper bound on any start's {@link PatchShape#reachOf reach}, which sizes the search for neighbours. */
    public static final int MAX_REACH = 48;

    /** A kind of start: a resource's patches, or the oil fields. */
    public record Kind(String name, PatchDimension dimension, int spacing, int order) {

        /** Whether this kind keeps its place against {@code other} where they are too close. */
        boolean rarerThan(Kind other) {
            return spacing != other.spacing ? spacing > other.spacing : order < other.order;
        }
    }

    /**
     * One potential start: its centre, the farthest its edge can reach, and whether it would generate
     * on its own account (switch on, past its minimum distance, a disc with something in it).
     */
    public record Start(Kind kind, int x, int z, int reach, boolean counts) {
    }

    /** The potential starts of one kind that might lie within {@code range} blocks of a point; extras are fine. */
    @FunctionalInterface
    public interface Starts {
        List<Start> near(Kind kind, int x, int z, int range);
    }

    private final List<Kind> kinds;
    private final int clearance;
    private final int maxReach;
    private final Starts starts;

    /** @param maxReach an upper bound on any start's reach, which sizes the search for neighbours */
    public Clearance(List<Kind> kinds, int clearance, int maxReach, Starts starts) {
        this.kinds = List.copyOf(kinds);
        this.clearance = clearance;
        this.maxReach = maxReach;
        this.starts = starts;
    }

    /** Whether the start keeps its place against every rarer neighbour that keeps its own. */
    public boolean stays(Start start) {
        int range = start.reach() + clearance + maxReach;
        for (Kind kind : kinds) {
            if (kind.dimension() != start.kind().dimension() || !kind.rarerThan(start.kind())) {
                continue;
            }
            for (Start neighbour : starts.near(kind, start.x(), start.z(), range)) {
                if (neighbour.counts() && tooClose(start, neighbour) && stays(neighbour)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean tooClose(Start a, Start b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z()) - a.reach() - b.reach() < clearance;
    }
}
