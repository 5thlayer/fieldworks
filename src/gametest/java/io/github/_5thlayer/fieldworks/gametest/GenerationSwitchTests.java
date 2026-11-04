// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

/** The Generation switch over all the others: off, no patch and no oil field generates, whatever their own switches say. */
final class GenerationSwitchTests {

    private GenerationSwitchTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("generation_switch_all_off_generates_no_patch_and_no_oil_field", 40, GenerationSwitchTests::allOff);
    }

    /** Where a structure generates with every switch on, tried again with the one over all of them off. */
    private record Site(String name, ServerLevel level, Holder<Structure> structure, ChunkPos chunk) {

        boolean generates() {
            return PatchDiscTests.generate(level, structure, chunk).isValid();
        }
    }

    private static void allOff(GameTestHelper helper) {
        List<Site> sites = new ArrayList<>();
        for (OreResource resource : OreResource.values()) {
            Holder<Structure> structure = PatchDiscTests.resolve(helper, resource);
            if (structure == null) {
                return;
            }
            ServerLevel level = PatchDiscTests.levelOf(helper, resource);
            ChunkPos at = PatchDiscTests.firstGenerating(level, structure, PatchDiscTests.discChunk(resource));
            if (at == null) {
                helper.fail(resource.key() + " generated nowhere near " + PatchDiscTests.discChunk(resource) + " with every switch on");
                return;
            }
            sites.add(new Site(resource.key(), level, structure, at));
        }
        Holder<Structure> oil = OilFieldTests.resolve(helper);
        if (oil == null) {
            return;
        }
        Site field = new Site("oil_field", helper.getLevel(), oil, OilFieldTests.FAR);
        if (!field.generates()) {
            helper.fail("no oil field generated at " + OilFieldTests.FAR + " with every switch on");
            return;
        }
        sites.add(field);

        var all = FieldworksConfig.generateAllSwitch();
        all.set(false);
        try {
            for (Site site : sites) {
                if (site.generates()) {
                    helper.fail(site.name() + " generated with the switch over all of them off");
                    return;
                }
            }
        } finally {
            all.set(true);
        }
        for (Site site : sites) {
            if (!site.generates()) {
                helper.fail(site.name() + " did not generate with the switch over all of them back on");
                return;
            }
        }
        helper.succeed();
    }
}
