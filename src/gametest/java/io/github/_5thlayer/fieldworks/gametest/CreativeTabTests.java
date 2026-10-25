// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.libworks.testkit.WorksTabCheck;

/** Every Fieldworks item is in the suite's Works tab, in a fixed order, and in no other tab (#4). */
final class CreativeTabTests {

    private static final List<String> WORKS_TAB_ORDER = List.of(
            "harvester_1", "harvester_2",
            "iron_ore", "copper_ore", "coal_ore", "stone_ore", "uranium_ore",
            "sulfur_ore", "redstone_ore", "gold_ore", "diamond_ore", "quartz_ore", "glowstone_ore", "netherite_ore",
            "sulfur", "raw_uranium", "uranium_ingot", "tar",
            "crude_oil_bucket", "drilling_mud_bucket", "magma_slurry_bucket");

    private CreativeTabTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("works_tab_holds_every_item_in_order", 1,
                helper -> WorksTabCheck.inOrder(helper, Fieldworks.MOD_ID, WORKS_TAB_ORDER));
        tests.test("no_other_tab_holds_a_module_item", 1,
                helper -> WorksTabCheck.noOtherTab(helper, Fieldworks.MOD_ID));
    }
}
