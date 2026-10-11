// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Optional;
import java.util.function.Predicate;

import io.github._5thlayer.fieldworks.FieldworksTags;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.DrillingFluid;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Tank;
import io.github._5thlayer.libworks.transfer.GuardedResourceHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Harvester's fluids (#9, #11): its product tank of crude and its Drilling Fluid tank, the fill and drain slots that
 * exchange containers with them, and the face the tanks show a pipe. An exchanged container goes to the output buffer.
 */
final class HarvesterFluids {

    /** A tank's capacity in mB: one fixed figure, not tier data (#1). */
    static final int TANK_CAPACITY = 8_000;

    private static final String FILL_KEY = "fill";
    private static final String DRAIN_KEY = "drain";
    private static final String PRODUCT_KEY = "product";
    private static final String DRILLING_KEY = "drilling_fluid";

    private final ItemStacksResourceHandler fill;
    private final ItemStacksResourceHandler drain;
    private final ItemStacksResourceHandler buffer;
    private final HarvesterTank product;
    private final HarvesterTank drillingFluidTank;
    private final ResourceHandler<FluidResource> face;

    HarvesterFluids(ItemStacksResourceHandler buffer, Runnable changed) {
        this.fill = new HarvesterSlots(1, changed);
        this.drain = new HarvesterSlots(1, changed);
        this.buffer = buffer;
        this.product = new HarvesterTank(fluid -> fluid == CrudeOil.SOURCE.get(), false, changed);
        this.drillingFluidTank = new HarvesterTank(fluid -> fluid.defaultFluidState().is(FieldworksTags.DRILLING_FLUIDS), true, changed);
        this.face = new CombinedResourceHandler<>(drillingFluidTank.face, product.face);
    }

    ItemStacksResourceHandler fill() {
        return fill;
    }

    ItemStacksResourceHandler drain() {
        return drain;
    }

    /**
     * The fluid capability: the Drilling Fluid tank takes a Drilling Fluid and gives nothing back, and the product tank
     * drains and takes nothing.
     */
    ResourceHandler<FluidResource> face() {
        return face;
    }

    FluidStack drillingFluid() {
        return drillingFluidTank.getResource(0).toStack(drillingFluidTank.getAmountAsInt(0));
    }

    FluidStack crude() {
        return product.getResource(0).toStack(product.getAmountAsInt(0));
    }

    /** Whether the product tank has room for {@code amount} mB more crude. */
    boolean fitsCrude(int amount) {
        return amount <= TANK_CAPACITY - product.getAmountAsInt(0);
    }

    void addCrude(int amount) {
        try (Transaction pumping = Transaction.openRoot()) {
            product.insert(0, crudeOil(), amount, pumping);
            pumping.commit();
        }
    }

    /** What the Drilling Fluid tank holds, as the choice reads it. */
    Tank choiceTank() {
        FluidResource held = drillingFluidTank.getResource(0);
        return held.isEmpty() ? Tank.EMPTY : new Tank(BuiltInRegistries.FLUID.getKey(held.getFluid()).toString(), drillingFluidTank.getAmountAsInt(0));
    }

    /** Takes one operation's Drilling Fluid from the tank, when {@code commit}, or says whether it is there. */
    boolean takeDrillingFluid(Optional<DrillingFluid> need, boolean commit) {
        if (need.isEmpty()) {
            return true;
        }
        FluidResource held = drillingFluidTank.getResource(0);
        if (held.isEmpty() || !need.get().fluids().contains(BuiltInRegistries.FLUID.getKey(held.getFluid()).toString())) {
            return false;
        }
        try (Transaction taking = Transaction.openRoot()) {
            if (drillingFluidTank.extract(0, held, need.get().amount(), taking) != need.get().amount()) {
                return false;
            }
            if (commit) {
                taking.commit();
            }
            return true;
        }
    }

    /**
     * Fills one container in the fill slot from the product tank and empties one in the drain slot into the Drilling
     * Fluid tank, each going to the output buffer. A container to fill waits while the tank holds too little for it; one
     * to empty, while the tank has no room for all it holds, which they give up whole or not at all; either, while the
     * buffer has no room for it.
     */
    void exchangeContainers() {
        if (fill.getAmountAsInt(0) > 0 && product.getAmountAsInt(0) > 0) {
            ResourceHandler<FluidResource> container = new SlotAccess(fill).oneByOne().getCapability(Capabilities.Fluid.ITEM);
            if (container != null) {
                ResourceHandlerUtil.move(product, container, resource -> true, Integer.MAX_VALUE, null);
            }
        }
        if (drain.getAmountAsInt(0) > 0) {
            ResourceHandler<FluidResource> container = new SlotAccess(drain).oneByOne().getCapability(Capabilities.Fluid.ITEM);
            if (container != null) {
                ResourceHandlerUtil.move(container, drillingFluidTank, resource -> true, Integer.MAX_VALUE, null);
            }
        }
    }

