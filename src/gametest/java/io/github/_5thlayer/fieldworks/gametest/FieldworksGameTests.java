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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Module's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code sh ./gradlew buildGametestStructures} writes, and sets up
 * what it needs itself, so the setup is in the diff. It lives in the {@code gametest} source set, which
 * only the Module's own runs load, and hooks itself in, so the {@code @Mod} class names no game-test
 * code and nothing gates it (libworks ADR 0011).
 */
@EventBusSubscriber(modid = Fieldworks.MOD_ID)
public final class FieldworksGameTests {

    private static final Identifier PLATFORM = id("gametest/platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Fieldworks.MOD_ID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
    }

    private FieldworksGameTests() {
    }

    @SubscribeEvent
    static void construct(FMLConstructModEvent event) {
        register(event.getContainer().getEventBus());
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        // The tests' own fluid-handler block and second crude, which a player's world never has.
        TestTank.register(modBus);
        TestCrude.register(modBus);
        // Posted only when game tests are enabled, which a production game never is.
        modBus.addListener(FieldworksGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment);
        GalleryTests.register(tests);
        LoadTests.register(tests);
        DataTests.register(tests);
        PatchDiscTests.register(tests);
        PatchGateTests.register(tests);
        OilFieldTests.register(tests);
        ClearanceTests.register(tests);
        HandMiningTests.register(tests);
        HandGateTests.register(tests);
        HarvesterTests.register(tests);
        HarvesterReachTests.register(tests);
        HarvesterFaceTests.register(tests);
        HarvestRecipeTests.register(tests);
        HarvestableBlockTests.register(tests);
        HarvesterWellTests.register(tests);
        HarvesterPowerTests.register(tests);
        HarvesterLitTests.register(tests);
        HeldRecipeTests.register(tests);
        DrillingMudTests.register(tests);
        DrillingFluidTests.register(tests);
        HarvesterMenuTests.register(tests);
        FlooredWellTests.register(tests);
        FlatWorldTests.register(tests);
        CreativeTabTests.register(tests);
        TarTests.register(tests);
        HarvesterCraftingTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            register(name, maxTicks, body, environment);
        }

        /**
         * A test in a batch of its own, for one that reloads the server's data, as turning the Gallery
         * pack on does, under every test in its batch (libworks ADR 0010).
         */
        void testOnItsOwn(String name, int maxTicks, Consumer<GameTestHelper> body) {
            register(name, maxTicks, body, event.registerEnvironment(id("on_its_own/" + name), new TestEnvironmentDefinition.AllOf(List.of())));
        }

        private void register(String name, int maxTicks, Consumer<GameTestHelper> body, Holder<TestEnvironmentDefinition<?>> batch) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(batch, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
