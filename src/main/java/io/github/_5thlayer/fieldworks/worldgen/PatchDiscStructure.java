// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.PatchDisc;
import io.github._5thlayer.fieldworks.ore.PatchLaw;
import io.github._5thlayer.fieldworks.ore.PatchShape;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * One resource's patch: a disc at the radius its law gives for a size factor at its centre's
 * distance from origin. Nothing excludes the ground near origin: a disc there has no quantity, so
 * its radius rounds below one block and nothing generates. The resource's generation switch
 * (ADR-0002) is read here, at generation.
 */
public final class PatchDiscStructure extends Structure {

    private static final Codec<OreResource> RESOURCE = Codec.STRING.comapFlatMap(
            key -> {
                try {
                    return DataResult.success(OreResource.of(key));
                } catch (IllegalArgumentException unknown) {
                    return DataResult.error(unknown::getMessage);
                }
            },
            OreResource::key);

    public static final MapCodec<PatchDiscStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            RESOURCE.fieldOf("resource").forGetter(structure -> structure.resource))
            .apply(instance, PatchDiscStructure::new));

    private final OreResource resource;

    public PatchDiscStructure(StructureSettings settings, OreResource resource) {
        super(settings);
        this.resource = resource;
    }

    public OreResource resource() {
        return resource;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!FieldworksConfig.generates(resource)) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        double sizeFactor = PatchLaw.of(resource).sizeFactor(context.random().nextDouble());
        int y = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        ImprovedNoise noise = new ImprovedNoise(new XoroshiroRandomSource(context.seed()).forkPositional()
                .fromHashOf(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch/" + resource.key())));
        PatchShape shape = PatchShape.of(PatchLaw.of(resource), sizeFactor, x, z,
                (scaledX, scaledZ) -> noise.noise(scaledX, 0, scaledZ), land(context, y));
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        PatchDisc disc = new PatchDisc(resource, sizeFactor, shape.blockCount(), x, z);
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder -> builder.addPiece(
                new PatchDiscPiece(disc, shape, context.heightAccessor()))));
    }

    /**
     * Each column's biome, read at the centre's height, against the structure's own predicate:
     * vanilla asks only at the stub, so a coastal disc would otherwise run onto the seabed.
     */
    static PatchShape.Land land(GenerationContext context, int y) {
        Map<Long, Boolean> quarts = new HashMap<>();
        int quartY = QuartPos.fromBlock(y);
        return (x, z) -> quarts.computeIfAbsent(ChunkPos.pack(QuartPos.fromBlock(x), QuartPos.fromBlock(z)),
                key -> context.validBiome().test(context.biomeSource().getNoiseBiome(
                        QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z), context.randomState().sampler())));
    }

    @Override
    public StructureType<?> type() {
        return FieldworksWorldgen.PATCH_DISC.get();
    }
}
