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
import io.github._5thlayer.fieldworks.ore.PatchDimension;
import io.github._5thlayer.fieldworks.ore.PatchDisc;
import io.github._5thlayer.fieldworks.ore.PatchLaw;
import io.github._5thlayer.fieldworks.ore.PatchShape;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * One resource's patch: a disc at the radius its law gives for a size factor at its centre's
 * distance from origin, in the one dimension its resource lies in (ADR-0132). Nothing excludes the ground near origin: a disc there has no quantity, so
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
        PatchLaw law = PatchLaw.of(resource);
        // Before anything that reads the terrain: a centre inside its resource's minimum distance has no patch.
        if (!law.generatesAt(law.distance(x, z))) {
            return Optional.empty();
        }
        double sizeFactor = law.sizeFactor(context.random().nextDouble());
        boolean nether = resource.dimension() == PatchDimension.NETHER;
        int y;
        if (nether) {
            // The Nether has no surface to read: the centre stands on its open floor, never on a lava sea (ADR-0132).
            OptionalInt floor = netherFloor(context, x, z);
            if (floor.isEmpty()) {
                return Optional.empty();
            }
            y = floor.getAsInt();
        } else {
            y = context.chunkGenerator().getFirstOccupiedHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        }
        ImprovedNoise noise = new ImprovedNoise(new XoroshiroRandomSource(context.seed()).forkPositional()
                .fromHashOf(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch/" + resource.key())));
        PatchShape shape = PatchShape.of(PatchLaw.of(resource), sizeFactor, x, z,
                (scaledX, scaledZ) -> noise.noise(scaledX, 0, scaledZ),
                nether ? netherLand(context, y) : land(context, y));
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        PatchDisc disc = new PatchDisc(resource, sizeFactor, shape.blockCount(), x, z);
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder -> builder.addPiece(
                new PatchDiscPiece(disc, shape, y, context.heightAccessor()))));
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

    /** The open floor under a column, from the generator's base terrain, which is all that exists when a start is found. */
    private static OptionalInt netherFloor(GenerationContext context, int x, int z) {
        LevelHeightAccessor height = context.heightAccessor();
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, height, context.randomState());
        var generator = context.chunkGenerator();
        // The terrain stops at the generator's depth, 128 in the Nether, where the dimension's own height spans 256.
        return NetherFloor.of(column::getBlock, height.getMinY(),
                Math.min(height.getMaxY(), generator.getMinY() + generator.getGenDepth() - 1));
    }

    /**
     * A Nether column's land: a valid biome and an open floor that is not under lava and lies within
     * {@link NetherFloor#MAX_STEP} of the centre's, so the disc follows the uneven floor and leaves a lava lake out.
     */
    private static PatchShape.Land netherLand(GenerationContext context, int y) {
        PatchShape.Land biome = land(context, y);
        Map<Long, Boolean> floors = new HashMap<>();
        return (x, z) -> biome.at(x, z)
                && floors.computeIfAbsent(ChunkPos.pack(x, z), key -> {
                    OptionalInt floor = netherFloor(context, x, z);
                    return floor.isPresent() && Math.abs(floor.getAsInt() - y) <= NetherFloor.MAX_STEP;
                });
    }

    @Override
    public StructureType<?> type() {
        return FieldworksWorldgen.PATCH_DISC.get();
    }
}
