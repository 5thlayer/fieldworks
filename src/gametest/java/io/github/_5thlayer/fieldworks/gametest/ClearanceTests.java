// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.oil.OilCorpus;
import io.github._5thlayer.fieldworks.oil.OilField;
import io.github._5thlayer.fieldworks.ore.OreCorpus;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.PatchLaw;
import io.github._5thlayer.fieldworks.ore.PatchShape;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The clearance rule (ADR 0009) on a real server: two starts of different kinds are found in
 * neighbouring cells of their structure sets, their gap worked out here from the laws, and each
 * generated where the placement puts it. The rarer stays; the commoner generates nothing, and
 * generates again once the rarer's switch is off, which shows the rarer was the cause.
 */
final class ClearanceTests {

    /** About 3,200 blocks out, where every resource lies past its minimum distance. */
    private static final ChunkPos AROUND = new ChunkPos(200, 200);
    private static final int CELLS = 150;

    private ClearanceTests() {
    }

    /** One kind of start: its structure, its set's placement, and how its start is sized and switched. */
    private record Kind(String name, Holder<Structure> structure, RandomSpreadStructurePlacement placement,
            Supplier<PatchLaw> law, ToDoubleFunction<WorldgenRandom> sizeFactor,
            ModConfigSpec.BooleanValue sw) {

        ChunkPos chunkOf(ServerLevel level, int cellX, int cellZ) {
            return placement.getPotentialStructureChunk(level.getSeed(), cellX * placement.spacing(), cellZ * placement.spacing());
        }

        int reach(ServerLevel level, ChunkPos chunk) {
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(level.getSeed(), chunk.x(), chunk.z());
            return PatchShape.reachOf(law.get(), sizeFactor.applyAsDouble(random), chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        }

        boolean valid(ServerLevel level, ChunkPos chunk) {
            return PatchDiscTests.generate(level, structure, chunk).isValid();
        }
    }

    private record Pair(Kind first, ChunkPos firstChunk, Kind second, ChunkPos secondChunk) {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("patches_clearance_rarer_patch_keeps_its_place_over_a_commoner_one", 400,
                helper -> rarerStays(helper, OreResource.SULFUR.key(), OreResource.IRON.key()));
        tests.test("patches_clearance_oil_field_keeps_its_place_over_a_patch", 400,
                helper -> rarerStays(helper, "oil_field", OreResource.IRON.key()));
        tests.test("patches_clearance_patches_of_one_resource_keep_only_their_spacing", 400,
                ClearanceTests::sameResourceUnaffected);
    }

    private static Kind kind(GameTestHelper helper, String name) {
        boolean oil = name.equals("oil_field");
        Holder<Structure> structure = oil ? OilFieldTests.resolve(helper)
                : PatchDiscTests.resolve(helper, OreResource.of(name));
        if (structure == null) {
            return null;
        }
        Identifier set = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, oil ? name : "patch_" + name);
        var placement = (RandomSpreadStructurePlacement) helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, set))
                .value().placement();
        if (oil) {
            return new Kind(name, structure, placement, () -> OilCorpus.get().law(), random -> OilField.SIZE_FACTOR,
                    FieldworksConfig.generateOilFieldsSwitch());
        }
        OreResource resource = OreResource.of(name);
        PatchLaw law = PatchLaw.of(resource);
        return new Kind(name, structure, placement, () -> law, random -> law.sizeFactor(random.nextDouble()),
                FieldworksConfig.generateSwitch(resource));
    }

    /** The first pair of starts, one of each kind in neighbouring cells, whose edges are closer than the clearance and that {@code fits}. */
    private static Pair closePair(ServerLevel level, Kind first, Kind second, Predicate<Pair> fits) {
        int clearance = OreCorpus.get().clearance();
        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                ChunkPos a = first.chunkOf(level, Math.floorDiv(AROUND.x(), first.placement().spacing()) + cx,
                        Math.floorDiv(AROUND.z(), first.placement().spacing()) + cz);
                int ra = first.reach(level, a);
                if (ra == 0) {
                    continue;
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        ChunkPos b = second.chunkOf(level, Math.floorDiv(a.x(), second.placement().spacing()) + dx,
                                Math.floorDiv(a.z(), second.placement().spacing()) + dz);
                        int rb = second.reach(level, b);
                        if (rb == 0 || a.equals(b)) {
                            continue;
                        }
                        double gap = Math.hypot(a.getMiddleBlockX() - b.getMiddleBlockX(),
                                a.getMiddleBlockZ() - b.getMiddleBlockZ()) - ra - rb;
                        Pair pair = new Pair(first, a, second, b);
                        if (gap < clearance && fits.test(pair)) {
                            return pair;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** The rarer start generates; the commoner one beside it does not, and does once the rarer's switch is off. */
    private static void rarerStays(GameTestHelper helper, String rarerName, String commonerName) {
        ServerLevel level = helper.getLevel();
        Kind rarer = kind(helper, rarerName);
        Kind commoner = kind(helper, commonerName);
        if (rarer == null || commoner == null) {
            return;
        }
        Pair pair = closePair(level, rarer, commoner, candidate -> {
            if (!candidate.first().valid(level, candidate.firstChunk())) {
                return false;
            }
            rarer.sw().set(false);
            try {
                return commoner.valid(level, candidate.secondChunk());
            } finally {
                rarer.sw().set(true);
            }
        });
        if (pair == null) {
            helper.fail("no " + rarerName + " and " + commonerName + " start within the clearance could both generate near " + AROUND);
            return;
        }
        if (commoner.valid(level, pair.secondChunk())) {
            helper.fail(commonerName + " at " + pair.secondChunk() + " generated within the clearance of "
                    + rarerName + " at " + pair.firstChunk());
            return;
        }
        helper.succeed();
    }

    /** Two coal starts closer than the clearance both generate: one resource keeps only its spacing. Coal's discs are the widest, so the pair is the likeliest to be found. */
    private static void sameResourceUnaffected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Kind coal = kind(helper, OreResource.COAL.key());
        if (coal == null) {
            return;
        }
        Pair pair = closePair(level, coal, coal,
                candidate -> coal.valid(level, candidate.firstChunk()) && coal.valid(level, candidate.secondChunk()));
        if (pair == null) {
            helper.fail("no two coal starts within the clearance both generated near " + AROUND);
            return;
        }
        helper.succeed();
    }
}
