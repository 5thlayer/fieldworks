// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.harvester.HarvesterBlock;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which faces between a Harvester's bottom blocks the renderer hides ([issue 39, adjacent drill heads draw a wall between them](https://github.com/5thlayer/fieldworks/issues/39)): a casing block's side face toward a casing
 * neighbour, and none toward a drill head. The states stand alone, as the renderer sees them.
 */
final class HarvesterFaceTests {

    /** Parts 1 to 8 are the bottom layer's, 9 and up the upper layers'. */
    private static final int BOTTOM_PART = 1;
    private static final int OTHER_BOTTOM_PART = 2;
    private static final int UPPER_PART = 9;

    private HarvesterFaceTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("harvester_faces_the_origin_hides_a_face_toward_a_casing_bottom_block", 1, HarvesterFaceTests::originToCasing);
        tests.test("harvester_faces_a_part_hides_a_face_toward_a_casing_bottom_block", 1, HarvesterFaceTests::partToCasing);
        tests.test("harvester_faces_a_casing_face_shows_toward_a_drill_head", 1, HarvesterFaceTests::casingToHead);
        tests.test("harvester_faces_a_drill_head_hides_nothing", 1, HarvesterFaceTests::headHidesNothing);
        tests.test("harvester_faces_only_side_faces_are_hidden_and_not_toward_the_upper_layers", 1, HarvesterFaceTests::sidesOnly);
    }

    private static BlockState origin(boolean head) {
        return Harvesters.block(TierBlock.HARVESTER_1).get().defaultBlockState().setValue(HarvesterBlock.DRILL_HEAD, head);
    }

    private static BlockState part(int number, boolean head) {
        return Harvesters.footprint(TierBlock.HARVESTER_1).stateAt(number, Direction.NORTH)
                .setValue(HarvesterBlock.DRILL_HEAD, head);
    }

    private static void originToCasing(GameTestHelper helper) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.assertTrue(origin(false).skipRendering(part(BOTTOM_PART, false), side), "the origin shows a face toward casing, " + side);
        }
        helper.succeed();
    }

    private static void partToCasing(GameTestHelper helper) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.assertTrue(part(BOTTOM_PART, false).skipRendering(origin(false), side), "a part shows a face toward the origin's casing, " + side);
            helper.assertTrue(part(BOTTOM_PART, false).skipRendering(part(OTHER_BOTTOM_PART, false), side), "a part shows a face toward a part's casing, " + side);
        }
        helper.succeed();
    }

    private static void casingToHead(GameTestHelper helper) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.assertFalse(origin(false).skipRendering(part(BOTTOM_PART, true), side), "the origin hides its face toward a head, " + side);
            helper.assertFalse(part(BOTTOM_PART, false).skipRendering(origin(true), side), "a part hides its face toward the origin's head, " + side);
            helper.assertFalse(part(BOTTOM_PART, false).skipRendering(part(OTHER_BOTTOM_PART, true), side), "a part hides its face toward a part's head, " + side);
        }
        helper.succeed();
    }

    private static void headHidesNothing(GameTestHelper helper) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.assertFalse(origin(true).skipRendering(part(BOTTOM_PART, false), side), "the origin's head hides a face, " + side);
            helper.assertFalse(part(BOTTOM_PART, true).skipRendering(origin(false), side), "a part's head hides a face, " + side);
        }
        helper.succeed();
    }

    private static void sidesOnly(GameTestHelper helper) {
        helper.assertFalse(origin(false).skipRendering(part(BOTTOM_PART, false), Direction.UP), "a face upward is hidden");
        helper.assertFalse(origin(false).skipRendering(part(BOTTOM_PART, false), Direction.DOWN), "a face downward is hidden");
        helper.assertFalse(origin(false).skipRendering(part(UPPER_PART, false), Direction.NORTH), "a face toward an upper-layer part is hidden");
        helper.succeed();
    }
}
