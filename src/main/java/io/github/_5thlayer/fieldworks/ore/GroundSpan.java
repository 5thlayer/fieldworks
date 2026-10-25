// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

/**
 * Whether a disc lies on ground a Harvester can work (ADR 0011): in every 3×3 square of its columns, the
 * highest and lowest ground differ by at most {@link #MAX}. Two columns share a square exactly when neither
 * axis puts them more than two apart, so the rule is a bound on every such pair. The same figure holds for
 * patches in both dimensions and for oil fields.
 */
public final class GroundSpan {

    /** The most height a Harvester's footprint, anywhere on a disc, ever has to cover. */
    public static final int MAX = 3;

    /** The y of the ground a disc's column would lie on. */
    @FunctionalInterface
    public interface Height {
        int at(int x, int z);
    }

    /** The footprint's side less one: how far apart two columns of one square can lie on each axis. */
    private static final int ACROSS = 2;

    private GroundSpan() {
    }

    /** Whether no 3×3 square of the shape's columns spans more than {@link #MAX}, reading each column's ground once. */
    public static boolean workable(PatchShape shape, Height ground) {
        int half = shape.reach();
        int side = 2 * half + 1;
        int[] heights = new int[side * side];
        boolean[] covered = new boolean[side * side];
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = shape.centreX() + dx;
                int z = shape.centreZ() + dz;
                if (shape.contains(x, z)) {
                    int i = (dx + half) * side + dz + half;
                    covered[i] = true;
                    heights[i] = ground.at(x, z);
                }
            }
        }
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                if (!covered[i * side + j]) {
                    continue;
                }
                int height = heights[i * side + j];
                // Each pair once: the columns after this one, along the rows it shares a square with.
                for (int di = 0; di <= ACROSS && i + di < side; di++) {
                    for (int dj = -ACROSS; dj <= ACROSS; dj++) {
                        int oj = j + dj;
                        if ((di == 0 && dj <= 0) || oj < 0 || oj >= side) {
                            continue;
                        }
                        int other = (i + di) * side + oj;
                        if (covered[other] && Math.abs(heights[other] - height) > MAX) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
