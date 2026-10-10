// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** The pieces of some structure starts that implement one source interface. */
public final class StartPieces {

    private StartPieces() {
    }

    public static <T> List<T> sources(Iterable<StructureStart> starts, Class<T> source) {
        List<T> found = new ArrayList<>();
        for (StructureStart start : starts) {
            for (StructurePiece piece : start.getPieces()) {
                if (source.isInstance(piece)) {
                    found.add(source.cast(piece));
                }
            }
        }
        return found;
    }
}
