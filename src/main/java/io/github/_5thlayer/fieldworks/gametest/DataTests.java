// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

/** The tags other mods find the Library's resources by. */
final class DataTests {

    private DataTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("crude_oil_is_tagged_c_crude_oil", 1, DataTests::crudeOil);
        tests.test("the_ores_are_tagged_c_ores", 1, DataTests::ores);
        tests.test("sulfur_and_uranium_carry_their_c_tags", 1, DataTests::items);
    }

    private static void crudeOil(GameTestHelper helper) {
        TagKey<net.minecraft.world.level.material.Fluid> tag = TagKey.create(Registries.FLUID, Identifier.parse("c:crude_oil"));
        if (!CrudeOil.SOURCE.get().defaultFluidState().is(tag) || !CrudeOil.FLOWING.get().defaultFluidState().is(tag)) {
            helper.fail("crude oil is not in c:crude_oil");
            return;
        }
        helper.succeed();
    }

    private static void ores(GameTestHelper helper) {
        TagKey<net.minecraft.world.level.block.Block> tag = TagKey.create(Registries.BLOCK, Identifier.parse("c:ores"));
        for (OreResource resource : OreResource.values()) {
            if (!FieldworksRegistries.ore(resource).get().defaultBlockState().is(tag)) {
                helper.fail(resource.blockName() + " is not in c:ores");
                return;
            }
        }
        helper.succeed();
    }

    private static void items(GameTestHelper helper) {
        TagKey<net.minecraft.world.item.Item> sulfur = TagKey.create(Registries.ITEM, Identifier.parse("c:dusts/sulfur"));
        TagKey<net.minecraft.world.item.Item> uranium = TagKey.create(Registries.ITEM, Identifier.parse("c:ingots/uranium"));
        if (!FieldworksRegistries.SULFUR.get().getDefaultInstance().is(sulfur)) {
            helper.fail("sulfur is not in c:dusts/sulfur");
            return;
        }
        if (!FieldworksRegistries.URANIUM_INGOT.get().getDefaultInstance().is(uranium)) {
            helper.fail("the uranium ingot is not in c:ingots/uranium");
            return;
        }
        helper.succeed();
    }
}
