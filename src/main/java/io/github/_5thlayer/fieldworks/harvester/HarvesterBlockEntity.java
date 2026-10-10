// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.oil.OilWellBlock;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.libworks.transfer.GuardedResourceHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester at work: each tick it fills the container in its fill slot from the product tank, pushes its
 * output buffer and its crude into the item and fluid handlers at its output face, then, with room for the
 * result and a tick of power, puts a tick into the operation the choice module names. One burn tick, or one
 * tick's FE, buys one tick of work. A full buffer or product tank stops it; nothing is dropped in the world.
 */
public final class HarvesterBlockEntity extends BlockEntity {

    /** The output buffer's slots. */
    static final int BUFFER_SLOTS = 9;

    /** A tank's capacity in mB: one fixed figure, not tier data (#1). */
    static final int TANK_CAPACITY = 8_000;

    private static final String FUEL_KEY = "fuel";
    private static final String FILL_KEY = "fill";
    private static final String BUFFER_KEY = "buffer";
    private static final String PRODUCT_KEY = "product";
    private static final String ENERGY_KEY = "energy";
    private static final String BURN_KEY = "burn_left";
    private static final String PROGRESS_KEY = "progress";
    private static final String CURSOR_KEY = "cursor";
    private static final String HELD_KEY = "held_recipe";

    private final ItemStacksResourceHandler fuel = new Slots(1);
    private final ItemStacksResourceHandler fill = new Slots(1);
    private final ItemStacksResourceHandler buffer = new Slots(BUFFER_SLOTS);
    private final ResourceHandler<ItemResource> face = new Face(new CombinedResourceHandler<>(fuel, fill, buffer));
    private final FluidStacksResourceHandler product = new ProductTank();
    private final ResourceHandler<FluidResource> fluidFace = new FluidFace(product);
    private final EnergyBuffer energy = new EnergyBuffer();

    /** Whether the energy capability was last seen exposed, so a reload that re-powers the tier invalidates it. */
    private boolean exposesEnergy;

    /** Burn ticks bought by the last fuel item and not yet worked. */
    private int burnLeft;
    private int progress;
    private int cursor;

    /** The Held recipe's id, resolved on each tick: a block entity loads before the recipes do. */
    private @Nullable Identifier held;

