// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.network.HeldRecipeSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester's menu: what a recipe viewer's transfer targets to set the Held recipe (#10), and the Harvester's face
 * to a player (#13). Its slots are the fuel slot, the fill slot, the drain slot and the 3x3 output buffer, then the
 * player's inventory. Power, both tanks, progress and the idle reason ride in data slots, whole ints under NeoForge, as
 * the figures of {@link HarvesterStatus}; the Held recipe crosses as a packet when it changes, since the client has no
 * recipe to read what it makes from. The Held recipe is cleared here, by a button, and never picked: a recipe is set
 * only from EMI or JEI.
 *
 * <p>The fuel slot is in the menu for every tier and hidden, and refuses everything, on a tier that takes no fuel, so
 * a slot's index does not depend on the tier.
 */
public final class HarvesterMenu extends AbstractContainerMenu {

    /** The Harvester's own slot indices: each menu slot stands at the index of the slot it shows. */
    public static final int FUEL_SLOT = HarvesterBlockEntity.FUEL_SLOT;
    public static final int FILL_SLOT = HarvesterBlockEntity.FILL_SLOT;
    public static final int DRAIN_SLOT = HarvesterBlockEntity.DRAIN_SLOT;
    public static final int BUFFER_FIRST = HarvesterBlockEntity.BUFFER_FIRST;
    public static final int FIRST_PLAYER_SLOT = BUFFER_FIRST + HarvesterBlockEntity.BUFFER_SLOTS;

    public static final int FUEL_X = 8;
    public static final int FUEL_Y = 18;
    public static final int DRAIN_X = 8;
    public static final int DRAIN_Y = 36;
    public static final int FILL_X = 8;
    public static final int FILL_Y = 54;
    public static final int BUFFER_X = 116;
    public static final int BUFFER_Y = 18;
    /** Where the player's inventory starts, from the top of the screen: below the bars, the Held recipe and the idle reason. */
    public static final int INVENTORY_Y = 130;

    private static final int DATA_TAKES_FUEL = 0;
    private static final int DATA_TAKES_ENERGY = 1;
    private static final int DATA_BURN_LEFT = 2;
    private static final int DATA_BURN_TOTAL = 3;
    private static final int DATA_ENERGY = 4;
    private static final int DATA_ENERGY_CAPACITY = 5;
    private static final int DATA_PROGRESS = 6;
    private static final int DATA_OPERATION_TICKS = 7;
    /** The idle reason's ordinal plus one; 0 while the Harvester works. */
    private static final int DATA_IDLE = 8;
    /** A tank's fluid is its registry id, then its mB. */
    private static final int DATA_DRILLING_FLUID = 9;
    private static final int DATA_DRILLING_AMOUNT = 10;
    private static final int DATA_PRODUCT_FLUID = 11;
    private static final int DATA_PRODUCT_AMOUNT = 12;
    private static final int DATA_TANK_CAPACITY = 13;
    private static final int DATA_BURN_PER_OPERATION = 14;
    private static final int DATA_COUNT = 15;

    private final BlockPos pos;
    /** The Harvester on the server; null on the client, which reads what the server sends. */
    private final @Nullable HarvesterBlockEntity harvester;
    private final ContainerData data;
    private final Player player;

    /** What the client was last told of the Held recipe; the server reads it live. */
    private Optional<HeldRecipeView> held = Optional.empty();
    /** The Held recipe's id as last sent to the client, or unset before the first send. */
    private @Nullable Optional<Identifier> sent;

    HarvesterMenu(int containerId, Inventory inventory, HarvesterBlockEntity harvester) {
        this(containerId, inventory, harvester.getBlockPos(), harvester, harvester.fuelSlots(), harvester.fillSlots(),
                harvester.drainSlots(), harvester.bufferSlots(), serverData(harvester));
    }

    /** The client's menu, opened at the position the server wrote; its slots and data stand over stubs the sync fills. */
    public HarvesterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), null, new ItemStacksResourceHandler(1), new ItemStacksResourceHandler(1),
                new ItemStacksResourceHandler(1), new ItemStacksResourceHandler(HarvesterBlockEntity.BUFFER_SLOTS),
                new SimpleContainerData(DATA_COUNT));
    }

    private HarvesterMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable HarvesterBlockEntity harvester,
            ItemStacksResourceHandler fuel, ItemStacksResourceHandler fill, ItemStacksResourceHandler drain,
            ItemStacksResourceHandler buffer, ContainerData data) {
        super(Harvesters.MENU.get(), containerId);
        this.pos = pos;
        this.harvester = harvester;
        this.data = data;
        this.player = inventory.player;
        addSlot(new FuelSlot(fuel));
        addSlot(new ContainerSlot(fill, FILL_X, FILL_Y, HarvesterFluids::acceptsCrude));
        addSlot(new ContainerSlot(drain, DRAIN_X, DRAIN_Y, HarvesterFluids::holdsDrillingFluid));
        IndexModifier<ItemResource> modifier = buffer::set;
        for (int index = 0; index < HarvesterBlockEntity.BUFFER_SLOTS; index++) {
            addSlot(new OutputSlot(buffer, modifier, index, BUFFER_X + index % 3 * 18, BUFFER_Y + index / 3 * 18));
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    /** The server's data: the Harvester's status, read live, so the menu cannot show anything it does not report. */
    private static ContainerData serverData(HarvesterBlockEntity harvester) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                HarvesterStatus status = harvester.status();
                return switch (index) {
                    case DATA_TAKES_FUEL -> status.takesFuel() ? 1 : 0;
                    case DATA_TAKES_ENERGY -> status.takesEnergy() ? 1 : 0;
                    case DATA_BURN_LEFT -> status.burnLeft();
                    case DATA_BURN_TOTAL -> status.burnTotal();
                    case DATA_BURN_PER_OPERATION -> status.burnPerOperation();
                    case DATA_ENERGY -> status.energy();
                    case DATA_ENERGY_CAPACITY -> status.energyCapacity();
                    case DATA_PROGRESS -> status.progress();
                    case DATA_OPERATION_TICKS -> status.operationTicks();
                    case DATA_IDLE -> status.idle().map(reason -> reason.ordinal() + 1).orElse(0);
                    case DATA_DRILLING_FLUID -> BuiltInRegistries.FLUID.getId(status.drillingFluid());
                    case DATA_DRILLING_AMOUNT -> status.drillingAmount();
                    case DATA_PRODUCT_FLUID -> BuiltInRegistries.FLUID.getId(status.product());
                    case DATA_PRODUCT_AMOUNT -> status.productAmount();
                    case DATA_TANK_CAPACITY -> status.tankCapacity();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    /** The Harvester's status as the server last told this menu: the figures the screen draws. */
    public HarvesterStatus status() {
        int idle = data.get(DATA_IDLE);
        return new HarvesterStatus(data.get(DATA_TAKES_FUEL) != 0, data.get(DATA_TAKES_ENERGY) != 0, data.get(DATA_BURN_LEFT),
                data.get(DATA_BURN_TOTAL), data.get(DATA_BURN_PER_OPERATION), data.get(DATA_ENERGY), data.get(DATA_ENERGY_CAPACITY), data.get(DATA_PROGRESS),
                data.get(DATA_OPERATION_TICKS),
                idle > 0 && idle <= IdleReason.values().length ? Optional.of(IdleReason.values()[idle - 1]) : Optional.empty(),
                BuiltInRegistries.FLUID.byId(data.get(DATA_DRILLING_FLUID)), data.get(DATA_DRILLING_AMOUNT),
                BuiltInRegistries.FLUID.byId(data.get(DATA_PRODUCT_FLUID)), data.get(DATA_PRODUCT_AMOUNT),
                data.get(DATA_TANK_CAPACITY));
    }

    public boolean takesFuel() {
        return status().takesFuel();
    }

    public boolean takesEnergy() {
        return status().takesEnergy();
    }

    public int energy() {
        return status().energy();
    }

    public int energyCapacity() {
        return status().energyCapacity();
    }

    public int burnLeft() {
        return status().burnLeft();
    }

    public int burnTotal() {
        return status().burnTotal();
    }

    public int progress() {
        return status().progress();
    }

    public int operationTicks() {
        return status().operationTicks();
    }

    public float progressFraction() {
        return status().progressFraction();
    }

    public FluidStack drillingFluid() {
        return status().drillingFluidStack();
    }

    public FluidStack product() {
        return status().productStack();
    }

    public int tankCapacity() {
        return status().tankCapacity();
    }

    /** Why the Harvester did no work on its last tick, the choice module's reason; empty while it works. */
    public Optional<IdleReason> idleReason() {
        return status().idle();
    }

    /** The Held recipe: the server's own, read live, and on the client as it was last sent. */
    public Optional<HeldRecipeView> held() {
        if (harvester != null && harvester.getLevel() instanceof ServerLevel level) {
            return harvester.heldView(level);
        }
        return held;
    }

    /** The client taking the Held recipe the server sent. */
    public void show(Optional<HeldRecipeView> held) {
        this.held = held;
    }

    /** Sends the client the Held recipe whenever it is not the one it was last told. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (harvester != null && player instanceof ServerPlayer server && harvester.getLevel() instanceof ServerLevel level) {
            Optional<Identifier> now = harvester.heldRecipe();
            if (!Objects.equals(sent, now)) {
                sent = now;
                HeldRecipeSyncPacket.send(server, new HeldRecipeSyncPacket(containerId, harvester.heldView(level)));
            }
        }
    }

    /**
     * Holds {@code recipe} on this Harvester if it is a Harvest Recipe, and says whether it did. A Default recipe has
     * no id the recipe manager knows, so it is never held: with no Held recipe the Default recipes run anyway.
     */
    public boolean hold(ServerPlayer player, Identifier recipe) {
        if (harvester == null || harvester.isRemoved() || HarvestRecipes.find(player.level(), recipe).isEmpty()) {
            return false;
        }
        harvester.setHeldRecipe(recipe);
        return true;
    }

    /** Drops the Held recipe, returning the Harvester to its Default recipes, and says whether there was a Harvester to. */
    public boolean clearHeld() {
        if (harvester == null || harvester.isRemoved()) {
            return false;
        }
        harvester.clearHeldRecipe();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < FIRST_PLAYER_SLOT) {
            if (!moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, FUEL_SLOT, BUFFER_FIRST, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (harvester != null && harvester.isRemoved()) {
            return false;
        }
        return player.isWithinBlockInteractionRange(pos, 4.0);
    }

    /**
     * The fuel slot: anything with burn time on a tier that takes fuel, asked the same on both sides, and nothing, and
     * hidden, on one that does not.
     */
    private final class FuelSlot extends ResourceHandlerSlot {

        FuelSlot(ItemStacksResourceHandler handler) {
            super(handler, handler::set, 0, FUEL_X, FUEL_Y);
        }

        @Override
        public boolean isActive() {
            return data.get(DATA_TAKES_FUEL) != 0;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && !stack.isEmpty() && stack.getBurnTime(null, player.level().fuelValues()) > 0;
        }
    }

    /** The fill and drain slots: the containers the Harvester's item capability would take there. */
    private static final class ContainerSlot extends ResourceHandlerSlot {

        private final Predicate<ItemResource> accepts;

        ContainerSlot(ItemStacksResourceHandler handler, int x, int y, Predicate<ItemResource> accepts) {
            super(handler, handler::set, 0, x, y);
            this.accepts = accepts;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && accepts.test(ItemResource.of(stack));
        }
    }

    /** An output buffer slot takes nothing from the player; what the Harvester drew is taken from it. */
    private static final class OutputSlot extends ResourceHandlerSlot {

        OutputSlot(ItemStacksResourceHandler handler, IndexModifier<ItemResource> modifier, int index, int x, int y) {
            super(handler, modifier, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
