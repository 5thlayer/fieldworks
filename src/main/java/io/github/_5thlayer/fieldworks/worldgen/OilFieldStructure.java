// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.oil.OilCorpus;
import io.github._5thlayer.fieldworks.oil.OilField;
import io.github._5thlayer.fieldworks.ore.PatchShape;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/** A crude-oil field: crude's disc shape, with a well on the columns its draw passes. */
public final class OilFieldStructure extends Structure {

    public static final MapCodec<OilFieldStructure> CODEC = simpleCodec(OilFieldStructure::new);

    public OilFieldStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!FieldworksConfig.generatesOilFields()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        // A rarer start of another kind within the clearance keeps its place, and this one generates nothing (ADR 0009).
        if (!StartClearance.stays(context, StartClearance.OIL, x, z,
                PatchShape.reachOf(OilCorpus.get().law(), OilField.SIZE_FACTOR, x, z))) {
            return Optional.empty();
        }
        int y = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        ImprovedNoise noise = new ImprovedNoise(new XoroshiroRandomSource(context.seed()).forkPositional()
                .fromHashOf(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch/crude_oil")));
        OilField field = OilField.of(x, z, (scaledX, scaledZ) -> noise.noise(scaledX, 0, scaledZ),
                PatchDiscStructure.land(context, y));
        if (field.shape().isEmpty()) {
            return Optional.empty();
        }
        List<OilField.Well> wells = field.draw(context.random()::nextDouble);
        if (wells.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder -> builder.addPiece(
                new OilFieldPiece(x, z, field.shape().reach(), wells, context.heightAccessor()))));
    }

    @Override
    public StructureType<?> type() {
        return FieldworksWorldgen.OIL_FIELD.get();
    }
}
