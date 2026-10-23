// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.function.IntPredicate;

/**
 * How a Harvester's column looks down (ADR 0008), with no Minecraft in it: from the block directly under the
 * Harvester through what it passes, down to {@code reach} blocks past that one. A block's depth is where it stands,
 * counted down from 1 for the block directly under the Harvester; reach is the tier's figure that bounds it.
 */
final class Reach {

    /** A column with no source: the depth that says so. */
    static final int NONE = 0;

    private Reach() {
    }

    /**
     * The depth of the first block {@code passes} refuses, counting the block directly under the Harvester as 1, or
     * {@link #NONE} when every block down to {@code reach} past it is passed. Reach is counted from the Harvester, so
     * a column opened from below by a broken block looks further but never past it.
     */
    static int firstBlock(int reach, IntPredicate passes) {
        for (int depth = 1; depth <= reach + 1; depth++) {
            if (!passes.test(depth)) {
                return depth;
            }
        }
        return NONE;
    }
}
