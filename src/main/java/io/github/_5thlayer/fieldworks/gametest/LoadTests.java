// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.Set;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/**
 * The Module loads on a server with nothing else installed, at the version its build gave it. It
 * is the harness's own check too: a run that reports this test ran the Module's tests, not
 * vanilla's. The run has only Groundworks beside it, so it proves the Module plays alone (#20).
 */
final class LoadTests {

    private LoadTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("loads_on_a_server", 1, LoadTests::loads);
        tests.test("loads_with_only_groundworks_beside_it", 1, LoadTests::alone);
    }

    private static void loads(GameTestHelper helper) {
        var container = ModList.get().getModContainerById(Fieldworks.MOD_ID);
        if (container.isEmpty()) {
            helper.fail("the server has not loaded " + Fieldworks.MOD_ID);
            return;
        }
        // processResources writes gradle.properties into neoforge.mods.toml; unexpanded, the Pack's
        // pin and the loaded Module would disagree on what is running.
        String version = container.get().getModInfo().getVersion().toString();
        if (version.contains("$")) {
            helper.fail("neoforge.mods.toml's version was not expanded: " + version);
            return;
        }
        helper.succeed();
    }

    /** Groundworks is required and libworks is nested in the jar; any other mod is one the run should not load. */
    private static void alone(GameTestHelper helper) {
        var allowed = Set.of("minecraft", "neoforge", Fieldworks.MOD_ID, "groundworks", "libworks");
        var others = ModList.get().getMods().stream().map(mod -> mod.getModId()).filter(id -> !allowed.contains(id)).sorted().toList();
        if (!others.isEmpty()) {
            helper.fail("the game tests run beside " + others);
            return;
        }
        helper.succeed();
    }
}
