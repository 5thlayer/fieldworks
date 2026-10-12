// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.libworks.WorksTab;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/** Every Fieldworks item is in the suite's Works tab, in a fixed order, and in no other tab (#4). */
final class CreativeTabTests {

    private static final List<String> WORKS_TAB_ORDER = List.of(
            "harvester_1", "harvester_2",
            "iron_ore", "copper_ore", "coal_ore", "stone_ore", "uranium_ore",
            "sulfur_ore",
            "sulfur", "raw_uranium", "uranium_ingot",
            "crude_oil_bucket", "drilling_mud_bottle", "drilling_mud_bucket");

    private CreativeTabTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("works_tab_holds_every_item_in_order", 1, CreativeTabTests::worksTabInOrder);
        tests.test("no_other_tab_holds_a_library_item", 1, CreativeTabTests::noOtherTab);
    }

    private static void worksTabInOrder(GameTestHelper helper) {
        rebuild(helper);
        List<String> shown = ownItems(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY));
        if (!shown.equals(WORKS_TAB_ORDER)) {
            helper.fail("the Works tab shows " + shown + ", not " + WORKS_TAB_ORDER);
            return;
        }
        helper.succeed();
    }

    private static void noOtherTab(GameTestHelper helper) {
        rebuild(helper);
        // Empty tabs would pass the loop below without the Library's items reaching any tab.
        if (ownItems(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY)).isEmpty()) {
            helper.fail("the Works tab shows no Fieldworks item, so no tab was filled");
            return;
        }
        for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            CreativeModeTab tab = entry.getValue();
            // The search tab lists every other tab's items.
            if (entry.getKey() == WorksTab.KEY || tab.getType() == CreativeModeTab.Type.SEARCH) {
                continue;
            }
            List<String> own = ownItems(tab);
            if (!own.isEmpty()) {
                helper.fail("the tab " + entry.getKey().identifier() + " shows " + own);
                return;
            }
        }
        helper.succeed();
    }

    private static void rebuild(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true,
                helper.getLevel().registryAccess());
    }

    private static List<String> ownItems(CreativeModeTab tab) {
        return tab.getDisplayItems().stream()
                .map(ItemStack::getItem)
                .map(BuiltInRegistries.ITEM::getKey)
                .filter(id -> id.getNamespace().equals(Fieldworks.MOD_ID))
                .map(Identifier::getPath)
                .toList();
    }
}
