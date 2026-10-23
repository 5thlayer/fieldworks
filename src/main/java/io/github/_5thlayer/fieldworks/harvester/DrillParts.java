// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;

/**
 * The parts a Harvester's drill heads and drill legs are drawn as (GLOSSARY.md, Drill head and Drill legs), with no
 * Minecraft in them: boxes inside the cell they stand in, each cell given as blocks from the Harvester's origin block,
 * which is its bottom centre.
 *
 * <p>A column is named by its depth, how far under the origin block its source lies: 0 for none, 1 for the block directly
 * under the Harvester, which draws its head in the bottom block's own cell, and more for a source lower down, whose head
 * stands in the cell above it with legs up to the bottom block. No part crosses a cell's edge, so each is lit by its own.
 */
public final class DrillParts {

    private static final double PX = 1 / 16d;
    /**
     * Columns under the footprint: {@code dx} then {@code dz} from -1 to 1, the order the Harvester's cursor counts in,
     * and the order {@code column_of} in {@code scripts/build-textures.py} numbers the bottom blocks' models in.
     */
    public static final int COLUMNS = 9;
    /** The depth of a source directly under the Harvester, whose column's bottom block is its drill head. */
    public static final int UNDER = 1;

    // The head: a housing with the bit under it, in the middle of its cell.
    private static final double BODY_LOW = 6 * PX;
    private static final double BODY_MIN = 3 * PX;
    private static final double BODY_MAX = 13 * PX;
    private static final double BIT_MIN = 5.5 * PX;
    private static final double BIT_MAX = 10.5 * PX;
    // The legs: a post up each corner of the head's housing, a hair clear of it.
    private static final double LEG_MIN = 3.5 * PX;
    private static final double LEG_WIDTH = 2 * PX;

    /** Which part: the housing, the bit under it, or a leg. */
    public enum Kind {
        BODY, BIT, LEG
    }

    /** A part: a box from {@code (x0, y0, z0)} to {@code (x1, y1, z1)} in blocks of the cell {@code (dx, dy, dz)}. */
    public record Box(Kind kind, int dx, int dy, int dz, double x0, double y0, double z0, double x1, double y1, double z1) {
    }

    private DrillParts() {
    }

    /** The column {@code index}'s offset from the origin block along x, -1 to 1. */
    public static int dx(int index) {
        return index / 3 - 1;
    }

    /** The column {@code index}'s offset from the origin block along z, -1 to 1. */
    public static int dz(int index) {
        return index % 3 - 1;
    }

    /** Whether a column of this depth has a drill head placed in the world, over a source lower than {@link #UNDER}. */
    public static boolean placesHead(int depth) {
        return depth > UNDER;
    }

    /** The cells, in blocks under the origin block, a column of this depth places a drill head in: 0 for the bottom block's own. */
    public static int headBelow(int depth) {
        return depth - 1;
    }

    /** Every drill head and leg for the nine {@code depths}, a column at a time. */
    public static List<Box> of(int[] depths) {
        if (depths.length != COLUMNS) {
            throw new IllegalArgumentException("a Harvester has " + COLUMNS + " columns, got " + depths.length);
        }
        List<Box> boxes = new ArrayList<>();
        for (int index = 0; index < COLUMNS; index++) {
            column(boxes, dx(index), dz(index), depths[index]);
        }
        return boxes;
    }

    private static void column(List<Box> boxes, int dx, int dz, int depth) {
        if (depth < UNDER) {
            return;
        }
        int head = -headBelow(depth);
        boxes.add(new Box(Kind.BODY, dx, head, dz, BODY_MIN, BODY_LOW, BODY_MIN, BODY_MAX, 1, BODY_MAX));
        boxes.add(new Box(Kind.BIT, dx, head, dz, BIT_MIN, 0, BIT_MIN, BIT_MAX, BODY_LOW, BIT_MAX));
        // From the cell under the bottom block down to the one over the head.
        for (int cell = -1; cell > head; cell--) {
            for (double x : new double[] {LEG_MIN, 1 - LEG_MIN - LEG_WIDTH}) {
                for (double z : new double[] {LEG_MIN, 1 - LEG_MIN - LEG_WIDTH}) {
                    boxes.add(new Box(Kind.LEG, dx, cell, dz, x, 0, z, x + LEG_WIDTH, 1, z + LEG_WIDTH));
                }
            }
        }
    }
}
