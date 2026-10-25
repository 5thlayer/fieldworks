// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Drilling mud (#11): crafted by the bucket from a water bucket and a clay ball, never brewed or bottled (ADR 0012,
 * #52), held by a bucket that exposes NeoForge's item fluid capability, and a Drilling Fluid by the tag a recipe's
 * fluids are checked against.
 */
final class DrillingMudTests {

    private DrillingMudTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("drilling_mud_is_crafted_from_a_water_bucket_and_a_clay_ball_leaving_no_empty_bucket", 1,
                DrillingMudTests::crafts);
        tests.test("drilling_mud_is_not_brewed_and_has_no_bottle", 1, DrillingMudTests::notBrewed);
        tests.test("drilling_mud_bucket_exposes_the_item_fluid_capability_and_mud_is_a_drilling_fluid", 1,
                DrillingMudTests::exposesTheCapability);
    }

    /** A water bucket and a clay ball craft a mud bucket, leaving no empty bucket in the grid; a clay block crafts nothing. */
    private static void crafts(GameTestHelper helper) {
        BucketCrafts.craftsLeavingNothing(helper, Items.WATER_BUCKET, Items.CLAY_BALL, Items.CLAY, DrillingMud.BUCKET.get());
        helper.succeed();
    }

    /** A brewing stand mixes nothing from a water bottle and clay, and no mud bottle is registered. */
    private static void notBrewed(GameTestHelper helper) {
        ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        for (ItemStack clay : List.of(new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY))) {
            helper.assertFalse(helper.getLevel().potionBrewing().hasMix(waterBottle, clay),
                    "a water bottle brews with " + clay);
        }
        BucketCrafts.hasNoBottle(helper, "drilling_mud");
        helper.succeed();
    }

    private static void exposesTheCapability(GameTestHelper helper) {
        ItemStack bucket = new ItemStack(DrillingMud.BUCKET.get());
        ResourceHandler<FluidResource> fluid = ItemAccess.forStack(bucket).getCapability(Capabilities.Fluid.ITEM);
        helper.assertTrue(fluid != null, "the mud bucket has no item fluid capability");
        helper.assertValueEqual(fluid.getResource(0), FluidResource.of(DrillingMud.SOURCE.get()), "the mud bucket holds");
        helper.assertValueEqual(fluid.getAmountAsInt(0), 1_000, "mB in the mud bucket");

        Fluid mud = DrillingMud.SOURCE.get();
        TagKey<Fluid> drillingFluids = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("fieldworks", "drilling_fluids"));
        helper.assertTrue(mud.defaultFluidState().is(drillingFluids), "drilling mud is not in #fieldworks:drilling_fluids");
        helper.assertTrue(DrillingMud.FLOWING.get().defaultFluidState().is(drillingFluids), "flowing drilling mud is not in #fieldworks:drilling_fluids");
        helper.succeed();
    }
}
