// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import java.util.OptionalInt;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a Nether patch lies in a column: the open floor, found by scanning down from under the bedrock
 * roof to the first solid block with nothing solid above it. The heightmaps are no use there, since the
 * topmost solid block of a Nether column is the roof. A floor under lava is no floor: the column is
 * rejected, so a patch never lies on a lava sea (ADR 0007).
 */
final class NetherFloor {

    /** Where the scan starts, below the top of the Nether's blocks: inside the roof's rock or under it, and under its bedrock. */
    static final int ROOF_CLEARANCE = 6;

    /**
     * How far above or below its centre's floor a patch's column may lie. A Nether column can hold several floors, caves
     * over caves, and the topmost of each need not be within reach of its neighbours: a column further off than this is
     * left out, so a patch stays one place a factory can stand beside rather than a scatter over every cave level.
     */
    static final int MAX_STEP = 8;

    /** The blocks of one column, by y. */
    @FunctionalInterface
    interface Column {
        BlockState at(int y);
    }

    private NetherFloor() {
    }

    /** The y of the floor block in a column, scanning down from {@code maxY}, the top of the terrain, or empty where there is no open floor or it lies under lava. */
    static OptionalInt of(Column column, int minY, int maxY) {
        for (int y = maxY - ROOF_CLEARANCE; y > minY; y--) {
            BlockState below = column.at(y);
            if (!below.blocksMotion()) {
                continue;
            }
            BlockState above = column.at(y + 1);
            if (above.blocksMotion()) {
                continue;
            }
            return above.getFluidState().is(FluidTags.LAVA) ? OptionalInt.empty() : OptionalInt.of(y);
        }
        return OptionalInt.empty();
    }
}
