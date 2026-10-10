// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The structure types and pieces the datapack's patches and oil fields refer to by id. */
public final class FieldworksWorldgen {

    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Fieldworks.MOD_ID);

    static final DeferredHolder<StructureType<?>, StructureType<PatchDiscStructure>> PATCH_DISC =
            STRUCTURE_TYPES.register("patch_disc", () -> () -> PatchDiscStructure.CODEC);

    static final DeferredHolder<StructureType<?>, StructureType<OilFieldStructure>> OIL_FIELD =
            STRUCTURE_TYPES.register("oil_field", () -> () -> OilFieldStructure.CODEC);

    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Fieldworks.MOD_ID);

    static final DeferredHolder<StructurePieceType, StructurePieceType> PATCH_DISC_PIECE =
            PIECES.register("patch_disc", () -> (StructurePieceType.ContextlessType) PatchDiscPiece::new);

    static final DeferredHolder<StructurePieceType, StructurePieceType> OIL_FIELD_PIECE =
            PIECES.register("oil_field", () -> (StructurePieceType.ContextlessType) OilFieldPiece::new);

    private FieldworksWorldgen() {
    }

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECES.register(modBus);
    }
}