    /** Pushes the product tank's crude into the fluid handler at {@code next}, from {@code side}; the Drilling Fluid stays (ADR 0004). */
    void pushCrude(ServerLevel level, BlockPos next, Direction side) {
        if (ResourceHandlerUtil.isEmpty(product)) {
            return;
        }
        ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, next, side);
        if (fluids != null) {
            ResourceHandlerUtil.move(product, fluids, resource -> true, Integer.MAX_VALUE, null);
        }
    }

    /** An empty container crude can go into: one that holds no fluid and has room for crude. */
    static boolean acceptsCrude(ItemResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        ResourceHandler<FluidResource> container = ItemAccess.forStack(resource.toStack(1)).getCapability(Capabilities.Fluid.ITEM);
        if (container == null || !ResourceHandlerUtil.isEmpty(container)) {
            return false;
        }
        for (int index = 0; index < container.size(); index++) {
            if (container.getCapacityAsLong(index, crudeOil()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** A filled container of a Drilling Fluid: one that holds a fluid in {@code #fieldworks:drilling_fluids}. */
    static boolean holdsDrillingFluid(ItemResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        ResourceHandler<FluidResource> container = ItemAccess.forStack(resource.toStack(1)).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) {
            return false;
        }
        for (int index = 0; index < container.size(); index++) {
            FluidResource held = container.getResource(index);
            if (container.getAmountAsLong(index) > 0 && held.getFluid().defaultFluidState().is(FieldworksTags.DRILLING_FLUIDS)) {
                return true;
            }
        }
        return false;
    }

    private static FluidResource crudeOil() {
        return FluidResource.of(CrudeOil.SOURCE.get());
    }

    void save(ValueOutput output) {
        fill.serialize(output.child(FILL_KEY));
        drain.serialize(output.child(DRAIN_KEY));
        product.serialize(output.child(PRODUCT_KEY));
        drillingFluidTank.serialize(output.child(DRILLING_KEY));
    }

    void load(ValueInput input) {
        fill.deserialize(input.childOrEmpty(FILL_KEY));
        drain.deserialize(input.childOrEmpty(DRAIN_KEY));
        product.deserialize(input.childOrEmpty(PRODUCT_KEY));
        drillingFluidTank.deserialize(input.childOrEmpty(DRILLING_KEY));
    }

    /**
     * A tank of {@link #TANK_CAPACITY}, one kind of fluid at a time of those {@code holds} takes, and its face to pipes.
     * A face that {@code fills} takes fluid in and gives nothing back, the Drilling Fluid tank's, since the Harvester
     * spends it itself; one that does not drains and takes nothing in, the product tank's (ADR 0004).
     */
    private static final class HarvesterTank extends FluidStacksResourceHandler {

        private final Predicate<Fluid> holds;
        private final Runnable changed;
        final ResourceHandler<FluidResource> face;

        HarvesterTank(Predicate<Fluid> holds, boolean fills, Runnable changed) {
            super(1, TANK_CAPACITY);
            this.holds = holds;
            this.changed = changed;
            this.face = new GuardedResourceHandler<>(this) {
                @Override
                public boolean isValid(int index, FluidResource resource) {
                    return fills && super.isValid(index, resource);
                }

                @Override
                public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                    return fills ? super.insert(index, resource, amount, transaction) : 0;
                }

                @Override
                public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                    return fills ? 0 : super.extract(index, resource, amount, transaction);
                }
            };
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return holds.test(resource.getFluid());
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            changed.run();
        }
    }

    /**
     * A container slot as the location of the container being filled or emptied: the container leaves the slot, and
     * the one it is exchanged for goes to the output buffer, never back into the slot.
     */
    private final class SlotAccess implements ItemAccess {

        private final ItemStacksResourceHandler slot;

        SlotAccess(ItemStacksResourceHandler slot) {
            this.slot = slot;
        }

        @Override
        public ItemResource getResource() {
            return slot.getResource(0);
        }

        @Override
        public int getAmount() {
            return slot.getAmountAsInt(0);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return ResourceHandlerUtil.insertStacking(buffer, resource, amount, transaction);
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return slot.extract(0, resource, amount, transaction);
        }
    }
}
