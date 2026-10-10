// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.util.HashMap;
import java.util.Map;

/**
 * What has been drawn out of the ore blocks in one chunk. A block's initial amount derives from its
 * patch and only the difference is stored, so an untouched patch costs nothing. Positions are
 * packed longs, which keeps this free of Minecraft.
 */
public final class OreDelta {

    private final Map<Long, Integer> drawn;

    public OreDelta() {
        this(new HashMap<>());
    }

    private OreDelta(Map<Long, Integer> drawn) {
        this.drawn = drawn;
    }

    public static OreDelta of(Map<Long, Integer> drawn) {
        return new OreDelta(new HashMap<>(drawn));
    }

    public Map<Long, Integer> drawn() {
        return Map.copyOf(drawn);
    }

    public boolean isEmpty() {
        return drawn.isEmpty();
    }

    public int remaining(long pos, int initial) {
        return Math.max(0, initial - drawn.getOrDefault(pos, 0));
    }

    /**
     * Takes one unit. The last unit is paid and the block is then exhausted, so a patch pays out
     * exactly what it holds. An exhausted position retires its entry.
     */
    public Draw draw(long pos, int initial) {
        int remaining = remaining(pos, initial);
        if (remaining <= 0) {
            drawn.remove(pos);
            return new Draw(0, 0, true);
        }
        int left = remaining - 1;
        if (left == 0) {
            drawn.remove(pos);
            return new Draw(1, 0, true);
        }
        drawn.put(pos, initial - left);
        return new Draw(1, left, false);
    }

    /** Drops the entry of a block that went away without being mined out, so its successor starts full. */
    public void retire(long pos) {
        drawn.remove(pos);
    }

    /**
     * @param paid units handed to the caller: one, or none from an exhausted block
     * @param remaining units still in the block
     * @param exhausted whether the block should now break
     */
    public record Draw(int paid, int remaining, boolean exhausted) {
    }
}
