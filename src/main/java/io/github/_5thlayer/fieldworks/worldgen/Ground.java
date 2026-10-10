// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where a patch lies in a column. Vanilla's heightmaps stop at leaves and logs, so a patch crossing
 * a wood would land on the canopy; this walks the column down through everything that grew there to
 * the first terrain block, so the patch lies under the trees.
 */
final class Ground {

    private Ground() {
    }

    /** The y of the topmost terrain block in a column: the one a patch replaces. */
    static int of(LevelReader level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinY() && !isTerrain(level.getBlockState(cursor.setY(y)))) {
            y--;
        }
        return y;
    }

    /** Water is not terrain either, so a patch that clips a pond lies on its floor rather than floating. */
    private static boolean isTerrain(BlockState state) {
        return state.blocksMotion()
                && !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.LEAVES);
    }
}
