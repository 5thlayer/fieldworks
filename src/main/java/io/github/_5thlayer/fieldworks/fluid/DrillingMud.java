// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.fluid;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Drilling mud, the first Drilling Fluid (#11): the fluid type, still and flowing fluids, a liquid block, a bucket
 * and a 250 mB bottle, brewed from a water bottle with a clay ball or a slime ball. It is in
 * {@code #fieldworks:drilling_fluids}, which is what a Harvest Recipe's fluids are checked against.
 */
public final class DrillingMud {

    /** What a bottle holds, in mB. */
    public static final int BOTTLE_VOLUME = 250;

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

    public static final DeferredItem<Item> BOTTLE = ITEMS.registerItem("drilling_mud_bottle",
            Item::new, props -> props.craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));

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
        modBus.addListener(DrillingMud::registerCapabilities);
        modBus.addListener(DrillingMud::addToCreativeTabs);
        // A game bus event, posted on the server and again on a client's login.
        NeoForge.EVENT_BUS.addListener(DrillingMud::registerBrewing);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new BottleHandler(access), BOTTLE.get());
    }

    /** A water bottle with a clay ball or a slime ball: each bottle in a stand becomes a mud bottle. */
    private static void registerBrewing(RegisterBrewingRecipesEvent event) {
        Ingredient water = DataComponentIngredient.of(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER), Items.POTION);
        event.getBuilder().addRecipe(water, Ingredient.of(Items.CLAY_BALL), new ItemStack(BOTTLE.get()));
        event.getBuilder().addRecipe(water, Ingredient.of(Items.SLIME_BALL), new ItemStack(BOTTLE.get()));
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BUCKET.get());
            event.accept(BOTTLE.get());
        }
    }

    /**
     * A mud bottle's fluid: 250 mB of mud, all or nothing like a bucket's, and a glass bottle once emptied. The
     * glass bottle has no capability of its own, so a bottle only drains.
     */
    private static final class BottleHandler extends ItemAccessResourceHandler<FluidResource> {

        BottleHandler(ItemAccess access) {
            super(access, 1);
        }

        @Override
        protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
            return accessResource.is(BOTTLE.get()) ? FluidResource.of(SOURCE.get()) : FluidResource.EMPTY;
        }

        @Override
        protected int getAmountFrom(ItemResource accessResource, int index) {
            return accessResource.is(BOTTLE.get()) ? BOTTLE_VOLUME : 0;
        }

        @Override
        protected ItemResource update(ItemResource accessResource, int index, FluidResource newResource, int newAmount) {
            if (newAmount == 0) {
                return ItemResource.of(Items.GLASS_BOTTLE);
            }
            return newAmount == BOTTLE_VOLUME && newResource.getFluid() == SOURCE.get() ? ItemResource.of(BOTTLE.get()) : ItemResource.EMPTY;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.getFluid() == SOURCE.get();
        }

        @Override
        protected int getCapacity(int index, FluidResource resource) {
            return BOTTLE_VOLUME;
        }
    }
}
