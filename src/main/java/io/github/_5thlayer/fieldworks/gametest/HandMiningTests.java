// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;

import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.fieldworks.ore.OutfieldDisc;
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

/** Hand mining: a vanilla pickaxe at its vanilla tier takes one unit per break, and a weaker tool takes none. */
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
        tests.test("an_iron_pickaxe_takes_one_unit_of_uranium_per_break", 20,
                helper -> mine(helper, OreResource.URANIUM, new ChunkPos(132, 180), Items.IRON_PICKAXE, 1));
    }

    /** Breaks the patch's first block once with {@code tool} and expects {@code units} drawn and paid. */
    private static void mine(GameTestHelper helper, OreResource resource, ChunkPos chunk, Item tool, int units) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> structure = OutfieldDiscTests.resolve(helper, resource);
        if (structure == null) {
            return;
        }
        StructureStart start = OutfieldDiscTests.generate(level, structure, chunk);
        if (!start.isValid() || !(start.getPieces().getFirst() instanceof OutfieldDisc.Source source)) {
            helper.fail(resource.key() + " generated no disc at " + chunk);
            return;
        }
        BoundingBox box = start.getBoundingBox();
        OutfieldDiscTests.loadChunks(level, box);
        OutfieldDiscTests.place(level, structure.value(), start, chunk, box);
        List<BlockPos> ores = OutfieldDiscTests.oresIn(level, box);
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
