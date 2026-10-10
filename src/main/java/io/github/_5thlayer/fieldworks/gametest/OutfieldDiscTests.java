// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.marker.MarkerDelivery;
import io.github._5thlayer.fieldworks.marker.MarkerEvents;
import io.github._5thlayer.fieldworks.marker.PatchDiscovery;
import io.github._5thlayer.fieldworks.marker.PatchMarker;
import io.github._5thlayer.fieldworks.marker.PatchMarkerData;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreDelta;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.OutfieldDisc;
import io.github._5thlayer.fieldworks.ore.OutfieldLaw;
import io.github._5thlayer.fieldworks.worldgen.OutfieldDiscStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.slf4j.Logger;

/**
 * Each resource's patch, resolved out of the server's registry and placed: generated far enough out
 * that the flat test world never overlaps another test, with a tree standing on its centre, and
 * placed chunk by chunk the way {@code /place} does. The start is also recorded in its chunk, which
 * {@code /place} skips and worldgen does not, so a broken block reads its amount through the same
 * structure manager a real world would.
 */
final class OutfieldDiscTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final TagKey<Biome> LAND = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_land"));

    private static final int HOLE = 150;

    private OutfieldDiscTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        OreResource[] resources = OreResource.values();
        for (int i = 0; i < resources.length; i++) {
            OreResource resource = resources[i];
            ChunkPos far = new ChunkPos(100 + 8 * i, 100);
            tests.test("outfield_" + resource.key() + "_disc_places", 20, helper -> discPlaces(helper, resource, far));
            tests.test("outfield_" + resource.key() + "_nothing_in_the_hole", 20,
                    helper -> nothingInTheHole(helper, resource));
            tests.test("outfield_" + resource.key() + "_nothing_off_the_land", 20,
                    helper -> nothingOffTheLand(helper, resource, far));
            tests.test("outfield_" + resource.key() + "_switched_off_generates_nothing", 20,
                    helper -> switchedOff(helper, resource, far));
        }
        tests.test("outfield_marker_is_found_once_and_removed_when_the_last_block_goes", 20,
                helper -> markerFollowsThePatch(helper, OreResource.IRON, new ChunkPos(100, 140)));
    }

    /**
     * A patch's marker is found from its centre chunk, sent with what it holds, sent again when
     * the patch has less, and sent exactly once more with nothing when its last block goes. The
     * first block is mined out and the last is mined to the end, so a mined-out block counted twice
     * ends the patch a block early; the rest are broken outright, the route by which a patch loses
     * units nobody drew.
     */
    private static void markerFollowsThePatch(GameTestHelper helper, OreResource resource, ChunkPos chunk) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        StructureStart start = generate(level, structure, chunk);
        if (!start.isValid() || !(start.getPieces().getFirst() instanceof OutfieldDisc.Source source)) {
            helper.fail(resource.key() + " generated no disc at " + chunk);
            return;
        }
        OutfieldDisc disc = source.disc();
        BoundingBox box = start.getBoundingBox();
        loadChunks(level, box);
        place(level, structure.value(), start, chunk, box);
        List<BlockPos> ores = oresIn(level, box);
        if (ores.size() < 2) {
            helper.fail(resource.key() + "'s disc at " + chunk + " placed " + ores.size() + " blocks");
            return;
        }
        UUID player = UUID.randomUUID();
        PatchMarkerData data = PatchMarkerData.get(level.getServer());
        try {
            markerFollowsThePatch(helper, level, data, player, chunk, disc, ores);
        } finally {
            data.logout(player);
        }
    }

    private static void markerFollowsThePatch(GameTestHelper helper, ServerLevel level, PatchMarkerData data,
            UUID player, ChunkPos chunk, OutfieldDisc disc, List<BlockPos> ores) {
        String dimension = level.dimension().identifier().toString();
        MarkerDelivery.Amounts amounts = MarkerEvents.amounts(level.getServer());
        data.observe(player, dimension);
        List<PatchMarker> found = PatchDiscovery.centredIn(level, level.getChunk(chunk.x(), chunk.z()));
        data.found(dimension, found);
        data.found(dimension, found);
        List<MarkerDelivery.Update> sent = data.takeMarkers(player, amounts);
        if (found.size() != 1 || sent.size() != 1 || sent.getFirst().amount() != disc.total()) {
            helper.fail("finding the disc's chunk found " + found + " and sent " + sent
                    + ", for a disc holding " + disc.total());
            return;
        }
        PatchMarker marker = found.getFirst();

        BlockPos first = ores.removeFirst();
        BlockPos last = ores.removeLast();
        OreBlock ore = (OreBlock) level.getBlockState(last).getBlock();
        for (int unit = 0; unit < disc.amountPerBlock(); unit++) {
            OreMining.draw(level, ore, first);
        }
        for (BlockPos pos : ores) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        data.refresh(dimension);
        List<MarkerDelivery.Update> broken = data.takeMarkers(player, amounts);
        if (!broken.equals(List.of(new MarkerDelivery.Update(marker, disc.amountPerBlock())))) {
            helper.fail("with one block of " + disc.blockCount() + " left, a refresh sent " + broken);
            return;
        }

        for (int unit = 0; unit < disc.amountPerBlock(); unit++) {
            if (!data.takeMarkers(player, amounts).isEmpty()) {
                helper.fail("a removal was sent with " + (disc.amountPerBlock() - unit) + " units left");
                return;
            }
            OreMining.draw(level, ore, last);
        }
        data.refresh(dimension);
        List<MarkerDelivery.Update> removal = data.takeMarkers(player, amounts);
        data.refresh(dimension);
        List<MarkerDelivery.Update> after = data.takeMarkers(player, amounts);
        if (!level.getBlockState(last).is(Blocks.STONE)
                || !removal.equals(List.of(new MarkerDelivery.Update(marker, 0))) || !after.isEmpty()) {
            helper.fail("mining out the last block left " + level.getBlockState(last) + ", sent " + removal
                    + " and then " + after);
            return;
        }
        helper.succeed();
    }

    private static void discPlaces(GameTestHelper helper, OreResource resource, ChunkPos chunk) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        StructureStart start = generate(level, structure, chunk);
        if (!start.isValid() || !(start.getPieces().getFirst() instanceof OutfieldDisc.Source source)) {
            helper.fail(resource.key() + " generated no disc at " + chunk + ", " + distance(chunk) + " from origin");
            return;
        }
        OutfieldDisc disc = source.disc();
        BoundingBox box = start.getBoundingBox();
        loadChunks(level, box);

        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, box.minX(), box.minZ()) - 1;
        BlockPos trunk = new BlockPos(disc.centreX(), ground + 1, disc.centreZ());
        for (int y = 0; y < 3; y++) {
            level.setBlockAndUpdate(trunk.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
        level.setBlockAndUpdate(trunk.above(3), Blocks.OAK_LEAVES.defaultBlockState());

        place(level, structure.value(), start, chunk, box);

        BlockState ore = FieldworksRegistries.ore(resource).get().defaultBlockState();
        int placed = 0;
        double farthest = 0;
        BlockPos farthestOre = null;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int ores = 0;
                for (int y = ground - 4; y <= ground + 5; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof OreBlock)) {
                        continue;
                    }
                    ores++;
                    if (state != ore) {
                        helper.fail(resource.key() + "'s disc placed " + state + " at " + pos);
                        return;
                    }
                    if (y != ground) {
                        helper.fail(resource.key() + "'s disc is not flush at " + pos + ": the terrain is at " + ground);
                        return;
                    }
                    double r = Math.hypot(x - disc.centreX(), z - disc.centreZ());
                    if (r >= farthest) {
                        farthest = r;
                        farthestOre = pos;
                    }
                }
                if (ores > 1) {
                    helper.fail(resource.key() + "'s disc is " + ores + " deep at " + x + ", " + z);
                    return;
                }
                if ((ores == 1) != source.covers(x, z)) {
                    helper.fail(resource.key() + "'s disc " + (ores == 1 ? "placed" : "missed") + " " + x + ", " + z);
                    return;
                }
                placed += ores;
            }
        }
        if (placed != disc.blockCount()) {
            helper.fail(resource.key() + "'s piece stored " + disc.blockCount() + " blocks and " + placed + " were placed");
            return;
        }
        if (!level.getBlockState(trunk.below()).is(ore.getBlock()) || !level.getBlockState(trunk).is(Blocks.OAK_LOG)) {
            helper.fail(resource.key() + "'s disc did not land under the tree at " + trunk);
            return;
        }

        double radius = OutfieldLaw.of(resource).radius(disc.sizeFactor(), disc.distance());
        if (farthest < 0.5 * radius || farthest > 1.8 * radius) {
            helper.fail(resource.key() + "'s disc reaches " + farthest + " for a law radius of " + radius
                    + " at size " + disc.sizeFactor() + ", " + disc.distance() + " from origin");
            return;
        }

        int amount = disc.amountPerBlock();
        OreDelta.Draw draw = OreMining.draw(level, (OreBlock) ore.getBlock(), farthestOre);
        if (amount <= 0 || draw.paid() != 1 || draw.remaining() != amount - 1) {
            helper.fail(resource.key() + " at " + farthestOre + " drew " + draw + " from a disc holding " + amount + " a block");
            return;
        }
        if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(resource.drop()))) {
            helper.fail(resource.key() + " pays out " + resource.drop() + ", which nothing registers, so every draw pays air");
            return;
        }
        LOGGER.info("PATCH {} disc at {}, {}: size {}, {} blocks, law radius {}, reaches {}, {} a block",
                resource.key(), disc.centreX(), disc.centreZ(), disc.sizeFactor(), placed, radius, farthest, amount);
        helper.succeed();
    }

    private static void nothingInTheHole(GameTestHelper helper, OreResource resource) {
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        int reach = SectionPos.blockToSectionCoord(HOLE) + 1;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (distance(chunk) <= HOLE && generate(helper.getLevel(), structure, chunk).isValid()) {
                    helper.fail(resource.key() + " generated at " + chunk + ", " + distance(chunk) + " from origin");
                    return;
                }
            }
        }
        helper.succeed();
    }

    /** The structure's biomes hold land and no water, and a column whose biome is not valid takes no ore. */
    private static void nothingOffTheLand(GameTestHelper helper, OreResource resource, ChunkPos chunk) {
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        String wet = wetBiomeInLand(helper.getLevel(), structure.value());
        if (wet != null) {
            helper.fail(resource.key() + " may generate in " + wet);
            return;
        }
        if (generate(helper.getLevel(), structure, chunk, biome -> biome.is(Biomes.OCEAN)).isValid()) {
            helper.fail(resource.key() + " generated where its biome was not valid");
            return;
        }
        helper.succeed();
    }

    /** The first of the sea, a deep sea and a river that the patch land holds, or null. */
    static String wetBiomeInLand(ServerLevel level, Structure structure) {
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> key : List.of(Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.RIVER)) {
            if (structure.biomes().contains(biomes.getOrThrow(key))) {
                return key.identifier().toString();
            }
        }
        return structure.biomes().contains(biomes.getOrThrow(Biomes.PLAINS)) ? null : "nothing: plains is missing";
    }

    /** A switch off stops the resource generating where it otherwise would, and only that resource. */
    private static void switchedOff(GameTestHelper helper, OreResource resource, ChunkPos chunk) {
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        var generate = FieldworksConfig.generateSwitch(resource);
        generate.set(false);
        try {
            if (generate(helper.getLevel(), structure, chunk).isValid()) {
                helper.fail(resource.key() + " generated with its switch off");
                return;
            }
        } finally {
            generate.set(true);
        }
        if (!generate(helper.getLevel(), structure, chunk).isValid()) {
            helper.fail(resource.key() + " did not generate with its switch back on");
            return;
        }
        helper.succeed();
    }

    static List<BlockPos> oresIn(ServerLevel level, BoundingBox box) {
        List<BlockPos> ores = new ArrayList<>();
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, box.minX(), box.minZ()) - 1;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos pos = new BlockPos(x, ground, z);
                if (level.getBlockState(pos).getBlock() instanceof OreBlock) {
                    ores.add(pos);
                }
            }
        }
        return ores;
    }

    static Holder<Structure> resolve(GameTestHelper helper, OreResource resource) {
        Identifier id = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "outfield_" + resource.key());
        var set = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceKey.create(Registries.STRUCTURE_SET, id));
        if (set.isEmpty()) {
            helper.fail("no structure set " + id);
            return null;
        }
        StructureSet value = set.get().value();
        if (!(value.placement() instanceof RandomSpreadStructurePlacement)) {
            helper.fail(id + " is not a random spread");
            return null;
        }
        Holder<Structure> structure = value.structures().getFirst().structure();
        if (!(structure.value() instanceof OutfieldDiscStructure disc) || disc.resource() != resource) {
            helper.fail(id + " holds " + structure.value() + ", not " + resource.key() + "'s disc");
            return null;
        }
        if (!structure.value().biomes().unwrapKey().map(LAND::equals).orElse(false)) {
            helper.fail(id + " is confined to " + structure.value().biomes() + ", not " + LAND.location());
            return null;
        }
        return structure;
    }

    static StructureStart generate(ServerLevel level, Holder<Structure> structure, ChunkPos chunk) {
        // The flat test world has no land biome; the tag is asserted in resolve().
        return generate(level, structure, chunk, biome -> true);
    }

    static StructureStart generate(ServerLevel level, Holder<Structure> structure, ChunkPos chunk,
            Predicate<Holder<Biome>> validBiome) {
        var generator = level.getChunkSource().getGenerator();
        return structure.value().generate(structure, level.dimension(), level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunk, 0, level, validBiome);
    }

    static void loadChunks(ServerLevel level, BoundingBox box) {
        for (int x = SectionPos.blockToSectionCoord(box.minX()); x <= SectionPos.blockToSectionCoord(box.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ()); z <= SectionPos.blockToSectionCoord(box.maxZ()); z++) {
                level.getChunk(x, z);
            }
        }
    }

    static void place(ServerLevel level, Structure structure, StructureStart start, ChunkPos origin,
            BoundingBox box) {
        level.getChunk(origin.x(), origin.z()).setStartForStructure(structure, start);
        for (int x = SectionPos.blockToSectionCoord(box.minX()); x <= SectionPos.blockToSectionCoord(box.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ()); z <= SectionPos.blockToSectionCoord(box.maxZ()); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                level.getChunk(x, z).addReferenceForStructure(structure, origin.pack());
                start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                        level.getRandom(), new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                                chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ()), chunk);
            }
        }
    }

    static double distance(ChunkPos chunk) {
        return Math.hypot(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
    }
}
