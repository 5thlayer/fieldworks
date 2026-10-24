// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;

import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.PatchDisc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Hand mining: a vanilla pickaxe at its vanilla tier takes one unit per break, and a weaker tool takes none. A resource
 * whose Default recipe is replaced is drawn by no hand ({@link HandGateTests}).
 */
final class HandMiningTests {

    private HandMiningTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_stone_pickaxe_takes_one_unit_of_iron_per_break", 20,
                helper -> mine(helper, OreResource.IRON, new ChunkPos(100, 180), Items.STONE_PICKAXE, 1));
        tests.test("a_wooden_pickaxe_takes_nothing_from_iron", 20,
                helper -> mine(helper, OreResource.IRON, new ChunkPos(108, 180), Items.WOODEN_PICKAXE, 0));
        tests.test("a_bare_hand_takes_nothing_from_coal", 20,
                helper -> mine(helper, OreResource.COAL, new ChunkPos(116, 180), Items.AIR, 0));
        tests.test("a_stone_pickaxe_takes_nothing_from_uranium", 20,
                helper -> mine(helper, OreResource.URANIUM, new ChunkPos(124, 180), Items.STONE_PICKAXE, 0));
        tests.test("hand_gate_an_iron_pickaxe_takes_nothing_from_uranium", 20,
                helper -> mine(helper, OreResource.URANIUM, new ChunkPos(132, 180), Items.IRON_PICKAXE, 0));
        tests.test("an_ore_block_outside_a_patch_yields_its_default_amount", 20,
                helper -> mineUnpatched(helper, OreResource.IRON));
    }

    /** An ore block placed where no patch stands starts at its resource's default amount, not zero. */
    private static void mineUnpatched(GameTestHelper helper, OreResource resource) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        OreBlock ore = FieldworksRegistries.ore(resource).get();
        level.setBlockAndUpdate(pos, ore.defaultBlockState());
        int amount = resource.corpus().defaultAmount();

        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(pos);

        int remaining = OreMining.remaining(level, pos, ore);
        if (remaining != amount - 1) {
            helper.fail("an unpatched " + resource.key() + " block should hold " + amount + " and left " + remaining + " after one break");
            return;
        }
        Item drop = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse(resource.drop()));
        int paid = level.getEntities(EntityType.ITEM, new AABB(pos).inflate(2), e -> true).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(drop))
                .mapToInt(ItemStack::getCount)
                .sum();
        if (paid != 1) {
            helper.fail("an unpatched " + resource.key() + " block paid " + paid + ", expected 1");
            return;
        }
        helper.succeed();
    }

    /** Breaks the patch's first block once with {@code tool} and expects {@code units} drawn and paid. */
    static void mine(GameTestHelper helper, OreResource resource, ChunkPos chunk, Item tool, int units) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> structure = PatchDiscTests.resolve(helper, resource);
        if (structure == null) {
            return;
        }
        StructureStart start = PatchDiscTests.generate(level, structure, chunk);
        if (!start.isValid() || !(start.getPieces().getFirst() instanceof PatchDisc.Source source)) {
            helper.fail(resource.key() + " generated no disc at " + chunk);
            return;
        }
        BoundingBox box = start.getBoundingBox();
        PatchDiscTests.loadChunks(level, box);
        PatchDiscTests.place(level, structure.value(), start, chunk, box);
        List<BlockPos> ores = PatchDiscTests.oresIn(level, box);
        if (ores.isEmpty()) {
            helper.fail(resource.key() + "'s disc placed nothing");
            return;
        }
        BlockPos pos = ores.getFirst();
        OreBlock ore = (OreBlock) level.getBlockState(pos).getBlock();
        int amount = source.disc().amountPerBlock();

        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        player.gameMode.destroyBlock(pos);

        if (!(level.getBlockState(pos).getBlock() instanceof OreBlock)) {
            helper.fail("a hand break removed the " + resource.key() + " block, leaving " + level.getBlockState(pos));
            return;
        }
        int remaining = OreMining.remaining(level, pos, ore);
        if (remaining != amount - units) {
            helper.fail(resource.key() + " held " + amount + " and a break with " + tool + " left " + remaining);
            return;
        }
        Item drop = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse(resource.drop()));
        int paid = level.getEntities(EntityType.ITEM, new AABB(pos).inflate(2), e -> true).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(drop))
                .mapToInt(ItemStack::getCount)
                .sum();
        if (paid != units) {
            helper.fail("a break with " + tool + " paid " + paid + " " + resource.drop() + ", expected " + units);
            return;
        }
        helper.succeed();
    }
}
