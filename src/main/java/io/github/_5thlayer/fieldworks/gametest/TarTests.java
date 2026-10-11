// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.FieldworksTags;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Crude meeting lava sets as tar, through a NeoForge fluid interaction, whichever of the two arrives
 * second and on whichever side they touch. The cases stand on Fieldworks' crude oil, and two on the
 * tests' own crude, which shows that {@code c:crude_oil} decides. Each case sets its blocks and reads
 * the result within the tick, before either fluid flows, and clears them again, so no lava reaches a
 * neighbouring test.
 */
final class TarTests {

    /** Lava's place: high enough that crude fits under it, and the platform's floor is under that. */
    private static final BlockPos LAVA = new BlockPos(3, 2, 3);
    private static final BlockPos PISTON = new BlockPos(2, 1, 3);
    private static final BlockPos PUSHED = PISTON.east();
    private static final int PISTON_SETTLE = 10;

    /** Which of the two fluids is placed second, the one whose arrival the other must answer. */
    private enum Arriving { CRUDE, LAVA }

    /** The crude a case places: a source sets as tar, flowing crude never does. */
    private enum Crude { SOURCE, FLOWING }

    private TarTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("crude_source_placed_against_lava_sets_as_tar", 1,
                helper -> eachSide(helper, CrudeOil.BLOCK.get(), Arriving.CRUDE, Crude.SOURCE));
        tests.test("lava_placed_against_a_crude_source_sets_it_as_tar", 1,
                helper -> eachSide(helper, CrudeOil.BLOCK.get(), Arriving.LAVA, Crude.SOURCE));
        tests.test("flowing_crude_placed_against_lava_stays_crude", 1,
                helper -> eachSide(helper, CrudeOil.BLOCK.get(), Arriving.CRUDE, Crude.FLOWING));
        tests.test("lava_placed_against_flowing_crude_leaves_it_crude", 1,
                helper -> eachSide(helper, CrudeOil.BLOCK.get(), Arriving.LAVA, Crude.FLOWING));
        tests.test("another_crude_source_placed_against_lava_sets_as_tar", 1,
                helper -> eachSide(helper, TestCrude.BLOCK.get(), Arriving.CRUDE, Crude.SOURCE));
        tests.test("lava_placed_against_another_crude_source_sets_it_as_tar", 1,
                helper -> eachSide(helper, TestCrude.BLOCK.get(), Arriving.LAVA, Crude.SOURCE));
        tests.test("breaking_tar_drops_one_tar", 20, TarTests::brokenByHand);
        tests.test("piston_breaks_tar_into_one_tar", 40, TarTests::pushedByPiston);
        tests.test("tar_is_tagged_c_tar", 1, TarTests::isTagged);
    }

    /**
     * Puts crude, a source or flowing, against lava on each of the six sides, in the order asked, and
     * expects the crude set as tar only when it is a source, and the lava as it was.
     */
    private static void eachSide(GameTestHelper helper, LiquidBlock crudeBlock, Arriving arriving, Crude kind) {
        boolean source = kind == Crude.SOURCE;
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState crude = source
                ? crudeBlock.defaultBlockState()
                : crudeBlock.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
        for (Direction side : Direction.values()) {
            BlockPos at = LAVA.relative(side);
            if (arriving == Arriving.CRUDE) {
                helper.setBlock(LAVA, lava);
                helper.setBlock(at, crude);
            } else {
                helper.setBlock(at, crude);
                helper.setBlock(LAVA, lava);
            }
            BlockState left = helper.getBlockState(at);
            BlockState lavaLeft = helper.getBlockState(LAVA);
            helper.setBlock(at, Blocks.AIR);
            helper.setBlock(LAVA, Blocks.AIR);
            String what = (source ? "a crude source" : "flowing crude") + " on lava's " + side + " side, "
                    + (arriving == Arriving.CRUDE ? "placed after the lava" : "with the lava placed after it");
            if (source && !left.is(FieldworksRegistries.TAR.get())) {
                helper.fail(what + " became " + left + ", not tar", at);
            }
            if (!source && !left.equals(crude)) {
                helper.fail(what + " became " + left + ", not left as it was", at);
            }
            if (!lavaLeft.equals(lava)) {
                helper.fail(what + " left the lava as " + lavaLeft, LAVA);
            }
        }
        helper.succeed();
    }

    private static void brokenByHand(GameTestHelper helper) {
        helper.setBlock(PUSHED, FieldworksRegistries.TAR.get());
        // Not the helper's own destroyBlock: it drops nothing, which is the very thing under test.
        helper.getLevel().destroyBlock(helper.absolutePos(PUSHED), true, null);
        helper.startSequence()
                .thenExecute(() -> expectOneTar(helper, "breaking a tar block"))
                .thenSucceed();
    }

    /** A piston pushing tar breaks it, which is what lets a piston and a hopper collect it. */
    private static void pushedByPiston(GameTestHelper helper) {
        helper.setBlock(PUSHED, FieldworksRegistries.TAR.get());
        helper.setBlock(PISTON, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(PISTON.west(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenIdle(PISTON_SETTLE)
                .thenExecute(() -> {
                    if (helper.getBlockState(PUSHED).is(FieldworksRegistries.TAR.get())) {
                        helper.fail("the piston left the tar standing", PUSHED);
                    }
                    expectOneTar(helper, "a piston pushing a tar block");
                })
                .thenSucceed();
    }

    /** How Wireworks finds tar for its plastic resins, with no id of ours in its data. */
    private static void isTagged(GameTestHelper helper) {
        if (!new ItemStack(FieldworksRegistries.TAR_ITEM.get()).is(FieldworksTags.TAR)) {
            helper.fail("tar is not in " + FieldworksTags.TAR.location());
        }
        helper.succeed();
    }

    /** Exactly one tar lying in the test's area, and nothing else. */
    private static void expectOneTar(GameTestHelper helper, String what) {
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        int tar = drops.stream().filter(stack -> stack.is(FieldworksRegistries.TAR_ITEM.get()))
                .mapToInt(ItemStack::getCount).sum();
        if (tar != 1 || drops.size() != 1) {
            helper.fail(what + " dropped " + tar + " tar among " + drops.size() + " stacks, not one tar", PUSHED);
        }
    }
}
