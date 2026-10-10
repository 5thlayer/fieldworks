// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.util.List;

/** Which sprite stage a block shows, computed from its remaining amount. */
public final class OreStage {

    private OreStage() {
    }

    /**
     * Stage 0 is untouched. A block is at stage {@code i} once its remaining fraction has fallen
     * below the {@code i}-th ratio; an exhausted block shows the last stage.
     */
    public static int stage(int remaining, int initial, List<Double> ratios) {
        if (ratios.isEmpty()) {
            return 0;
        }
        if (initial <= 0 || remaining <= 0) {
            return ratios.size() - 1;
        }
        double fraction = (double) remaining / initial;
        int stage = 0;
        for (int index = 1; index < ratios.size(); index++) {
            if (fraction < ratios.get(index)) {
                stage = index;
            }
        }
        return stage;
    }

    public static int count(List<Double> ratios) {
        return Math.max(1, ratios.size());
    }
}
