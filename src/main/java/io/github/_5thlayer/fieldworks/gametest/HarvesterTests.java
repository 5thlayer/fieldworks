// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreDelta;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.Rotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Harvester on a real server (#5): placed whole from its item over the platform's middle, it works
 * the layer under it, {@code y = 1}, and fills the platform's height.
 */
final class HarvesterTests {

    /** The origin, at the bottom centre of the 3x3x3 footprint, stands over the middle of the layer. */
    static final BlockPos ORIGIN = new BlockPos(4, 2, 4);

    private HarvesterTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_burner_harvester_places_rotates_and_breaks_as_one_footprint", 20, HarvesterTests::wholeFootprint);
        tests.test("a_burner_harvester_fed_coal_by_hopper_draws_its_layer_into_the_chest_at_its_output_face", 200,
                HarvesterTests::drawsIntoTheChest);
        tests.test("a_burner_harvester_with_a_full_chest_stops_and_drops_nothing", 20, HarvesterTests::stopsWhenFull);
    }

    private static void wholeFootprint(GameTestHelper helper) {
        helper.setBlock(ORIGIN.below(), Blocks.STONE);
        FakePlayer player = place(helper, Direction.NORTH);

        Rotate.press(player, helper.absolutePos(ORIGIN.above().west()), false);
        Direction turned = helper.getBlockState(ORIGIN).getValue(Footprint.FACING);
        helper.assertTrue(turned != Direction.NORTH, "Rotate in Place left the Harvester facing north");
        expectStanding(helper, turned, TierBlock.HARVESTER_1);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN.above(2).south().east()));
        for (BlockPos pos : footprint().positions(helper.absolutePos(ORIGIN), turned)) {
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "the break left " + helper.getLevel().getBlockState(pos) + " at " + pos);
        }
        Item item = Harvesters.item(TierBlock.HARVESTER_1).get();
        int dropped = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(dropped == 1, "the break dropped " + dropped + " Harvesters, not 1");
        helper.succeed();
    }

    /**
     * Three iron ore blocks under the footprint, one on its last unit, and two just outside the layer: one
     * beside it and one below it. Three operations draw each block in the layer once.
     */
    private static void drawsIntoTheChest(GameTestHelper helper) {
        BlockPos last = new BlockPos(3, 1, 3);
        BlockPos centre = ORIGIN.below();
        BlockPos corner = new BlockPos(5, 1, 5);
        BlockPos beside = new BlockPos(6, 1, 4);
        BlockPos below = new BlockPos(4, 0, 4);
        int initial = ore(helper, centre, 0);
        ore(helper, corner, 0);
        ore(helper, beside, 0);
        ore(helper, below, 0);
        ore(helper, last, initial - 1);

        BlockPos chest = ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);
        BlockPos hopper = ORIGIN.relative(Direction.WEST, 2);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 4));
        place(helper, Direction.NORTH);

        helper.succeedWhen(() -> {
            int arrived = count(helper, chest, Items.RAW_IRON);
            helper.assertTrue(arrived == 3, arrived + " raw iron in the chest, waiting for 3");
            helper.assertBlockPresent(Blocks.STONE, last);
            helper.assertValueEqual(remaining(helper, centre), initial - 1, "the centre block's amount");
            helper.assertValueEqual(remaining(helper, corner), initial - 1, "the corner block's amount");
            helper.assertValueEqual(remaining(helper, beside), initial, "the amount of the block beside the layer");
            helper.assertValueEqual(remaining(helper, below), initial, "the amount of the block below the layer");
            helper.assertItemEntityNotPresent(Items.RAW_IRON);
        });
    }

    /**
     * A full chest at the output face: the Harvester draws until its own buffer is full too, then stops. Its
     * tick is run by hand, many times in one game tick, so the buffer fills without waiting on the clock.
     */
    private static void stopsWhenFull(GameTestHelper helper) {
        List<BlockPos> layer = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                layer.add(new BlockPos(x, 1, z));
            }
        }
        int initial = 0;
        for (BlockPos pos : layer) {
            initial = ore(helper, pos, 0);
        }
        BlockPos chest = ORIGIN.relative(Direction.NORTH, 2);
        helper.setBlock(chest, Blocks.CHEST);
        Container full = helper.getBlockEntity(chest, ChestBlockEntity.class);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        place(helper, Direction.NORTH);
        // Through the item capability of a block on the footprint's edge, as a hopper there would.
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            helper.assertValueEqual(items.insert(ItemResource.of(Items.COAL), 64, feeding), 64, "coal the Harvester took");
            feeding.commit();
        }

        HarvesterBlockEntity machine = helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
        tick(helper, machine, 15_000);
        int drawn = drawn(helper, layer, initial);
        tick(helper, machine, 1_000);

        helper.assertTrue(drawn > 0, "the Harvester drew nothing");
        helper.assertValueEqual(drawn(helper, layer, initial), drawn, "units drawn once the buffer was full");
        helper.assertValueEqual(count(helper, chest, Items.COBBLESTONE), 27 * 64, "cobblestone in the chest");
        helper.assertValueEqual(count(helper, chest, Items.RAW_IRON), 0, "raw iron in the full chest");
        helper.assertItemEntityNotPresent(Items.RAW_IRON);
        helper.succeed();
    }

    static void tick(GameTestHelper helper, HarvesterBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            machine.serverTick(helper.getLevel());
        }
    }

    private static int drawn(GameTestHelper helper, List<BlockPos> layer, int initial) {
        int drawn = 0;
        for (BlockPos pos : layer) {
            drawn += initial - remaining(helper, pos);
        }
        return drawn;
    }

    // -- fixtures -------------------------------------------------------------------------------

    /**
     * Sets an iron ore block outside any patch, with {@code drawn} units already taken, and returns its
     * initial amount. The chunk's other draws are kept: tests stand side by side and may share it.
     */
    static int ore(GameTestHelper helper, BlockPos rel, int drawn) {
        return ore(helper, OreResource.IRON, rel, drawn);
    }

    static int ore(GameTestHelper helper, OreResource resource, BlockPos rel, int drawn) {
        OreBlock ore = FieldworksRegistries.ore(resource).get();
        helper.setBlock(rel, ore);
        BlockPos pos = helper.absolutePos(rel);
        if (drawn > 0) {
            LevelChunk chunk = helper.getLevel().getChunkAt(pos);
            Map<Long, Integer> draws = new HashMap<>(chunk.getData(FieldworksRegistries.ORE_DELTA).drawn());
            draws.put(pos.asLong(), drawn);
            chunk.setData(FieldworksRegistries.ORE_DELTA, OreDelta.of(draws));
        }
        return OreMining.initialAmount(helper.getLevel(), ore, pos);
    }

    static int remaining(GameTestHelper helper, BlockPos rel) {
        BlockPos pos = helper.absolutePos(rel);
        if (!(helper.getLevel().getBlockState(pos).getBlock() instanceof OreBlock ore)) {
            return 0;
        }
        return OreMining.remaining(helper.getLevel(), pos, ore);
    }

    static int count(GameTestHelper helper, BlockPos chest, Item item) {
        Container container = helper.getBlockEntity(chest, ChestBlockEntity.class);
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                count += container.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static Footprint footprint() {
        return Harvesters.footprint(TierBlock.HARVESTER_1);
    }

    /**
     * Places a Burner Harvester facing {@code facing} by a click on the block under {@link #ORIGIN}, which the
     * test has set: a click on air would place it there instead. A footprint faces the player who lays it, and
     * that player is returned.
     */
    static FakePlayer place(GameTestHelper helper, Direction facing) {
        return place(helper, facing, TierBlock.HARVESTER_1);
    }

    /** Places the Harvester of {@code tier} as {@link #place(GameTestHelper, Direction)} does. */
    static FakePlayer place(GameTestHelper helper, Direction facing, TierBlock tier) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fieldworks_harvester"));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)));
        player.setPos(feet.x, feet.y, feet.z);
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(facing.getOpposite().toYRot());
        ItemStack stack = new ItemStack(Harvesters.item(tier).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(ORIGIN.below());
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
        expectStanding(helper, facing, tier);
        return player;
    }

    private static void expectStanding(GameTestHelper helper, Direction facing, TierBlock tier) {
        Footprint footprint = Harvesters.footprint(tier);
        List<BlockPos> positions = footprint.positions(helper.absolutePos(ORIGIN), facing);
        helper.assertTrue(positions.size() == 27, "a 3x3x3 footprint has " + positions.size() + " blocks");
        for (int i = 0; i < positions.size(); i++) {
            helper.assertTrue(helper.getLevel().getBlockState(positions.get(i)).equals(footprint.stateAt(i, facing)),
                    "block " + i + " at " + positions.get(i) + " is " + helper.getLevel().getBlockState(positions.get(i)));
        }
    }
}
