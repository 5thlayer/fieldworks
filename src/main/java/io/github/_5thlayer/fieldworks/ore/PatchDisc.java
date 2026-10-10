// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One generated patch, as its structure piece records it: the size factor it drew, the blocks it
 * placed and its centre column. Every block holds the amount at the centre's distance from origin.
 */
public record PatchDisc(OreResource resource, double sizeFactor, int blockCount, int centreX,
        int centreZ) {

    /** Implemented by the structure piece that places a disc. */
    public interface Source {
        PatchDisc disc();

        BoundingBox getBoundingBox();

        /** Whether the disc placed ore in this column; its box is a square around a ragged disc. */
        boolean covers(int x, int z);
    }

    public double distance() {
        return Math.sqrt((double) centreX * centreX + (double) centreZ * centreZ);
    }

    /** What the placed blocks hold between them, which the floored quotient leaves under the law's total. */
    public long total() {
        return (long) blockCount * amountPerBlock();
    }

    public int amountPerBlock() {
        long total = PatchLaw.of(resource).total(sizeFactor, distance());
        return new OreField(resource.key(), total, blockCount).amountPerBlock();
    }
}
