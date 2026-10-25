// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.PatchDimension;
import io.github._5thlayer.fieldworks.ore.PatchDisc;
import io.github._5thlayer.fieldworks.ore.PatchShape;
import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A disc as a structure piece: what {@code OreMining} reads a block's amount from. The box is the
 * world's full height, because each column lands wherever the ground is and {@code OreMining} finds
 * the disc by testing the box against the broken block. In the Nether each column lands on its open floor.
 * The box reaches a chunk past the disc's columns, so the piece is placed in every chunk whose features
 * can reach the disc, and clears what they dropped on it.
 */
public final class PatchDiscPiece extends StructurePiece implements PatchDisc.Source {

    /** How far a chunk's features reach past it: one chunk (vanilla's {@code blockStateWriteRadius} for features). */
    private static final int FEATURE_REACH = 16;

    private final PatchDisc disc;
    private final PatchShape shape;
    /** The height of the centre's floor, which a Nether patch's columns keep within reach of; unknown for a disc saved before. */
    private final int centreY;

    PatchDiscPiece(PatchDisc disc, PatchShape shape, int centreY, LevelHeightAccessor height) {
        super(FieldworksWorldgen.PATCH_DISC_PIECE.get(), 0, new BoundingBox(
                disc.centreX() - shape.reach() - FEATURE_REACH, height.getMinY(), disc.centreZ() - shape.reach() - FEATURE_REACH,
                disc.centreX() + shape.reach() + FEATURE_REACH, height.getMaxY(), disc.centreZ() + shape.reach() + FEATURE_REACH));
        this.disc = disc;
        this.shape = shape;
        this.centreY = centreY;
    }

    public PatchDiscPiece(CompoundTag tag) {
        super(FieldworksWorldgen.PATCH_DISC_PIECE.get(), tag);
        this.disc = new PatchDisc(
                OreResource.of(tag.getStringOr("resource", "")),
                tag.getDoubleOr("size_factor", 0),
                tag.getIntOr("block_count", 0),
                tag.getIntOr("centre_x", 0),
                tag.getIntOr("centre_z", 0));
        this.shape = PatchShape.of(disc.centreX(), disc.centreZ(), tag.getIntOr("reach", 0),
                tag.getLongArray("columns").orElse(new long[0]));
        this.centreY = tag.getIntOr("centre_y", Integer.MIN_VALUE);
    }

    @Override
    public PatchDisc disc() {
        return disc;
    }

    @Override
    public boolean covers(int x, int z) {
        return shape.contains(x, z);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("resource", disc.resource().key());
        tag.putDouble("size_factor", disc.sizeFactor());
        tag.putInt("block_count", disc.blockCount());
        tag.putInt("centre_x", disc.centreX());
        tag.putInt("centre_z", disc.centreZ());
        tag.putInt("reach", shape.reach());
        tag.putLongArray("columns", shape.mask());
        tag.putInt("centre_y", centreY);
    }

    /** A Nether column's ore, on its open floor: none on a lava sea or off the centre's reach, and what grows or burns over it goes. */
    private void placeOnFloor(WorldGenLevel level, BlockPos.MutableBlockPos cursor, int x, int z, BlockState ore) {
        OptionalInt floor = Ground.nether(level, x, z);
        if (floor.isEmpty() || centreY != Integer.MIN_VALUE && Math.abs(floor.getAsInt() - centreY) > NetherFloor.MAX_STEP) {
            return;
        }
        Ground.place(level, cursor, x, floor.getAsInt(), z, ore);
    }

    /**
     * Places the disc's columns in this chunk, and then clears what this chunk's features dropped on the
     * columns already placed in the chunks round it. A chunk is decorated after its neighbours or before
     * them, and its trees and leaf litter reach one chunk out, so growth falls on a neighbour's patch that
     * was cleared already; this chunk's placement comes after all of its own features, and so last.
     */
    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        BlockState ore = FieldworksRegistries.ore(disc.resource()).get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = Math.max(disc.centreX() - shape.reach(), chunkBB.minX() - FEATURE_REACH);
        int maxX = Math.min(disc.centreX() + shape.reach(), chunkBB.maxX() + FEATURE_REACH);
        int minZ = Math.max(disc.centreZ() - shape.reach(), chunkBB.minZ() - FEATURE_REACH);
        int maxZ = Math.min(disc.centreZ() + shape.reach(), chunkBB.maxZ() + FEATURE_REACH);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!shape.contains(x, z)) {
                    continue;
                }
                boolean nether = disc.resource().dimension() == PatchDimension.NETHER;
                if (!chunkBB.isInside(x, chunkBB.minY(), z)) {
                    clearPlaced(level, cursor, x, z, ore, nether);
                } else if (nether) {
                    placeOnFloor(level, cursor, x, z, ore);
                } else {
                    Ground.place(level, cursor, x, Ground.of(level, x, z), z, ore);
                }
            }
        }
    }

    /** Clears over a column of a neighbouring chunk, if its ore is placed there already. */
    private static void clearPlaced(WorldGenLevel level, BlockPos.MutableBlockPos cursor, int x, int z, BlockState ore, boolean nether) {
        OptionalInt floor = nether ? Ground.nether(level, x, z) : OptionalInt.of(Ground.of(level, x, z));
        if (floor.isPresent() && level.getBlockState(cursor.set(x, floor.getAsInt(), z)).is(ore.getBlock())) {
            Ground.clearAbove(level, cursor, x, floor.getAsInt(), z);
        }
    }
}
