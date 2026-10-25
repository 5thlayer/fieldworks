// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import io.github._5thlayer.fieldworks.FieldworksTags;
import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Where a patch lies in a column. Vanilla's heightmaps stop at whatever grows on the ground, so a patch
 * crossing a wood would land on the canopy; this walks the column down through everything that grew
 * there, the blocks of {@link FieldworksTags#PATCH_CLEARS}, to the first terrain block, so the patch
 * lies under what grows on it, and then clears it away.
 */
final class Ground {

    /**
     * How a patch sets its blocks: with no shape updates to their neighbours, which worldgen never runs, so a
     * patch placed by {@code /place} into a live level leaves the same blocks that worldgen would.
     */
    private static final int PLACE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

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

    /**
     * The y of the open floor in a Nether column, or empty where it lies under lava (ADR 0007). Plants and
     * fire standing on it, and huge fungi grown on it, do not make it less open.
     */
    static OptionalInt nether(LevelReader level, int x, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, 0, z);
        return NetherFloor.of(y -> level.getBlockState(cursor.setY(y)), level.getMinY(), netherTop(level));
    }

    /** The top of a Nether column's blocks: its logical height, 128; the 256 the dimension spans are empty air over the roof. */
    private static int netherTop(LevelReader level) {
        return Math.min(level.getMaxY(), level.getMinY() + level.dimensionType().logicalHeight() - 1);
    }

    /**
     * The y of an Overworld column's ground in the generator's base terrain, which is all that exists when a start is
     * found: its topmost solid block, a pond's or lake's floor and not its water, as {@link #of} lays the ore.
     */
    static int overworldBase(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height, int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState) - 1;
    }

    /**
     * The y of the open floor in a Nether column of the generator's base terrain, or empty where it lies under lava:
     * the floor a start judges its ground by, before carvers and features, which is all that exists when a start is found.
     */
    static OptionalInt netherBase(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height, int x, int z) {
        NoiseColumn column = generator.getBaseColumn(x, z, height, randomState);
        // The terrain stops at the generator's depth, 128 in the Nether, where the dimension's own height spans 256.
        return NetherFloor.of(column::getBlock, height.getMinY(),
                Math.min(height.getMaxY(), generator.getMinY() + generator.getGenDepth() - 1));
    }

    /**
     * Whether a Nether column still has an open floor at {@code y} once carvers and features have run: a solid block
     * with no terrain above it, and no lava. What grows or burns on it does not make it less open.
     */
    static boolean netherOpenAt(LevelReader level, int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        if (!isTerrain(level.getBlockState(cursor))) {
            return false;
        }
        BlockState above = level.getBlockState(cursor.setY(y + 1));
        return !isTerrain(above) && !above.getFluidState().is(FluidTags.LAVA);
    }

    /** The y of the topmost block of {@code ore} in a Nether column under its roof, or empty where it holds none. */
    static OptionalInt netherOre(LevelReader level, int x, int z, BlockState ore) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = netherTop(level) - NetherFloor.ROOF_CLEARANCE; y > level.getMinY(); y--) {
            if (level.getBlockState(cursor.setY(y)).is(ore.getBlock())) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    /** Sets a patch's ore block at {@code y} and clears what grows over it. */
    static void place(WorldGenLevel level, BlockPos.MutableBlockPos cursor, int x, int y, int z, BlockState ore) {
        level.setBlock(cursor.set(x, y, z), ore, PLACE);
        clearAbove(level, cursor, x, y, z);
    }

    /**
     * Clears what grows over a patch's block at {@code y}, up the column to the first terrain block, a cave's
     * roof or an overhang, or to the column's top: a tree's trunk and the canopy over it, a bamboo stalk, the
     * grass, and the snow that lay on any of it. Water stays: a waterlogged plant or seagrass leaves its water,
     * so a patch under a pond keeps it, and snow lying on the ore itself stays.
     */
    static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos cursor, int x, int y, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int above = y + 1; above < top; above++) {
            BlockState state = level.getBlockState(cursor.set(x, above, z));
            if (isTerrain(state)) {
                return;
            }
            if (state.is(FieldworksTags.PATCH_CLEARS) || (above > y + 1 && state.is(Blocks.SNOW))) {
                BlockState left = state.getFluidState().createLegacyBlock();
                if (left != state) {
                    level.setBlock(cursor, left, PLACE);
                }
            }
        }
    }

    /**
     * A solid block that did not grow there. Water is not terrain either, so a patch that clips a pond lies
     * on its floor rather than floating.
     */
    static boolean isTerrain(BlockState state) {
        return state.blocksMotion() && !state.is(FieldworksTags.PATCH_CLEARS);
    }
}
