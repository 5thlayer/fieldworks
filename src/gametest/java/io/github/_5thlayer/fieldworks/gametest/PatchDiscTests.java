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
import io.github._5thlayer.fieldworks.marker.FoundPatchMarkers;
import io.github._5thlayer.fieldworks.marker.PatchMarker;
import io.github._5thlayer.fieldworks.marker.PatchMarkerData;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreDelta;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.PatchDimension;
import io.github._5thlayer.fieldworks.ore.PatchDisc;
import io.github._5thlayer.fieldworks.ore.PatchLaw;
import io.github._5thlayer.fieldworks.worldgen.PatchDiscStructure;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
final class PatchDiscTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final TagKey<Biome> LAND = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_land"));

    /** The Nether biomes a Nether patch may lie in (ADR-0132). */
    static final TagKey<Biome> NETHER_LAND = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_nether"));

    private static final int HOLE = 150;

    private PatchDiscTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        OreResource[] resources = OreResource.values();
        for (int i = 0; i < resources.length; i++) {
            OreResource resource = resources[i];
            ChunkPos far = new ChunkPos(100 + 8 * i, 100);
            if (nether(resource)) {
                tests.test("patches_" + resource.key() + "_disc_lies_on_the_open_nether_floor", 40,
                        helper -> netherDiscPlaces(helper, resource, far));
            } else {
                tests.test("patch_" + resource.key() + "_disc_places", 20, helper -> discPlaces(helper, resource, far));
            }
            tests.test("patches_" + resource.key() + "_nothing_inside_its_minimum_distance", 40,
                    helper -> nothingInsideTheMinimumDistance(helper, resource));
            tests.test("patch_" + resource.key() + "_nothing_off_the_land", 40,
                    helper -> nothingOffTheLand(helper, resource, far));
            tests.test("patch_" + resource.key() + "_switched_off_generates_nothing", 40,
                    helper -> switchedOff(helper, resource, far));
            tests.test("patches_" + resource.key() + "_generates_only_in_its_own_dimension", 40,
                    helper -> onlyInItsOwnDimension(helper, resource, far));
        }
        tests.test("patches_a_nether_ore_block_exhausted_becomes_netherrack_and_an_overworld_one_stone", 1000,
                PatchDiscTests::exhaustedBlocks);
        tests.test("patch_marker_is_found_once_and_removed_when_the_last_block_goes", 20,
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
        if (!start.isValid() || !(start.getPieces().getFirst() instanceof PatchDisc.Source source)) {
            helper.fail(resource.key() + " generated no disc at " + chunk);
            return;
        }
        PatchDisc disc = source.disc();
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
            UUID player, ChunkPos chunk, PatchDisc disc, List<BlockPos> ores) {
        String dimension = level.dimension().identifier().toString();
        MarkerDelivery.Amounts amounts = MarkerEvents.amounts(level.getServer());
        data.observe(player, dimension);
        List<PatchMarker> found = FoundPatchMarkers.centredIn(level, level.getChunk(chunk.x(), chunk.z()));
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
        if (!start.isValid() || !(start.getPieces().getFirst() instanceof PatchDisc.Source source)) {
            helper.fail(resource.key() + " generated no disc at " + chunk + ", " + distance(chunk) + " from origin");
            return;
        }
        PatchDisc disc = source.disc();
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

        double radius = PatchLaw.of(resource).radius(disc.sizeFactor(), disc.distance());
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

    /**
     * Nothing generates in the starting area, nor inside the resource's own minimum distance, all in
     * Overworld-equivalent blocks: a Nether chunk counts for eight times its distance from origin, so
     * the gate is crossed at an eighth of the blocks. A ring of chunks just inside the gate holds
     * nothing, and a ring just beyond it holds a patch.
     */
    private static void nothingInsideTheMinimumDistance(GameTestHelper helper, OreResource resource) {
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        ServerLevel level = levelOf(helper, resource);
        int scale = resource.dimension().distanceScale();
        int minimum = resource.corpus().minDistance();
        // The starting area first: every chunk within the placement radius, which for a Nether resource is an eighth of it.
        int hole = (int) Math.ceil((double) HOLE / scale);
        int reach = SectionPos.blockToSectionCoord(hole) + 1;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (distance(chunk) * scale <= HOLE && generate(level, structure, chunk).isValid()) {
                    helper.fail(resource.key() + " generated at " + chunk + ", " + distance(chunk) * scale + " from origin");
                    return;
                }
            }
        }
        if (minimum > 0) {
            for (int angle = 0; angle < 16; angle++) {
                double theta = Math.PI / 8 * angle;
                for (double inside : new double[] {minimum - 100, minimum - 40}) {
                    ChunkPos chunk = chunkAt(inside / scale, theta);
                    if (distance(chunk) * scale < minimum && generate(level, structure, chunk).isValid()) {
                        helper.fail(resource.key() + " generated at " + chunk + ", " + distance(chunk) * scale
                                + " from origin, inside its minimum distance " + minimum);
                        return;
                    }
                }
            }
        }
        // And beyond the gate it does generate, somewhere on a ring a little past it.
        double beyond = Math.max(minimum + 300, 600);
        int generated = 0;
        for (int angle = 0; angle < 32; angle++) {
            for (double extra : new double[] {0, 64, 128}) {
                ChunkPos chunk = chunkAt((beyond + extra) / scale, Math.PI / 16 * angle);
                if (generate(level, structure, chunk).isValid()) {
                    generated++;
                }
            }
        }
        if (generated == 0) {
            helper.fail(resource.key() + " generated nowhere on a ring " + beyond + " from origin, past its gate of " + minimum);
            return;
        }
        helper.succeed();
    }

    private static ChunkPos chunkAt(double blocks, double theta) {
        return new ChunkPos(SectionPos.blockToSectionCoord((int) Math.round(blocks * Math.cos(theta))),
                SectionPos.blockToSectionCoord((int) Math.round(blocks * Math.sin(theta))));
    }

    /**
     * A resource generates only in the dimension its Patch numbers name: its structure's biomes hold that
     * dimension's biomes and nothing of the other's, and a start there is refused by the other dimension's
     * real biomes, with the structure's own biome set as the test.
     */
    private static void onlyInItsOwnDimension(GameTestHelper helper, OreResource resource, ChunkPos chunk) {
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        List<ResourceKey<Biome>> theirs = nether(resource)
                ? List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.FOREST, Biomes.OCEAN, Biomes.THE_END)
                : List.of(Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY, Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST,
                        Biomes.BASALT_DELTAS, Biomes.THE_END);
        for (ResourceKey<Biome> key : theirs) {
            if (structure.value().biomes().contains(biomes.getOrThrow(key))) {
                helper.fail(resource.key() + " may generate in " + key.identifier());
                return;
            }
        }
        if (nether(resource)) {
            for (ResourceKey<Biome> key : List.of(Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY, Biomes.CRIMSON_FOREST,
                    Biomes.WARPED_FOREST, Biomes.BASALT_DELTAS)) {
                if (!structure.value().biomes().contains(biomes.getOrThrow(key))) {
                    helper.fail(resource.key() + " may not generate in the Nether biome " + key.identifier());
                    return;
                }
            }
        }
        // Each other dimension's real biome source, with only the structure's own biomes valid.
        for (ServerLevel level : List.of(helper.getLevel(), helper.getLevel().getServer().getLevel(Level.NETHER),
                helper.getLevel().getServer().getLevel(Level.END))) {
            if (level == null || level == levelOf(helper, resource)) {
                continue;
            }
            for (int i = 0; i < 4; i++) {
                ChunkPos at = new ChunkPos(chunk.x() + 3 * i, chunk.z());
                if (generate(level, structure, at, structure.value().biomes()::contains).isValid()) {
                    helper.fail(resource.key() + " generated in " + level.dimension().identifier() + " at " + at);
                    return;
                }
            }
        }
        helper.succeed();
    }

    /**
     * An ore block whose last unit is drawn becomes stone, or netherrack for a Nether resource, so a
     * patch lies flush with the ground it was dealt on (ADR-0132).
     */
    private static void exhaustedBlocks(GameTestHelper helper) {
        for (OreResource resource : List.of(OreResource.IRON, OreResource.QUARTZ, OreResource.NETHERITE, OreResource.DIAMOND)) {
            BlockPos rel = new BlockPos(2, 1, 2);
            OreBlock ore = FieldworksRegistries.ore(resource).get();
            helper.setBlock(rel, ore);
            BlockPos pos = helper.absolutePos(rel);
            int units = OreMining.initialAmount(helper.getLevel(), ore, pos);
            for (int unit = 0; unit < units; unit++) {
                helper.assertTrue(helper.getLevel().getBlockState(pos).is(ore), resource.key() + " ran out after " + unit + " of " + units);
                OreMining.draw(helper.getLevel(), ore, pos);
            }
            var spent = nether(resource) ? Blocks.NETHERRACK : Blocks.STONE;
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(spent),
                    resource.key() + " exhausted became " + helper.getLevel().getBlockState(pos) + ", not " + spent);
        }
        helper.succeed();
    }

    static boolean nether(OreResource resource) {
        return resource.dimension() == PatchDimension.NETHER;
    }

    /** The level a resource's patches lie in: the Overworld the test stands in, or the server's real Nether. */
    static ServerLevel levelOf(GameTestHelper helper, OreResource resource) {
        if (!nether(resource)) {
            return helper.getLevel();
        }
        ServerLevel level = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (level == null) {
            throw helper.assertionException("the game-test server has no Nether");
        }
        return level;
    }

    /**
     * A Nether patch in the server's real Nether, generated and placed through the structure's own biome
     * test, from the first chunk at or after {@code from} where one is found. Each ore block is the floor
     * itself, a solid block with air above it, under the bedrock roof and clear of lava, and the disc follows
     * the uneven floor column by column within eight blocks of the centre's, and the few columns it left
     * bare are ones the lava took or the floor put out of reach.
     */
    private static void netherDiscPlaces(GameTestHelper helper, OreResource resource, ChunkPos from) {
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        ServerLevel level = levelOf(helper, resource);
        StructureStart start = null;
        ChunkPos chunk = from;
        for (int i = 0; i < 80 && start == null; i++) {
            chunk = new ChunkPos(from.x() + i, from.z());
            StructureStart found = generate(level, structure, chunk, structure.value().biomes()::contains);
            if (found.isValid()) {
                start = found;
            }
        }
        if (start == null || !(start.getPieces().getFirst() instanceof PatchDisc.Source source)) {
            helper.fail(resource.key() + " generated no disc in the Nether in 80 chunks from " + from);
            return;
        }
        PatchDisc disc = source.disc();
        BoundingBox box = start.getBoundingBox();
        loadChunks(level, box);
        place(level, structure.value(), start, chunk, box);

        BlockState ore = FieldworksRegistries.ore(resource).get().defaultBlockState();
        int placed = 0;
        int missed = 0;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        BlockPos any = null;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                List<BlockPos> ores = new ArrayList<>();
                for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof OreBlock) {
                        ores.add(pos);
                    }
                }
                if (ores.size() > 1) {
                    helper.fail(resource.key() + "'s disc is " + ores.size() + " deep at " + x + ", " + z);
                    return;
                }
                if (ores.isEmpty()) {
                    // A column the disc covers and left bare is one whose floor lay under lava or out of the centre's reach.
                    if (source.covers(x, z)) {
                        missed++;
                    }
                    continue;
                }
                BlockPos pos = ores.getFirst();
                if (!source.covers(x, z)) {
                    helper.fail(resource.key() + "'s disc placed ore at " + pos + ", outside its columns");
                    return;
                }
                if (level.getBlockState(pos) != ore) {
                    helper.fail(resource.key() + "'s disc placed " + level.getBlockState(pos) + " at " + pos);
                    return;
                }
                if (pos.getY() >= level.getMinY() + level.dimensionType().logicalHeight() - 6) {
                    helper.fail(resource.key() + "'s disc placed ore at " + pos + ", in or on the bedrock roof");
                    return;
                }
                if (!level.getBlockState(pos.above()).isAir()) {
                    helper.fail(resource.key() + "'s ore at " + pos + " has " + level.getBlockState(pos.above()) + " above it, not air");
                    return;
                }
                if (level.getFluidState(pos.above()).is(FluidTags.LAVA) || level.getFluidState(pos).is(FluidTags.LAVA)) {
                    helper.fail(resource.key() + "'s ore at " + pos + " is in lava");
                    return;
                }
                lowest = Math.min(lowest, pos.getY());
                highest = Math.max(highest, pos.getY());
                any = pos;
                placed++;
            }
        }
        if (placed == 0) {
            helper.fail(resource.key() + "'s disc at " + chunk + " placed no ore");
            return;
        }
        if (missed > disc.blockCount() / 4) {
            helper.fail(resource.key() + "'s disc left " + missed + " of its " + disc.blockCount() + " columns bare");
            return;
        }
        // Each column within eight blocks of the centre's floor, so the disc is one place and not every cave level.
        if (highest - lowest > 16) {
            helper.fail(resource.key() + "'s disc spans floors from " + lowest + " to " + highest + ", more than 16 apart");
            return;
        }
        if (placed + missed != disc.blockCount()) {
            helper.fail(resource.key() + "'s piece stored " + disc.blockCount() + " blocks and " + placed + " were placed, with "
                    + missed + " left to the lava");
            return;
        }
        OreBlock block = (OreBlock) ore.getBlock();
        int amount = disc.amountPerBlock();
        OreDelta.Draw draw = OreMining.draw(level, block, any);
        if (amount <= 0 || draw.paid() != 1 || draw.remaining() != amount - 1) {
            helper.fail(resource.key() + " at " + any + " drew " + draw + " from a disc holding " + amount + " a block");
            return;
        }
        LOGGER.info("PATCH {} nether disc at {}, {}: {} blocks on a floor from {} to {}, {} left to the lava",
                resource.key(), disc.centreX(), disc.centreZ(), placed, lowest, highest, missed);
        helper.succeed();
    }

    /** The structure's biomes hold land and no water, and a column whose biome is not valid takes no ore. */
    private static void nothingOffTheLand(GameTestHelper helper, OreResource resource, ChunkPos chunk) {
        Holder<Structure> structure = resolve(helper, resource);
        if (structure == null) {
            return;
        }
        String wet = nether(resource) ? null : wetBiomeInLand(helper.getLevel(), structure.value());
        if (wet != null) {
            helper.fail(resource.key() + " may generate in " + wet);
            return;
        }
        if (generate(levelOf(helper, resource), structure, chunk, biome -> biome.is(Biomes.OCEAN)).isValid()) {
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
        ServerLevel level = levelOf(helper, resource);
        // A Nether resource may find no floor at one chunk, so the first chunk it generates at is the one switched.
        ChunkPos at = chunk;
        for (int i = 0; i < 80 && !generate(level, structure, at).isValid(); i++) {
            at = new ChunkPos(chunk.x() + i + 1, chunk.z());
        }
        if (!generate(level, structure, at).isValid()) {
            helper.fail(resource.key() + " generated nowhere near " + chunk + " with its switch on");
            return;
        }
        var generate = FieldworksConfig.generateSwitch(resource);
        generate.set(false);
        try {
            if (generate(level, structure, at).isValid()) {
                helper.fail(resource.key() + " generated with its switch off");
                return;
            }
        } finally {
            generate.set(true);
        }
        if (!generate(level, structure, at).isValid()) {
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
        Identifier id = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_" + resource.key());
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
        if (!(structure.value() instanceof PatchDiscStructure disc) || disc.resource() != resource) {
            helper.fail(id + " holds " + structure.value() + ", not " + resource.key() + "'s disc");
            return null;
        }
        TagKey<Biome> land = nether(resource) ? NETHER_LAND : LAND;
        if (!structure.value().biomes().unwrapKey().map(land::equals).orElse(false)) {
            helper.fail(id + " is confined to " + structure.value().biomes() + ", not " + land.location());
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
