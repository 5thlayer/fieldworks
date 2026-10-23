// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.Fieldworks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** A superflat world, whatever its preset lists, places the patches and oil fields. */
final class FlatWorldTests {

    private FlatWorldTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("a_superflat_world_listing_only_villages_places_the_patches_and_oil_fields", 20,
                FlatWorldTests::flatWorldPlacesPatches);
    }

    private static void flatWorldPlacesPatches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup<StructureSet> structureSets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        FlatLevelGeneratorSettings settings = FlatLevelGeneratorSettings.getDefault(
                level.registryAccess().lookupOrThrow(Registries.BIOME), structureSets,
                level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE));
        FlatLevelGeneratorSettings villagesOnly = settings.withBiomeAndLayers(settings.getLayersInfo(),
                Optional.of(HolderSet.direct(structureSets.getOrThrow(BuiltinStructureSets.VILLAGES))),
                settings.getBiome());
        List<Holder<StructureSet>> placed = new FlatLevelSource(villagesOnly)
                .createState(structureSets, level.getChunkSource().randomState(), level.getSeed())
                .possibleStructureSets();
        for (String path : List.of("patch_iron", "patch_copper", "patch_coal", "patch_stone", "patch_sulfur",
                "patch_uranium", "patch_redstone", "patch_gold", "patch_diamond", "oil_field")) {
            ResourceKey<StructureSet> key = ResourceKey.create(Registries.STRUCTURE_SET,
                    Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, path));
            helper.assertTrue(placed.stream().anyMatch(set -> set.is(key)), "a superflat world does not place " + key);
        }
        helper.assertTrue(placed.stream().anyMatch(set -> set.is(BuiltinStructureSets.VILLAGES)),
                "a superflat world lost the villages its preset lists");
        helper.succeed();
    }
}
