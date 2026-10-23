// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.LevelReader;
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
 * Magma slurry, the Nether Drilling Fluid (ADR-0132): the fluid type, still and flowing fluids, a liquid block and
 * a bucket, and no bottles. It is in {@code #fieldworks:drilling_fluids/nether}. It flows at lava's pace, faster in
 * a dimension where lava does, but it is not lava: it is not in {@code #minecraft:lava}, so it burns nothing, fuels
 * no furnace and sets no crude into tar. Like a bucket of any fluid it is reachable through NeoForge's fluid
 * capability.
 */
public final class MagmaSlurry {

    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Fieldworks.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Fieldworks.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fieldworks.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> TYPE = FLUID_TYPES.register(
            "magma_slurry",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + Fieldworks.MOD_ID + ".magma_slurry")
                    .lightLevel(4)
                    .density(2500)
                    .temperature(900)
                    .viscosity(6000)
                    .rarity(Rarity.COMMON)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SOURCE =
            FLUIDS.register("magma_slurry", () -> new Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING =
            FLUIDS.register("flowing_magma_slurry", () -> new Flowing(properties()));

    public static final DeferredHolder<Block, LiquidBlock> BLOCK = BLOCKS.registerBlock("magma_slurry",
            props -> new LiquidBlock(SOURCE.get(), liquidProperties(props)));

    /** A plain {@link BucketItem}, not a subclass: NeoForge gives the fluid item capability to that exact class only. */
    public static final DeferredItem<BucketItem> BUCKET = ITEMS.registerItem("magma_slurry_bucket",
            props -> new BucketItem(SOURCE.get(), props),
            props -> props.craftRemainder(Items.BUCKET).stacksTo(1));

    private MagmaSlurry() {
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK).bucket(BUCKET);
    }

    /** Takes the properties rather than making them: a block's registry id rides on them. */
    private static BlockBehaviour.Properties liquidProperties(BlockBehaviour.Properties props) {
        return props
                .mapColor(MapColor.COLOR_RED)
                .replaceable()
                .noCollision()
                .strength(100.0F)
                .lightLevel(state -> 4)
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY);
    }

    /** Whether the dimension's lava runs fast, as the Nether's does: the slurry then flows as fast. */
    private static boolean fast(LevelReader level) {
        return level.environmentAttributes().getDimensionValue(EnvironmentAttributes.FAST_LAVA);
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    /** The still fluid, flowing at lava's pace. */
    public static final class Source extends BaseFlowingFluid.Source {

        Source(BaseFlowingFluid.Properties properties) {
            super(properties);
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return fast(level) ? 10 : 30;
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return fast(level) ? 4 : 2;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return fast(level) ? 1 : 2;
        }
    }

    /** The flowing fluid, at lava's pace. */
    public static final class Flowing extends BaseFlowingFluid.Flowing {

        Flowing(BaseFlowingFluid.Properties properties) {
            super(properties);
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return fast(level) ? 10 : 30;
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return fast(level) ? 4 : 2;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return fast(level) ? 1 : 2;
        }
    }
}