    public HarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(Harvesters.BLOCK_ENTITY.get(), pos, state);
    }

    /** The item capability on every face of the footprint: routed by content, not by face (ADR 0004). */
    ResourceHandler<ItemResource> itemFace() {
        return face;
    }

    /** The fluid capability on every face of the footprint: the product tank drains and takes nothing. */
    ResourceHandler<FluidResource> fluidFace() {
        return fluidFace;
    }

    /**
     * The energy capability on every face of the footprint, sized by the tier's FE buffer: it takes FE and gives
     * none back. A tier that takes no FE exposes none (ADR 0005), and neither does one whose file is missing.
     */
    @Nullable EnergyHandler energyFace() {
        HarvesterTier tier = tier();
        if (tier == null || !tier.power().fe()) {
            return null;
        }
        energy.resize(tier.feBuffer());
        return energy.face;
    }

    private TierBlock tierBlock() {
        return ((HarvesterBlock) getBlockState().getBlock()).tier();
    }

    private @Nullable HarvesterTier tier() {
        return HarvesterTiers.get(tierBlock());
    }

    public void serverTick(ServerLevel level) {
        fillContainer();
        pushOutput(level);
        HarvesterTier tier = tier();
        exposeEnergy(level, tier != null && tier.power().fe());
        if (tier == null) {
            return;
        }
        HarvestRecipes.Loaded recipes = HarvestRecipes.loaded(level);
        Optional<OperationChoice.Recipe> heldChoice = Optional.empty();
        if (held != null) {
            HarvestRecipes.Entry entry = recipes.byId().get(held.toString());
            if (entry == null) {
                // A Held recipe a reload removed runs nothing; it never falls back to the Default recipes.
                return;
            }
            heldChoice = Optional.of(entry.choice());
        }
        List<BlockPos> layer = layer();
        if (!(OperationChoice.choose(sources(level, layer), cursor, recipes.choices(), heldChoice, tier.rank()) instanceof Operation operation)) {
            return;
        }
        BlockPos at = layer.get(operation.position());
        Runnable work = level.getBlockEntity(at) instanceof OilWellBlockEntity well ? pump(well) : draw(level, at, operation, recipes);
        if (work == null || !payTick(level, tier)) {
            return;
        }
        progress++;
        if (progress >= tier.ticks(operation.ticks())) {
            progress = 0;
            cursor = operation.nextCursor();
            work.run();
        }
        setChanged();
    }

    /**
     * One draw of the ore block at {@code at}, paying the operation's recipe result or the block's own item,
     * or null while the buffer has no room for it.
     */
    private @Nullable Runnable draw(ServerLevel level, BlockPos at, Operation operation, HarvestRecipes.Loaded recipes) {
        OreBlock ore = (OreBlock) level.getBlockState(at).getBlock();
        ItemStack stack = operation.recipe()
                .map(recipe -> recipes.byId().get(recipe.id()).recipe().result().create())
                .orElseGet(() -> drop(ore));
        ItemResource result = ItemResource.of(stack);
        if (!fits(result, stack.getCount())) {
            return null;
        }
        return () -> {
            if (OreMining.draw(level, ore, at).paid() > 0 && !result.isEmpty()) {
                ResourceHandlerUtil.insertStacking(buffer, result, stack.getCount(), null);
            }
        };
    }

    /** One cycle of the well's yield and decay (ADR 0002), or null while the product tank has no room for it. */
    private @Nullable Runnable pump(OilWellBlockEntity well) {
        if (well.nextCycle() > TANK_CAPACITY - product.getAmountAsInt(0)) {
            return null;
        }
        return () -> {
            int crude = well.cycle();
            if (crude > 0) {
                try (Transaction pumping = Transaction.openRoot()) {
                    product.insert(0, crudeOil(), crude, pumping);
                    pumping.commit();
                }
            }
        };
    }

    /**
     * Sets the Harvest Recipe this Harvester works, or none: with one it works only the sources the recipe
     * matches. Progress on the last operation is lost.
     */
    public void setHeldRecipe(@Nullable Identifier recipe) {
        held = recipe;
        progress = 0;
        setChanged();
    }

    /** The layer under the footprint, in the fixed order the cursor counts in, whatever way the Harvester faces. */
    private List<BlockPos> layer() {
        List<BlockPos> layer = new ArrayList<>(9);
        BlockPos centre = worldPosition.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                layer.add(centre.offset(dx, 0, dz));
            }
        }
        return layer;
    }

    private static List<Source> sources(ServerLevel level, List<BlockPos> layer) {
        List<Source> sources = new ArrayList<>(layer.size());
        for (BlockPos pos : layer) {
            Block block = level.getBlockState(pos).getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            sources.add(block instanceof OilWellBlock ? Source.well(id)
                    : block instanceof OreBlock ? Source.oreBlock(id, tagsOf(block))
                    : Source.NONE);
        }
        return sources;
    }

    private static Set<String> tagsOf(Block block) {
        return block.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).collect(Collectors.toUnmodifiableSet());
    }

    private static ItemStack drop(OreBlock ore) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(ore.resource().drop())));
    }

    private static FluidResource crudeOil() {
        return FluidResource.of(CrudeOil.SOURCE.get());
    }

    private boolean fits(ItemResource result, int amount) {
        if (result.isEmpty()) {
            return true;
        }
        try (Transaction simulated = Transaction.openRoot()) {
            return ResourceHandlerUtil.insertStacking(buffer, result, amount, simulated) == amount;
        }
    }

    /** Pays for one tick of work as the tier's power rule says (#7): a burn tick, a tick's FE, or a fuel item lit. */
    private boolean payTick(ServerLevel level, HarvesterTier tier) {
        HarvesterTier.Power power = tier.power();
        if (power.fe()) {
            energy.resize(tier.feBuffer());
        }
        return switch (power.payment(burnLeft, energy.getAmountAsInt())) {
            case BURN -> {
                burnLeft--;
                yield true;
            }
            case FE -> {
                try (Transaction paying = Transaction.openRoot()) {
                    energy.extract(power.fePerTick(), paying);
                    paying.commit();
                }
                yield true;
            }
            case LIGHT -> {
                if (!light(level)) {
                    yield false;
                }
                burnLeft--;
                yield true;
            }
            case NONE -> false;
        };
    }

    /**
     * Lights the fuel item in the fuel slot. A fuel item's remainder, the bucket of a lava bucket, goes to the
     * output buffer, so a fuel item whose remainder has no room there is not lit.
     */
    private boolean light(ServerLevel level) {
        ItemResource item = fuel.getResource(0);
        int burnTime = item.isEmpty() ? 0 : item.toStack(1).getBurnTime(null, level.fuelValues());
        if (burnTime == 0) {
            return false;
        }
        try (Transaction lighting = Transaction.openRoot()) {
            fuel.extract(0, item, 1, lighting);
            ItemStackTemplate remainder = item.toStack(1).getCraftingRemainder();
            if (remainder != null && ResourceHandlerUtil.insertStacking(buffer,
                    ItemResource.of(remainder.create()), 1, lighting) != 1) {
                return false;
            }
            lighting.commit();
        }
        burnLeft = burnTime;
        return true;
    }

    /**
     * Invalidates the energy capability at every block of the footprint when the tier's power changes whether it
     * is exposed: NeoForge drops cached lookups only when it is told to.
     */
    private void exposeEnergy(ServerLevel level, boolean exposed) {
        if (exposed == exposesEnergy) {
            return;
        }
        exposesEnergy = exposed;
        Direction facing = getBlockState().getValue(HarvesterBlock.FACING);
        for (BlockPos pos : Harvesters.footprint(tierBlock()).positions(worldPosition, facing)) {
            level.invalidateCapabilities(pos);
        }
    }

    /**
     * Fills one container in the fill slot from the product tank, and sends it full to the output buffer.
     * A container waits while the tank holds too little for it or the buffer has no room.
     */
    private void fillContainer() {
        if (fill.getAmountAsInt(0) == 0 || product.getAmountAsInt(0) == 0) {
            return;
        }
        ResourceHandler<FluidResource> container = new FillSlotAccess().oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (container != null) {
            ResourceHandlerUtil.move(product, container, resource -> true, Integer.MAX_VALUE, null);
        }
    }

    /**
     * Pushes the buffer and the product tank out of the output face, the way the Harvester faces, into the
     * block one past the footprint's edge at the face's bottom centre. Some pipes pull from nothing, so crude
     * is pushed as items are (ADR 0004); nothing is pulled back.
     */
    private void pushOutput(ServerLevel level) {
        Direction out = getBlockState().getValue(HarvesterBlock.FACING);
        BlockPos next = worldPosition.relative(out, 2);
        if (!ResourceHandlerUtil.isEmpty(buffer)) {
            ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, next, out.getOpposite());
            if (items != null) {
                ResourceHandlerUtil.moveStacking(buffer, items, resource -> true, Integer.MAX_VALUE, null);
            }
        }
        if (!ResourceHandlerUtil.isEmpty(product)) {
            ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, next, out.getOpposite());
            if (fluids != null) {
                ResourceHandlerUtil.move(product, fluids, resource -> true, Integer.MAX_VALUE, null);
            }
        }
    }

    /** An empty container crude can go into: one that holds no fluid and has room for crude. */
    private static boolean holdsCrude(ItemResource resource) {
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fuel.serialize(output.child(FUEL_KEY));
        fill.serialize(output.child(FILL_KEY));
        buffer.serialize(output.child(BUFFER_KEY));
        product.serialize(output.child(PRODUCT_KEY));
        energy.serialize(output.child(ENERGY_KEY));
        output.putInt(BURN_KEY, burnLeft);
        output.putInt(PROGRESS_KEY, progress);
        output.putInt(CURSOR_KEY, cursor);
        if (held != null) {
            output.store(HELD_KEY, Identifier.CODEC, held);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fuel.deserialize(input.childOrEmpty(FUEL_KEY));
        fill.deserialize(input.childOrEmpty(FILL_KEY));
        buffer.deserialize(input.childOrEmpty(BUFFER_KEY));
        product.deserialize(input.childOrEmpty(PRODUCT_KEY));
        energy.deserialize(input.childOrEmpty(ENERGY_KEY));
        burnLeft = input.getIntOr(BURN_KEY, 0);
        progress = input.getIntOr(PROGRESS_KEY, 0);
        cursor = input.getIntOr(CURSOR_KEY, 0);
        held = input.read(HELD_KEY, Identifier.CODEC).orElse(null);
    }

    /** A broken Harvester drops what it holds, as a furnace does. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (ItemStacksResourceHandler slots : List.of(fuel, fill, buffer)) {
                for (ItemStack stack : slots.copyToList()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
    }

    /** Slots that mark the block entity changed when an insert or extract commits. */
    private final class Slots extends ItemStacksResourceHandler {

        Slots(int size) {
            super(size);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    /**
     * The FE buffer, sized by the tier on each use since a reload can change it. It is drawn only by the
     * Harvester's own work; {@link #face} takes FE in and gives none out.
     */
    private final class EnergyBuffer extends SimpleEnergyHandler {

        final EnergyHandler face = new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return EnergyBuffer.this.getAmountAsLong();
            }

            @Override
            public long getCapacityAsLong() {
                return EnergyBuffer.this.getCapacityAsLong();
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                return EnergyBuffer.this.insert(amount, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return 0;
            }
        };

        EnergyBuffer() {
            super(0);
        }

        void resize(int capacity) {
            this.capacity = capacity;
            this.maxInsert = capacity;
            this.maxExtract = capacity;
            this.energy = Math.min(energy, capacity);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    }

    /** The product tank: crude only. */
    private final class ProductTank extends FluidStacksResourceHandler {

        ProductTank() {
            super(1, TANK_CAPACITY);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.getFluid() == CrudeOil.SOURCE.get();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    }

    /**
     * Routes items by content (ADR 0004): the fuel slot, index 0, takes anything with burn time while the tier
     * burns fuel, and nothing on a tier that takes none (ADR 0005), and the fill
     * slot, index 1, empty containers crude can go into; neither gives anything back. The output buffer takes
     * nothing from outside and gives everything. Buffer slots report no room, so a hopper reads the
     * Harvester full once the slot its item goes to is.
     */
    private final class Face extends GuardedResourceHandler<ItemResource> {

        private static final int FUEL_SLOT = 0;
        private static final int FILL_SLOT = 1;

        Face(ResourceHandler<ItemResource> slots) {
            super(slots);
        }

        private boolean isInputSlot(int index) {
            return index == FUEL_SLOT || index == FILL_SLOT;
        }

        private boolean burns(ItemResource resource) {
            return level != null && resource.toStack(1).getBurnTime(null, level.fuelValues()) > 0;
        }

        private boolean takesFuel() {
            HarvesterTier tier = tier();
            return tier != null && tier.power().fuel();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return switch (index) {
                case FUEL_SLOT -> takesFuel() && burns(resource);
                case FILL_SLOT -> holdsCrude(resource);
                default -> false;
            };
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            if (!isInputSlot(index) || index == FUEL_SLOT && !takesFuel() || !resource.isEmpty() && !isValid(index, resource)) {
                return 0;
            }
            return super.getCapacityAsLong(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isValid(index, resource) ? super.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isInputSlot(index) ? 0 : super.extract(index, resource, amount, transaction);
        }
    }

    /** The product tank as a pipe sees it: it drains, and takes nothing in. */
    private static final class FluidFace extends GuardedResourceHandler<FluidResource> {

        FluidFace(ResourceHandler<FluidResource> tanks) {
            super(tanks);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return false;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }

    /**
     * The fill slot as the location of the container being filled: the container leaves the slot, and the
     * filled one it is exchanged for goes to the output buffer, never back into the slot.
     */
    private final class FillSlotAccess implements ItemAccess {

        @Override
        public ItemResource getResource() {
            return fill.getResource(0);
        }

        @Override
        public int getAmount() {
            return fill.getAmountAsInt(0);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return ResourceHandlerUtil.insertStacking(buffer, resource, amount, transaction);
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return fill.extract(0, resource, amount, transaction);
        }
    }
}
