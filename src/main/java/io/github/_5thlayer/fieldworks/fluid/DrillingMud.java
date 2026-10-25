// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Drilling mud, the first Drilling Fluid (#11): the fluid type, still and flowing fluids, a liquid block and a bucket,
 * crafted from a water bucket and a clay ball, and no bottles (ADR 0012). It is in
 * {@code #fieldworks:drilling_fluids}, which is what a Harvest Recipe's fluids are checked against.
 */
public final class DrillingMud {

    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Fieldworks.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Fieldworks.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fieldworks.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> TYPE = FLUID_TYPES.register(
            "drilling_mud",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + Fieldworks.MOD_ID + ".drilling_mud")
                    .lightLevel(0)
                    .density(1500)
                    .temperature(298)
                    .viscosity(4000)
                    .rarity(Rarity.COMMON)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SOURCE =
            FLUIDS.register("drilling_mud", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING =
            FLUIDS.register("flowing_drilling_mud", () -> new BaseFlowingFluid.Flowing(properties()));

    public static final DeferredHolder<Block, LiquidBlock> BLOCK = BLOCKS.registerBlock("drilling_mud",
            props -> new LiquidBlock(SOURCE.get(), liquidProperties(props)));

    /** A plain {@link BucketItem}, not a subclass: NeoForge gives the fluid item capability to that exact class only. */
    public static final DeferredItem<BucketItem> BUCKET = ITEMS.registerItem("drilling_mud_bucket",
            props -> new BucketItem(SOURCE.get(), props),
            props -> props.craftRemainder(Items.BUCKET).stacksTo(1));

    private DrillingMud() {
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK).bucket(BUCKET);
    }

    /** Takes the properties rather than making them: a block's registry id rides on them. */
    private static BlockBehaviour.Properties liquidProperties(BlockBehaviour.Properties props) {
        return props
                .mapColor(MapColor.COLOR_BROWN)
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
        ITEMS.register(modBus);
    }
}
