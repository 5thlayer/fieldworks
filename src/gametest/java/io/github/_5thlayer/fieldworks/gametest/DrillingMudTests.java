// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.fluid.DrillingMudBrewing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Drilling mud (#11): brewed in a vanilla brewing stand, held by a bottle and a bucket that both expose NeoForge's
 * item fluid capability, and a Drilling Fluid by the tag a recipe's fluids are checked against.
 */
final class DrillingMudTests {

    private static final BlockPos STAND = new BlockPos(4, 1, 4);

    private DrillingMudTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        // A brew takes 400 ticks, run by the stand itself.
        tests.test("a_water_bottle_brewed_with_a_clay_ball_gives_a_drilling_mud_bottle", 460, helper -> brews(helper, pairing(Items.CLAY_BALL)));
        tests.test("a_water_bottle_brewed_with_a_clay_block_gives_a_drilling_mud_bottle", 460, helper -> brews(helper, pairing(Items.CLAY)));
        tests.test("the_drilling_mud_bottle_and_bucket_expose_the_item_fluid_capability_and_mud_is_a_drilling_fluid", 1,
                DrillingMudTests::exposesTheCapability);
    }

    /** The pairing the stand brews with {@code ingredient}. */
    private static DrillingMudBrewing.Pairing pairing(Item ingredient) {
        return DrillingMudBrewing.PAIRINGS.stream().filter(pairing -> pairing.ingredient() == ingredient).findFirst().orElseThrow();
    }

    /** Three of the pairing's bottles and its ingredient, with blaze powder to fuel the stand: three mud bottles come out. */
    private static void brews(GameTestHelper helper, DrillingMudBrewing.Pairing pairing) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND, BrewingStandBlockEntity.class);
        for (int slot = 0; slot < 3; slot++) {
            stand.setItem(slot, pairing.bottleStack());
        }
        stand.setItem(3, new ItemStack(pairing.ingredient()));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));

        helper.succeedWhen(() -> {
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(stand.getItem(slot).is(DrillingMud.BOTTLE.get()), "slot " + slot + " holds " + stand.getItem(slot));
            }
        });
    }

    private static void exposesTheCapability(GameTestHelper helper) {
        expectHolds(helper, new ItemStack(DrillingMud.BOTTLE.get()), 250);
        expectHolds(helper, new ItemStack(DrillingMud.BUCKET.get()), 1_000);

        Fluid mud = DrillingMud.SOURCE.get();
        TagKey<Fluid> drillingFluids = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("fieldworks", "drilling_fluids"));
        helper.assertTrue(mud.defaultFluidState().is(drillingFluids), "drilling mud is not in #fieldworks:drilling_fluids");
        helper.assertTrue(DrillingMud.FLOWING.get().defaultFluidState().is(drillingFluids), "flowing drilling mud is not in #fieldworks:drilling_fluids");
        helper.succeed();
    }

    private static void expectHolds(GameTestHelper helper, ItemStack container, int amount) {
        ResourceHandler<FluidResource> fluid = ItemAccess.forStack(container).getCapability(Capabilities.Fluid.ITEM);
        helper.assertTrue(fluid != null, container + " has no item fluid capability");
        helper.assertValueEqual(fluid.getResource(0), FluidResource.of(DrillingMud.SOURCE.get()), container + " holds");
        helper.assertValueEqual(fluid.getAmountAsInt(0), amount, "mB in " + container);
    }
}
