// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.worldgen;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.oil.OilCorpus;
import io.github._5thlayer.fieldworks.oil.OilField;
import io.github._5thlayer.fieldworks.ore.Clearance;
import io.github._5thlayer.fieldworks.ore.OreCorpus;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.PatchDimension;
import io.github._5thlayer.fieldworks.ore.PatchLaw;
import io.github._5thlayer.fieldworks.ore.PatchShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Vanilla's side of the clearance rule (ADR 0009): the kinds from the structure sets in the registry,
 * and every neighbour's potential start recomputed from the seed as its own generation would, so the
 * answer does not depend on the order chunks generate in. A neighbour's start chunk is its set's
 * {@link RandomSpreadStructurePlacement#getPotentialStructureChunk}, and a patch's size factor the
 * first draw of the same large-feature random that {@link Structure.GenerationContext} hands its
 * {@code findGenerationPoint}.
 */
final class StartClearance {

    /** The kind of the oil fields, whose structure set is {@code fieldworks:oil_field}. */
    static final String OIL = "oil_field";

    private final Structure.GenerationContext context;
    private final Map<Clearance.Kind, RandomSpreadStructurePlacement> placements = new HashMap<>();
    private final Map<String, Clearance.Kind> kinds = new HashMap<>();
    private final Clearance clearance;

    private StartClearance(Structure.GenerationContext context) {
        this.context = context;
        List<Clearance.Kind> found = new ArrayList<>();
        add(found, OIL, PatchDimension.OVERWORLD, 0);
        int order = 1;
        for (String key : OreCorpus.get().resources().keySet()) {
            add(found, key, OreResource.of(key).dimension(), order++);
        }
        this.clearance = new Clearance(found, OreCorpus.get().clearance(), Clearance.MAX_REACH, this::near);
    }

    /** Whether a start of {@code kind} at the chunk's middle, reaching {@code reach}, keeps its place. */
    static boolean stays(Structure.GenerationContext context, String kind, int x, int z, int reach) {
        StartClearance rule = new StartClearance(context);
        Clearance.Kind self = rule.kinds.get(kind);
        return self == null || rule.clearance.stays(new Clearance.Start(self, x, z, reach, true));
    }

    private void add(List<Clearance.Kind> found, String name, PatchDimension dimension, int order) {
        String set = name.equals(OIL) ? OIL : "patch_" + name;
        context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, set)))
                .map(holder -> holder.value().placement())
                .filter(RandomSpreadStructurePlacement.class::isInstance)
                .map(RandomSpreadStructurePlacement.class::cast)
                .ifPresent(placement -> {
                    Clearance.Kind kind = new Clearance.Kind(name, dimension, placement.spacing(), order);
                    found.add(kind);
                    kinds.put(name, kind);
                    placements.put(kind, placement);
                });
    }

    private List<Clearance.Start> near(Clearance.Kind kind, int x, int z, int range) {
        RandomSpreadStructurePlacement placement = placements.get(kind);
        int spacing = placement.spacing();
        List<Clearance.Start> starts = new ArrayList<>();
        for (int cellX = Math.floorDiv((x - range) >> 4, spacing); cellX <= Math.floorDiv((x + range) >> 4, spacing); cellX++) {
            for (int cellZ = Math.floorDiv((z - range) >> 4, spacing); cellZ <= Math.floorDiv((z + range) >> 4, spacing); cellZ++) {
                ChunkPos chunk = placement.getPotentialStructureChunk(context.seed(), cellX * spacing, cellZ * spacing);
                starts.add(start(kind, chunk));
            }
        }
        return starts;
    }

    /** The start a chunk would be given: its size factor, reach, and whether it would generate at all. */
    private Clearance.Start start(Clearance.Kind kind, ChunkPos chunk) {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        if (kind.name().equals(OIL)) {
            PatchLaw law = OilCorpus.get().law();
            int reach = PatchShape.reachOf(law, OilField.SIZE_FACTOR, x, z);
            return new Clearance.Start(kind, x, z, reach, FieldworksConfig.generatesOilFields() && reach > 0);
        }
        OreResource resource = OreResource.of(kind.name());
        PatchLaw law = PatchLaw.of(resource);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(context.seed(), chunk.x(), chunk.z());
        int reach = PatchShape.reachOf(law, law.sizeFactor(random.nextDouble()), x, z);
        return new Clearance.Start(kind, x, z, reach,
                FieldworksConfig.generates(resource) && law.generatesAt(law.distance(x, z)) && reach > 0);
    }
}
