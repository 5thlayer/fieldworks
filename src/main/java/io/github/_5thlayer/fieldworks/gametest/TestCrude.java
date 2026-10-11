// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A second crude, the tests' own, which the game test pack puts in {@code c:crude_oil}: it sets as
 * tar as Fieldworks' own crude does, which shows that the tag, not the fluid, decides. Registered only
 * on the game test run, so no player's world has it.
 */
final class TestCrude {

    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Fieldworks.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Fieldworks.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);

    static final DeferredHolder<FluidType, FluidType> TYPE = FLUID_TYPES.register("gametest_crude",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + Fieldworks.MOD_ID + ".gametest_crude")));
    static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SOURCE =
            FLUIDS.register("gametest_crude", () -> new BaseFlowingFluid.Source(properties()));
    static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING =
            FLUIDS.register("flowing_gametest_crude", () -> new BaseFlowingFluid.Flowing(properties()));
    static final DeferredHolder<Block, LiquidBlock> BLOCK = BLOCKS.registerBlock("gametest_crude",
            props -> new LiquidBlock(SOURCE.get(), props.replaceable().noCollision().noLootTable().liquid()));

    private TestCrude() {
    }

    static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK);
    }
}
