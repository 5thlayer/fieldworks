// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.FieldworksTags;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** The tags other mods find the Module's resources by. */
final class DataTests {

    private DataTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("crude_oil_is_tagged_c_crude_oil", 1, DataTests::crudeOil);
        tests.test("the_ores_are_tagged_c_ores", 1, DataTests::ores);
        tests.test("each_ore_but_stone_is_tagged_c_ores_by_its_resource", 1, DataTests::oresByResource);
        tests.test("sulfur_and_uranium_carry_their_c_tags", 1, DataTests::items);
        tests.test("the_harvestable_tag_holds_every_block_in_c_ores_and_vanilla_ores_among_them", 1, DataTests::harvestable);
        tests.test("every_tag_fieldworks_reads_holds_an_entry_of_minecraft_or_fieldworks", 1, DataTests::tagsPlayAlone);
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

    /**
     * Each ore is in {@code c:ores/<resource>} and no other {@code c:ores/} subtag (#21). Stone ore is in none:
     * {@code c:ores/stone} is no tag other mods read, and a stone patch yields cobblestone.
     */
    private static void oresByResource(GameTestHelper helper) {
        for (OreResource resource : OreResource.values()) {
            Block ore = FieldworksRegistries.ore(resource).get();
            Set<String> subtags = ore.builtInRegistryHolder().tags()
                    .map(TagKey::location)
                    .filter(id -> id.getNamespace().equals("c") && id.getPath().startsWith("ores/"))
                    .map(Identifier::toString)
                    .collect(Collectors.toSet());
            Set<String> expected = resource == OreResource.STONE ? Set.of() : Set.of("c:ores/" + resource.key());
            if (!subtags.equals(expected)) {
                helper.fail(resource.blockName() + " is in " + subtags + ", not " + expected);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * {@code fieldworks:harvestable} defaults to {@code #c:ores} (#19): every block in {@code c:ores} is in it, which a
     * hand-written list of ores would miss, and that reaches vanilla's ores.
     */
    private static void harvestable(GameTestHelper helper) {
        TagKey<Block> ores = TagKey.create(Registries.BLOCK, Identifier.parse("c:ores"));
        boolean vanilla = false;
        for (Holder<Block> ore : BuiltInRegistries.BLOCK.getTagOrEmpty(ores)) {
            if (!ore.value().defaultBlockState().is(FieldworksTags.HARVESTABLE)) {
                helper.fail(BuiltInRegistries.BLOCK.getKey(ore.value()) + " is in c:ores but not in " + FieldworksTags.HARVESTABLE.location());
                return;
            }
            vanilla |= BuiltInRegistries.BLOCK.getKey(ore.value()).getNamespace().equals("minecraft");
        }
        if (!vanilla) {
            helper.fail("no vanilla ore is in " + FieldworksTags.HARVESTABLE.location());
            return;
        }
        helper.succeed();
    }

    /**
     * Fieldworks plays alone on Groundworks (#20): each tag it reads reaches at least one entry with no other mod
     * installed, Minecraft's or its own. A tag filled only by other mods would leave it inert.
     */
    private static void tagsPlayAlone(GameTestHelper helper) {
        List<TagKey<?>> read = List.of(FieldworksTags.HARVESTABLE, FieldworksTags.DRILLING_FLUIDS, FieldworksTags.CRUDE_OIL,
                FieldworksTags.FLAT_WORLD_STRUCTURE_SETS,
                FieldworksTags.DRILLING_FLUIDS_OVERWORLD, FieldworksTags.DRILLING_FLUIDS_NETHER,
                TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_land")),
                TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_nether")));
        var alone = Set.of("minecraft", Fieldworks.MOD_ID);
        for (TagKey<?> tag : read) {
            if (!reachesAlone(helper, tag, alone)) {
                helper.fail(tag.location() + " holds no entry of " + alone);
                return;
            }
        }
        helper.succeed();
    }

    private static <T> boolean reachesAlone(GameTestHelper helper, TagKey<T> tag, Set<String> namespaces) {
        return helper.getLevel().registryAccess().lookupOrThrow(tag.registry()).get(tag).stream()
                .flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().filter(key -> namespaces.contains(key.identifier().getNamespace())).isPresent());
    }

    private static void items(GameTestHelper helper) {
        TagKey<net.minecraft.world.item.Item> sulfur = TagKey.create(Registries.ITEM, Identifier.parse("c:dusts/sulfur"));
        TagKey<net.minecraft.world.item.Item> uranium = TagKey.create(Registries.ITEM, Identifier.parse("c:ingots/uranium"));
        TagKey<net.minecraft.world.item.Item> rawUranium = TagKey.create(Registries.ITEM, Identifier.parse("c:raw_materials/uranium"));
        if (!FieldworksRegistries.SULFUR.get().getDefaultInstance().is(sulfur)) {
            helper.fail("sulfur is not in c:dusts/sulfur");
            return;
        }
        if (!FieldworksRegistries.URANIUM_INGOT.get().getDefaultInstance().is(uranium)) {
            helper.fail("the uranium ingot is not in c:ingots/uranium");
            return;
        }
        if (!FieldworksRegistries.RAW_URANIUM.get().getDefaultInstance().is(rawUranium)) {
            helper.fail("raw uranium is not in c:raw_materials/uranium");
            return;
        }
        helper.succeed();
    }
}
