// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

/** Which sprite stage a block shows, computed from its remaining amount. */
public final class OreStage {

    /** Sizes the blockstate property and the sprites, so it is code, not data (ADR 0014). */
    public static final int COUNT = 8;

    private OreStage() {
    }

    /**
     * Stage 0 is untouched. Stage {@code i} begins once the remaining fraction is at or below
     * {@code ratio^i}; an exhausted block shows the last stage.
     */
    public static int stage(int remaining, int initial, double ratio) {
        if (initial <= 0 || remaining <= 0) {
            return COUNT - 1;
        }
        double fraction = (double) remaining / initial;
        int stage = 0;
        double threshold = ratio;
        while (stage < COUNT - 1 && fraction <= threshold) {
            stage++;
            threshold *= ratio;
        }
        return stage;
    }
}
