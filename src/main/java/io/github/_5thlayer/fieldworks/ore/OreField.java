// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

/** What one block of a placed patch starts with: the patch total divided over its blocks. */
public record OreField(String resource, long total, int blocks) {

    /**
     * Rounds down, so a patch never holds more than its total, and is floored at one so a block
     * never starts empty. A patch with no blocks has no amount.
     */
    public int amountPerBlock() {
        if (blocks <= 0) {
            return 0;
        }
        return (int) Math.max(1, total / blocks);
    }
}
