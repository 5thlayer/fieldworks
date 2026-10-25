// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.Arrays;
import java.util.function.Function;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.PatchDisc;
import io.github._5thlayer.fieldworks.worldgen.PatchDiscPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.slf4j.Logger;

/**
 * A patch, in either dimension, and an oil field generate only on ground a Harvester can work (ADR 0011): a step
 * of 3 across the disc keeps the start, a step of 4 leaves nothing, and a gentle ramp is kept whole. The flat test
 * world has no slope to find, so each start is generated against a generator whose base terrain is the ground
 * the test draws, the only terrain a start reads.
 */
final class GroundSpanTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The floor the drawn ground stands on, well clear of the Nether's roof scan. */
    private static final int FLOOR = 64;

    /** How a test draws a column's ground, given the start's centre x. */
    @FunctionalInterface
    private interface Drawn {
        int at(int centreX, int x, int z);
    }

    private static final Drawn FLAT = (centreX, x, z) -> FLOOR;

    private GroundSpanTests() {
    }

    private record Kind(String name, ChunkPos from, Function<GameTestHelper, Holder<Structure>> resolve,
            Function<GameTestHelper, ServerLevel> level) {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        Kind[] kinds = {
                new Kind("overworld_patch", new ChunkPos(100, 160),
                        helper -> PatchDiscTests.resolve(helper, OreResource.IRON), GameTestHelper::getLevel),
                new Kind("nether_patch", new ChunkPos(100, 160),
                        helper -> PatchDiscTests.resolve(helper, OreResource.QUARTZ),
                        helper -> PatchDiscTests.levelOf(helper, OreResource.QUARTZ)),
                new Kind("oil_field", new ChunkPos(144, 100), OilFieldTests::resolve, GameTestHelper::getLevel),
        };
        for (Kind kind : kinds) {
            tests.test("ground_span_" + kind.name() + "_generates_on_a_step_of_three", 40,
                    helper -> generates(helper, kind, step(3), true));
            tests.test("ground_span_" + kind.name() + "_generates_nothing_across_a_step_of_four", 40,
                    helper -> generates(helper, kind, step(4), false));
            tests.test("ground_span_" + kind.name() + "_generates_nothing_below_a_step_of_four", 40,
                    helper -> generates(helper, kind, step(-4), false));
        }
        tests.test("ground_span_nether_patch_keeps_every_column_on_a_long_ramp", 40,
                helper -> rampKeepsEveryColumn(helper, kinds[1]));
        tests.test("ground_span_nether_patch_saved_before_keeps_its_trim", 100,
                helper -> savedBeforeKeepsItsTrim(helper, new Kind("nether_patch", new ChunkPos(100, 220),
                        kinds[1].resolve(), kinds[1].level())));
    }

    /**
     * A Nether piece saved before ADR 0011 has no {@code ground_span}, and still leaves out a column more than 8 from
     * its centre's floor when its chunk places, as it did when it generated. With its saved centre 20 under the real
     * floor, every column is left out; with its own centre, it places columns, each within 8 of it; and the same
     * piece saved since places columns too.
     */
    private static void savedBeforeKeepsItsTrim(GameTestHelper helper, Kind kind) {
        Holder<Structure> structure = kind.resolve().apply(helper);
        if (structure == null) {
            return;
        }
        ServerLevel level = kind.level().apply(helper);
        StructureStart start = null;
        for (int i = 0; i < 80 && start == null; i++) {
            StructureStart found = PatchDiscTests.generate(level, structure, new ChunkPos(kind.from().x() + i, kind.from().z()));
            if (found.isValid()) {
                start = found;
            }
        }
        if (start == null || !(start.getPieces().getFirst() instanceof PatchDiscPiece piece)) {
            helper.fail(kind.name() + " generated no disc in the Nether in 80 chunks from " + kind.from());
            return;
        }
        BoundingBox box = start.getBoundingBox();
        PatchDiscTests.loadChunks(level, box);
        CompoundTag saved = piece.createTag(StructurePieceSerializationContext.fromLevel(level));
        saved.putInt("centre_y", saved.getIntOr("centre_y", 0) - 20);
        CompoundTag before = saved.copy();
        before.remove("ground_span");

        int trimmed = placed(level, new PatchDiscPiece(before), box);
        if (trimmed != 0) {
            helper.fail("a piece saved before placed " + trimmed + " columns more than 8 off its centre's floor");
            return;
        }
        CompoundTag own = before.copy();
        int centreY = own.getIntOr("centre_y", 0) + 20;
        own.putInt("centre_y", centreY);
        int near = placed(level, new PatchDiscPiece(own), box);
        if (near == 0) {
            helper.fail("a piece saved before placed none of its columns round its own centre at " + centreY);
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), level.getMinY(), box.minZ(), box.maxX(), level.getMaxY(), box.maxZ())) {
            if (level.getBlockState(pos).getBlock() instanceof OreBlock && Math.abs(pos.getY() - centreY) > 8) {
                helper.fail("a piece saved before placed ore at " + pos + ", more than 8 off its centre's floor at " + centreY);
                return;
            }
        }
        int kept = placed(level, new PatchDiscPiece(saved), box);
        if (kept == 0) {
            helper.fail("a piece saved since placed none of its " + piece.disc().blockCount() + " columns");
            return;
        }
        helper.succeed();
    }

    /** Places the piece chunk by chunk over the box, and counts the ore blocks in the box afterwards. */
    private static int placed(ServerLevel level, PatchDiscPiece piece, BoundingBox box) {
        for (int x = SectionPos.blockToSectionCoord(box.minX()); x <= SectionPos.blockToSectionCoord(box.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ()); z <= SectionPos.blockToSectionCoord(box.maxZ()); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                        new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                                chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ()),
                        chunk, chunk.getWorldPosition());
            }
        }
        int ores = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), level.getMinY(), box.minZ(), box.maxX(), level.getMaxY(), box.maxZ())) {
            if (level.getBlockState(pos).getBlock() instanceof OreBlock) {
                ores++;
            }
        }
        return ores;
    }

    /** Ground {@code rise} higher from the start's centre x eastward. */
    private static Drawn step(int rise) {
        return (centreX, x, z) -> x >= centreX ? FLOOR + rise : FLOOR;
    }

    /** The first chunk from {@code kind.from()} at which a start generates on flat ground. */
    private static ChunkPos onFlatGround(GameTestHelper helper, Kind kind, Holder<Structure> structure, ServerLevel level) {
        for (int i = 0; i < 80; i++) {
            ChunkPos chunk = new ChunkPos(kind.from().x() + i, kind.from().z());
            if (generate(level, structure, chunk, FLAT).isValid()) {
                return chunk;
            }
        }
        helper.fail(kind.name() + " generated nowhere on flat ground in 80 chunks from " + kind.from());
        return null;
    }

    private static void generates(GameTestHelper helper, Kind kind, Drawn ground, boolean expected) {
        Holder<Structure> structure = kind.resolve().apply(helper);
        if (structure == null) {
            return;
        }
        ServerLevel level = kind.level().apply(helper);
        ChunkPos chunk = onFlatGround(helper, kind, structure, level);
        if (chunk == null) {
            return;
        }
        if (generate(level, structure, chunk, ground).isValid() != expected) {
            helper.fail(kind.name() + " at " + chunk + (expected ? " generated nothing" : " generated") + " on that ground");
            return;
        }
        helper.succeed();
    }

    /**
     * One block of rise a block eastward, more than the 8 a Nether column could once lie from its centre's floor: every
     * 3×3 square spans 2, so the disc keeps every column it has on flat ground.
     */
    private static void rampKeepsEveryColumn(GameTestHelper helper, Kind kind) {
        Holder<Structure> structure = kind.resolve().apply(helper);
        if (structure == null) {
            return;
        }
        ServerLevel level = kind.level().apply(helper);
        ChunkPos chunk = onFlatGround(helper, kind, structure, level);
        if (chunk == null) {
            return;
        }
        StructureStart flat = generate(level, structure, chunk, FLAT);
        StructureStart ramp = generate(level, structure, chunk, (centreX, x, z) -> FLOOR + (x - centreX));
        if (!ramp.isValid()
                || !(flat.getPieces().getFirst() instanceof PatchDisc.Source flatDisc)
                || !(ramp.getPieces().getFirst() instanceof PatchDisc.Source rampDisc)) {
            helper.fail(kind.name() + " at " + chunk + " generated nothing on a ramp");
            return;
        }
        int half = (flat.getBoundingBox().getXSpan() - 1) / 2;
        if (half <= 8) {
            helper.fail(kind.name() + " at " + chunk + " is " + half + " blocks wide each side, too little to rise past 8 on the ramp");
            return;
        }
        if (rampDisc.disc().blockCount() != flatDisc.disc().blockCount()) {
            helper.fail(kind.name() + " at " + chunk + " has " + rampDisc.disc().blockCount() + " columns on the ramp and "
                    + flatDisc.disc().blockCount() + " on flat ground");
            return;
        }
        LOGGER.info("GROUND SPAN {} at {}, {} blocks wide each side, rising {} across the ramp, keeping all {} columns",
                kind.name(), chunk, half, 2 * half, rampDisc.disc().blockCount());
        helper.succeed();
    }

    /** A start at the chunk on the drawn ground, the flat Overworld's settings standing in for the rest in either dimension. */
    private static StructureStart generate(ServerLevel level, Holder<Structure> structure, ChunkPos chunk, Drawn ground) {
        ChunkGenerator generator = new DrawnGround(
                ((FlatLevelSource) level.getServer().overworld().getChunkSource().getGenerator()).settings(), chunk.getMiddleBlockX(), ground);
        // The flat test world has no land biome; each structure's tag is asserted where it is resolved.
        return structure.value().generate(structure, level.dimension(), level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunk, 0, level, biome -> true);
    }

    /** A flat generator whose base terrain is stone up to the drawn ground in each column and air above. */
    private static final class DrawnGround extends FlatLevelSource {

        private final int centreX;
        private final Drawn ground;

        DrawnGround(FlatLevelGeneratorSettings settings, int centreX, Drawn ground) {
            super(settings);
            this.centreX = centreX;
            this.ground = ground;
        }

        @Override
        public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState randomState) {
            return ground.at(centreX, x, z) + 1;
        }

        @Override
        public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState randomState) {
            BlockState[] states = new BlockState[height.getHeight()];
            Arrays.fill(states, Blocks.AIR.defaultBlockState());
            Arrays.fill(states, 0, ground.at(centreX, x, z) - height.getMinY() + 1, Blocks.STONE.defaultBlockState());
            return new NoiseColumn(height.getMinY(), states);
        }
    }
}
