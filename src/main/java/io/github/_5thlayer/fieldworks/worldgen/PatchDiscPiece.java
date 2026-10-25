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
 */
public final class PatchDiscPiece extends StructurePiece implements PatchDisc.Source {

    private final PatchDisc disc;
    private final PatchShape shape;
    /** The height of the centre's floor, which a Nether patch's columns keep within reach of; unknown for a disc saved before. */
    private final int centreY;

    PatchDiscPiece(PatchDisc disc, PatchShape shape, int centreY, LevelHeightAccessor height) {
        super(FieldworksWorldgen.PATCH_DISC_PIECE.get(), 0, new BoundingBox(
                disc.centreX() - shape.reach(), height.getMinY(), disc.centreZ() - shape.reach(),
                disc.centreX() + shape.reach(), height.getMaxY(), disc.centreZ() + shape.reach()));
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
        int y = floor.getAsInt();
        level.setBlock(cursor.set(x, y, z), ore, Ground.PLACE);
        Ground.clearAbove(level, cursor, x, y, z);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        BlockState ore = FieldworksRegistries.ore(disc.resource()).get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = Math.max(boundingBox.minX(), chunkBB.minX());
        int maxX = Math.min(boundingBox.maxX(), chunkBB.maxX());
        int minZ = Math.max(boundingBox.minZ(), chunkBB.minZ());
        int maxZ = Math.min(boundingBox.maxZ(), chunkBB.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!shape.contains(x, z)) {
                    continue;
                }
                if (disc.resource().dimension() == PatchDimension.NETHER) {
                    placeOnFloor(level, cursor, x, z, ore);
                } else {
                    int y = Ground.of(level, x, z);
                    level.setBlock(cursor.set(x, y, z), ore, Ground.PLACE);
                    Ground.clearAbove(level, cursor, x, y, z);
                }
            }
        }
    }
}
