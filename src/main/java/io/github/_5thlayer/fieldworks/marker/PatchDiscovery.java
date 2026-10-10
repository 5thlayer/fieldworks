// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.fieldworks.oil.OilField;
import io.github._5thlayer.fieldworks.oil.OilFieldSource;
import io.github._5thlayer.fieldworks.ore.OutfieldDisc;
import io.github._5thlayer.fieldworks.worldgen.OilFieldPiece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/** Reads a patch's marker off the structure start its centre chunk keeps. */
public final class PatchDiscovery {

    private PatchDiscovery() {
    }

    /**
     * The markers of the discs and oil fields whose centre lies in {@code chunk}. A patch belongs to
     * the chunk holding its centre, so it is marked once however many chunks it reaches into. An oil
     * field's total is its wells less those an ore column turned away.
     */
    public static List<PatchMarker> centredIn(ServerLevel level, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        List<PatchMarker> found = new ArrayList<>();
        for (var start : chunk.getAllStarts().values()) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof OilFieldSource field && centred(pos, field.centreX(), field.centreZ())) {
                    long total = OilField.total(OilField.placeable(field.wells(),
                            (x, z) -> OilFieldPiece.oreAt(level.structureManager(), x, z)));
                    found.add(new PatchMarker(PatchMarker.CRUDE_OIL, field.centreX(), surface(level, field.centreX(), field.centreZ()),
                            field.centreZ(), total));
                }
                if (piece instanceof OutfieldDisc.Source source) {
                    OutfieldDisc disc = source.disc();
                    if (centred(pos, disc.centreX(), disc.centreZ())) {
                        found.add(new PatchMarker(disc.resource().key(), disc.centreX(),
                                surface(level, disc.centreX(), disc.centreZ()), disc.centreZ(), disc.total()));
                    }
                }
            }
        }
        return found;
    }

    private static boolean centred(ChunkPos chunk, int x, int z) {
        return chunk.x() == (x >> 4) && chunk.z() == (z >> 4);
    }

    private static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }
}
