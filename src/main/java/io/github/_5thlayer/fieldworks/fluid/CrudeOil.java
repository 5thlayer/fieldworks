// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Crude oil: the fluid type, still and flowing fluids and a liquid block. It has no bucket and no
 * item of its own; it is tagged {@code c:crude_oil}, which is how other mods find it.
 */
public final class CrudeOil {

    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Fieldworks.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Fieldworks.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> TYPE = FLUID_TYPES.register(
            "crude_oil",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + Fieldworks.MOD_ID + ".crude_oil")
                    .lightLevel(0)
                    .density(900)
                    .temperature(298)
                    .viscosity(5000)
                    .rarity(Rarity.COMMON)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SOURCE =
            FLUIDS.register("crude_oil", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING =
            FLUIDS.register("flowing_crude_oil", () -> new BaseFlowingFluid.Flowing(properties()));

    public static final DeferredHolder<Block, LiquidBlock> BLOCK = BLOCKS.registerBlock("crude_oil",
            props -> new LiquidBlock(SOURCE.get(), liquidProperties(props)));

    private CrudeOil() {
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK);
    }

    /** Takes the properties rather than making them: a block's registry id rides on them. */
    private static BlockBehaviour.Properties liquidProperties(BlockBehaviour.Properties props) {
        return props
                .mapColor(MapColor.COLOR_BLACK)
                .replaceable()
                .noCollision()
                .strength(100.0F)
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY);
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
    }
}
