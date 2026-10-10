// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.Rotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

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
    }

    private static void wholeFootprint(GameTestHelper helper) {
        helper.setBlock(ORIGIN.below(), Blocks.STONE);
        Placed harvester = place(helper, Direction.NORTH);

        BlockPos aimed = helper.absolutePos(ORIGIN.above().west());
        Rotate.press(harvester.player(), aimed, false);
        Direction turned = helper.getBlockState(ORIGIN).getValue(Footprint.FACING);
        helper.assertTrue(turned != Direction.NORTH, "Rotate in Place left the Harvester facing north");
        expectStanding(helper, turned);

        harvester.player().gameMode.destroyBlock(helper.absolutePos(ORIGIN.above(2).south().east()));
        for (BlockPos pos : footprint().positions(helper.absolutePos(ORIGIN), turned)) {
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "the break left " + helper.getLevel().getBlockState(pos) + " at " + pos);
        }
        Item item = Harvesters.item(TierBlock.HARVESTER_1).get();
        int dropped = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(dropped == 1, "the break dropped " + dropped + " Harvesters, not 1");
        helper.succeed();
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static Footprint footprint() {
        return Harvesters.footprint(TierBlock.HARVESTER_1);
    }

    /** A Harvester placed in a test, with the player who placed it. */
    record Placed(GameTestHelper helper, FakePlayer player, Direction facing) {
    }

    /**
     * Places a Burner Harvester facing {@code facing} by a click on the block under {@link #ORIGIN}, which the
     * test has set: a click on air would place it there instead. A footprint faces the player who lays it.
     */
    static Placed place(GameTestHelper helper, Direction facing) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fieldworks_harvester"));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)));
        player.setPos(feet.x, feet.y, feet.z);
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(facing.getOpposite().toYRot());
        ItemStack stack = new ItemStack(Harvesters.item(TierBlock.HARVESTER_1).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(ORIGIN.below());
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
        expectStanding(helper, facing);
        return new Placed(helper, player, facing);
    }

    private static void expectStanding(GameTestHelper helper, Direction facing) {
        List<BlockPos> positions = footprint().positions(helper.absolutePos(ORIGIN), facing);
        helper.assertTrue(positions.size() == 27, "a 3x3x3 footprint has " + positions.size() + " blocks");
        for (int i = 0; i < positions.size(); i++) {
            helper.assertTrue(helper.getLevel().getBlockState(positions.get(i)).equals(footprint().stateAt(i, facing)),
                    "block " + i + " at " + positions.get(i) + " is " + helper.getLevel().getBlockState(positions.get(i)));
        }
    }
}
