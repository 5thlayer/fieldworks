// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Library's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code scripts/build-gametest-structures.py} writes, and sets up
 * what it needs itself, so the setup is in the diff.
 */
public final class FieldworksGameTests {

    private static final String GAMETEST_PACK = "fieldworks.gametestPack";

    private static final Identifier PLATFORM = id("gametest/platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Fieldworks.MOD_ID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
    }

    private FieldworksGameTests() {
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        // Posted only when game tests are enabled, so a production server never registers the tests.
        modBus.addListener(FieldworksGameTests::registerTests);
        if (Boolean.getBoolean(GAMETEST_PACK)) {
            modBus.addListener(FieldworksGameTests::addGameTestPack);
            // The tests' own fluid-handler block, which a player's world never has.
            TestTank.register(modBus);
        }
    }

    /**
     * The recipes only the game tests read, in {@code gametest_pack/} in the jar. Only the
     * {@code gameTestServer} run sets {@value #GAMETEST_PACK}, so no dev or player world sees them.
     */
    private static void addGameTestPack(AddPackFindersEvent event) {
        event.addPackFinders(id("gametest_pack"), PackType.SERVER_DATA, Component.literal("Fieldworks game tests"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment);
        LoadTests.register(tests);
        DataTests.register(tests);
        PatchDiscTests.register(tests);
        OilFieldTests.register(tests);
        HandMiningTests.register(tests);
        HarvesterTests.register(tests);
        HarvestRecipeTests.register(tests);
        HarvestableBlockTests.register(tests);
        HarvesterWellTests.register(tests);
        HarvesterPowerTests.register(tests);
        HeldRecipeTests.register(tests);
        DrillingMudTests.register(tests);
        DrillingFluidTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(environment, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
