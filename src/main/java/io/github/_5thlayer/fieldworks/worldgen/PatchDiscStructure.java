// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.ore.GroundSpan;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * One resource's patch: a disc at the radius its law gives for a size factor at its centre's
 * distance from origin, in the one dimension its resource lies in (ADR 0007). Nothing excludes the ground near origin: a disc there has no quantity, so
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
        // A rarer start of another kind within the clearance keeps its place, and this one generates nothing (ADR 0009).
        if (!StartClearance.stays(context, resource.key(), x, z, PatchShape.reachOf(law, sizeFactor, x, z))) {
            return Optional.empty();
        }
        boolean nether = resource.dimension() == PatchDimension.NETHER;
        int y;
        if (nether) {
            // The Nether has no surface to read: the centre stands on its open floor, never on a lava sea (ADR 0007).
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
        Map<Long, OptionalInt> floors = new HashMap<>();
        PatchShape shape = PatchShape.of(PatchLaw.of(resource), sizeFactor, x, z,
                (scaledX, scaledZ) -> noise.noise(scaledX, 0, scaledZ),
                nether ? netherLand(context, y, floors) : land(context, y));
        // Ground a Harvester can't work generates nothing, rather than a patch trimmed to fit (ADR 0011).
        if (shape.isEmpty()
                || !GroundSpan.workable(shape, nether ? netherGround(context, floors) : ground(context))) {
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

    /** An Overworld column's ground in the generator's base terrain. */
    static GroundSpan.Height ground(GenerationContext context) {
        return (x, z) -> Ground.overworldBase(context.chunkGenerator(), context.randomState(), context.heightAccessor(), x, z);
    }

    /** A Nether column's floor, read once for both its land and its ground; the land has already left out a column with none. */
    private static GroundSpan.Height netherGround(GenerationContext context, Map<Long, OptionalInt> floors) {
        return (x, z) -> floor(context, floors, x, z).orElseThrow();
    }

    private static OptionalInt floor(GenerationContext context, Map<Long, OptionalInt> floors, int x, int z) {
        return floors.computeIfAbsent(ChunkPos.pack(x, z), key -> netherFloor(context, x, z));
    }

    private static OptionalInt netherFloor(GenerationContext context, int x, int z) {
        return Ground.netherBase(context.chunkGenerator(), context.randomState(), context.heightAccessor(), x, z);
    }

    /** A Nether column's land: a valid biome and an open floor that is not under lava, so the disc leaves a lava lake out. */
    private static PatchShape.Land netherLand(GenerationContext context, int y, Map<Long, OptionalInt> floors) {
        PatchShape.Land biome = land(context, y);
        return (x, z) -> biome.at(x, z) && floor(context, floors, x, z).isPresent();
    }

    @Override
    public StructureType<?> type() {
        return FieldworksWorldgen.PATCH_DISC.get();
    }
}
