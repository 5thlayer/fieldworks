// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A second crude for the tests, which keeps tar's promise for any fluid in {@code c:crude_oil}, not only
 * Fieldworks' own: a fluid of the tests' own, which the game-test pack ({@code gametest_pack/}, switched on
 * by DevPacks) puts in the tag. Registered only in the Module's own runs, from the {@code gametest}
 * source set, so no player's world has it.
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
    static final DeferredHolder<Block, CrudeBlock> BLOCK = BLOCKS.registerBlock("gametest_crude",
            props -> new CrudeBlock(SOURCE.get(), props.replaceable().noCollision().noLootTable().liquid()));

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

    /** The liquid block's constructor is protected. */
    static final class CrudeBlock extends LiquidBlock {
        CrudeBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
            super(fluid, properties);
        }
    }
}
