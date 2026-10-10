// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Choice;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.DrillingFluid;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Idle;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Machine;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Tank;
import io.github._5thlayer.fieldworks.oil.OilWellBlock;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.libworks.transfer.GuardedResourceHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
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
 * A Harvester at work: each tick it fills the container in its fill slot from the product tank, empties the one in
 * its drain slot into the Drilling Fluid tank, pushes its
 * output buffer and its crude into the item and fluid handlers at its output face, then, with room for the
 * result and a tick of power, puts a tick into the operation the choice module names. One burn tick, or one
 * tick's FE, buys one tick of work. A full buffer or product tank stops it; nothing is dropped in the world.
 */
public final class HarvesterBlockEntity extends BlockEntity implements MenuProvider {

    /** The output buffer's slots. */
    static final int BUFFER_SLOTS = 9;

    /** A tank's capacity in mB: one fixed figure, not tier data (#1). */
    static final int TANK_CAPACITY = 8_000;

    /** The blocks, besides ore blocks, that a Harvester breaks for their loot; a pack sets them by tag. */
    static final TagKey<Block> HARVESTABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvestable"));

    /** The fluids a Harvest Recipe may consume, and so the only ones the Drilling Fluid tank takes (#1). */
    static final TagKey<Fluid> DRILLING_FLUIDS = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "drilling_fluids"));

    private static final String FUEL_KEY = "fuel";
    private static final String FILL_KEY = "fill";
    private static final String DRAIN_KEY = "drain";
    private static final String BUFFER_KEY = "buffer";
    private static final String PRODUCT_KEY = "product";
    private static final String DRILLING_KEY = "drilling_fluid";
    private static final String ENERGY_KEY = "energy";
    private static final String BURN_KEY = "burn_left";
    private static final String BURN_TOTAL_KEY = "burn_total";
    private static final String PROGRESS_KEY = "progress";
    private static final String CURSOR_KEY = "cursor";
    private static final String HELD_KEY = "held_recipe";

    private final ItemStacksResourceHandler fuel = new Slots(1);
    private final ItemStacksResourceHandler fill = new Slots(1);
    private final ItemStacksResourceHandler drain = new Slots(1);
    private final ItemStacksResourceHandler buffer = new Slots(BUFFER_SLOTS);
    private final ResourceHandler<ItemResource> face = new Face(new CombinedResourceHandler<>(fuel, fill, drain, buffer));
    private final FluidStacksResourceHandler product = new ProductTank();
    private final FluidStacksResourceHandler drilling = new DrillingTank();
    private final ResourceHandler<FluidResource> fluidFace =
            new CombinedResourceHandler<>(new FluidFace(drilling, true), new FluidFace(product, false));
    private final EnergyBuffer energy = new EnergyBuffer();

    /** Whether the energy capability was last seen exposed, so a reload that re-powers the tier invalidates it. */
    private boolean exposesEnergy;

    /** Burn ticks bought by the last fuel item and not yet worked. */
    private int burnLeft;
    /** The burn the last fuel item bought, so the menu can draw {@link #burnLeft} against it (#13). */
    private int burnTotal;
    private int progress;
    /** Ticks the operation under way takes on this tier, for the menu's progress bar; not saved, set as it works (#13). */
    private int operationTicks;
    private int cursor;

    /** The Held recipe's id, resolved on each tick: a block entity loads before the recipes do. */
    private @Nullable Identifier held;
    /** Why the last tick did no work, or null if it worked; read again every tick, so never saved. */
    private @Nullable IdleReason idle;

    public HarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(Harvesters.BLOCK_ENTITY.get(), pos, state);
    }

    /** The item capability on every face of the footprint: routed by content, not by face (ADR 0004). */
    ResourceHandler<ItemResource> itemFace() {
        return face;
    }

    /**
     * The fluid capability on every face of the footprint: the Drilling Fluid tank takes a Drilling Fluid and gives
     * nothing back, and the product tank drains and takes nothing.
     */
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
        drainContainer();
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
                // A Held recipe a reload removed matches nothing; it never falls back to the Default recipes (#10).
                idle = IdleReason.NOTHING_MATCHING;
                return;
            }
            heldChoice = Optional.of(entry.choice());
        }
        List<BlockPos> layer = layer();
        Machine machine = new Machine(tier.rank(), drillingFluidTank(), operation -> work(level, layer, operation, recipes) != null, canPay(level, tier));
        Choice choice = OperationChoice.choose(sources(level, layer), cursor, recipes.choices(), heldChoice, machine);
        if (!(choice instanceof Operation operation)) {
            idle = ((Idle) choice).reason();
            return;
        }
        Runnable work = work(level, layer, operation, recipes);
        if (work == null || !payTick(level, tier)) {
            return;
        }
        idle = null;
        progress++;
        operationTicks = tier.ticks(operation.ticks());
        if (progress >= operationTicks) {
            progress = 0;
            cursor = operation.nextCursor();
            // Taken on completion, as the result is paid; only a Held recipe names a fluid, so a Default recipe takes none (#1).
            if (takeDrillingFluid(operation.drillingFluid())) {
                work.run();
            }
        }
        setChanged();
    }

    /** The operation's work, or null while there is no room for what it yields. */
    private @Nullable Runnable work(ServerLevel level, List<BlockPos> layer, Operation operation, HarvestRecipes.Loaded recipes) {
        BlockPos at = layer.get(operation.position());
        return level.getBlockEntity(at) instanceof OilWellBlockEntity well ? pump(well, operation, recipes)
                : level.getBlockState(at).getBlock() instanceof OreBlock ? draw(level, at, operation, recipes)
                : breakBlock(level, at, operation, recipes);
    }

    /**
     * One draw of the ore block at {@code at}, paying the operation's recipe result or the block's own item,
     * or null while the buffer has no room for it.
     */
    private @Nullable Runnable draw(ServerLevel level, BlockPos at, Operation operation, HarvestRecipes.Loaded recipes) {
        OreBlock ore = (OreBlock) level.getBlockState(at).getBlock();
        ItemStack stack = operation.recipe()
                .map(recipe -> recipes.byId().get(recipe.id()).recipe().result().stack())
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

    /**
     * The harvestable block at {@code at} broken to air, its loot, or the operation's recipe result in its place,
     * going to the buffer; or null while the buffer has no room for all of it. The loot table runs against an
     * unenchanted pick and nothing checks the tool, so a diamond ore breaks for its diamond (#1).
     */
    private @Nullable Runnable breakBlock(ServerLevel level, BlockPos at, Operation operation, HarvestRecipes.Loaded recipes) {
        BlockState state = level.getBlockState(at);
        List<ItemStack> loot = operation.recipe()
                .map(recipe -> List.of(recipes.byId().get(recipe.id()).recipe().result().stack()))
                .orElseGet(() -> Block.getDrops(state, level, at, level.getBlockEntity(at), null, new ItemStack(Items.DIAMOND_PICKAXE)));
        if (!fitsAll(loot)) {
            return null;
        }
        return () -> {
            // Not removeBlock or destroyBlock: a waterlogged block would leave water, and the spec says air.
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (ItemStack stack : loot) {
                if (!stack.isEmpty()) {
                    ResourceHandlerUtil.insertStacking(buffer, ItemResource.of(stack), stack.getCount(), null);
                }
            }
        };
    }

    /**
     * One cycle of the well's yield and decay (ADR 0002), or null while the product tank has no room for it. A recipe
     * with a crude result pumps that many mB in place of the cycle's yield; the cycle still takes its step off the
     * well, so a well above its floor goes on decaying (#12). A recipe whose result is an item, which only a block tag
     * naming the well could give it, pumps the cycle's own yield.
     */
    private @Nullable Runnable pump(OilWellBlockEntity well, Operation operation, HarvestRecipes.Loaded recipes) {
        int recipeCrude = operation.recipe().map(recipe -> recipes.byId().get(recipe.id()).recipe().result().crude()).orElse(0);
        if ((recipeCrude > 0 ? recipeCrude : well.nextCycle()) > TANK_CAPACITY - product.getAmountAsInt(0)) {
            return null;
        }
        return () -> {
            int cycled = well.cycle();
            int crude = recipeCrude > 0 ? recipeCrude : cycled;
            if (crude > 0) {
                try (Transaction pumping = Transaction.openRoot()) {
                    product.insert(0, crudeOil(), crude, pumping);
                    pumping.commit();
                }
            }
        };
    }

    /** What the Drilling Fluid tank holds, as the choice reads it. */
    private Tank drillingFluidTank() {
        FluidResource held = drilling.getResource(0);
        return held.isEmpty() ? Tank.EMPTY : new Tank(BuiltInRegistries.FLUID.getKey(held.getFluid()).toString(), drilling.getAmountAsInt(0));
    }

    /** Takes one operation's Drilling Fluid from the tank, or says it is not there; none to take is taken. */
    private boolean takeDrillingFluid(Optional<DrillingFluid> need) {
        if (need.isEmpty()) {
            return true;
        }
        FluidResource held = drilling.getResource(0);
        if (held.isEmpty() || !need.get().fluids().contains(BuiltInRegistries.FLUID.getKey(held.getFluid()).toString())) {
            return false;
        }
        try (Transaction taking = Transaction.openRoot()) {
            if (drilling.extract(0, held, need.get().amount(), taking) != need.get().amount()) {
                return false;
            }
            taking.commit();
            return true;
        }
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

    /** The Held recipe's id, or empty with none held. */
    public Optional<Identifier> heldRecipe() {
        return Optional.ofNullable(held);
    }

    /** Why the Harvester did no work on its last tick, or empty if it worked (#13 shows it). */
    public Optional<IdleReason> idleReason() {
        return Optional.ofNullable(idle);
    }

    /**
     * What the menu and Jade show of this Harvester (#13): power only for the kinds the tier declares, both tanks,
     * progress, and the idle reason the choice last returned.
     */
    public HarvesterStatus status() {
        HarvesterTier tier = tier();
        boolean takesFuel = tier != null && tier.power().fuel();
        boolean takesEnergy = tier != null && tier.power().fe();
        if (takesEnergy) {
            energy.resize(tier.feBuffer());
        }
        FluidResource mud = drilling.getResource(0);
        FluidResource crude = product.getResource(0);
        return new HarvesterStatus(takesFuel, takesEnergy, takesFuel ? burnLeft : 0, takesFuel ? burnTotal : 0,
                takesEnergy ? energy.getAmountAsInt() : 0, takesEnergy ? tier.feBuffer() : 0, progress, operationTicks,
                idleReason(), mud.getFluid(), drilling.getAmountAsInt(0), crude.getFluid(), product.getAmountAsInt(0), TANK_CAPACITY);
    }

    /** The Held recipe with what it makes, for a menu or tooltip to name; empty with none held. */
    public Optional<HeldRecipeView> heldView(ServerLevel level) {
        return heldRecipe().map(id -> new HeldRecipeView(id, HarvestRecipes.find(level, id)
                .map(recipe -> recipe.result().stack()).orElse(ItemStack.EMPTY)));
    }

    /** Drops the Held recipe, which returns the Harvester to its Default recipes (#13). */
    public void clearHeldRecipe() {
        setHeldRecipe(null);
    }

    /** The raw slots a menu stands over, ahead of the item capability's rules (#13). */
    ItemStacksResourceHandler fuelSlots() {
        return fuel;
    }

    ItemStacksResourceHandler fillSlots() {
        return fill;
    }

    ItemStacksResourceHandler drainSlots() {
        return drain;
    }

    ItemStacksResourceHandler bufferSlots() {
        return buffer;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HarvesterMenu(containerId, inventory, this);
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
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            sources.add(block instanceof OilWellBlock ? level.getBlockEntity(pos) instanceof OilWellBlockEntity well && well.atFloor()
                            ? Source.flooredWell(id) : Source.well(id)
                    : block instanceof OreBlock ? Source.oreBlock(id, tagsOf(block))
                    // After the ore blocks: they are in c:ores, so in this tag, and are drawn, never broken (#1).
                    : state.is(HARVESTABLE) ? Source.harvestable(id, tagsOf(block))
                    : Source.NONE);
        }
        return sources;
    }

    static Set<String> tagsOf(Block block) {
        return block.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).collect(Collectors.toUnmodifiableSet());
    }

    static ItemStack drop(OreBlock ore) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(ore.resource().drop())));
    }

    private static FluidResource crudeOil() {
        return FluidResource.of(CrudeOil.SOURCE.get());
    }

    /** Whether the buffer has room for every stack at once. */
    private boolean fitsAll(List<ItemStack> stacks) {
        try (Transaction simulated = Transaction.openRoot()) {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty() && ResourceHandlerUtil.insertStacking(buffer, ItemResource.of(stack), stack.getCount(), simulated) != stack.getCount()) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean fits(ItemResource result, int amount) {
        if (result.isEmpty()) {
            return true;
        }
        try (Transaction simulated = Transaction.openRoot()) {
            return ResourceHandlerUtil.insertStacking(buffer, result, amount, simulated) == amount;
        }
    }

    /** Whether the tier's power rule could pay for a tick of work now, without paying it. */
    private boolean canPay(ServerLevel level, HarvesterTier tier) {
        HarvesterTier.Power power = tier.power();
        if (power.fe()) {
            energy.resize(tier.feBuffer());
        }
        return switch (power.payment(burnLeft, energy.getAmountAsInt())) {
            case BURN, FE -> true;
            case LIGHT -> light(level, false);
            case NONE -> false;
        };
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
                if (!light(level, true)) {
                    yield false;
                }
                burnLeft--;
                yield true;
            }
            case NONE -> false;
        };
    }

    /**
     * Lights the fuel item in the fuel slot, when {@code commit}, or says whether it would light. A fuel item's
     * remainder, the bucket of a lava bucket, goes to the output buffer, so a fuel item whose remainder has no room
     * there is not lit.
     */
    private boolean light(ServerLevel level, boolean commit) {
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
            if (!commit) {
                return true;
            }
            lighting.commit();
        }
        burnLeft = burnTime;
        burnTotal = burnTime;
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
        ResourceHandler<FluidResource> container = new SlotAccess(fill).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (container != null) {
            ResourceHandlerUtil.move(product, container, resource -> true, Integer.MAX_VALUE, null);
        }
    }

    /**
     * Empties one container in the drain slot into the Drilling Fluid tank, and sends it empty to the output buffer.
     * A container waits while the tank has no room for all it holds, which they give up whole or not at all, or the
     * buffer has no room for the emptied one.
     */
    private void drainContainer() {
        if (drain.getAmountAsInt(0) == 0) {
            return;
        }
        ResourceHandler<FluidResource> container = new SlotAccess(drain).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (container != null) {
            ResourceHandlerUtil.move(container, drilling, resource -> true, Integer.MAX_VALUE, null);
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
    static boolean holdsCrude(ItemResource resource) {
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
            if (container.getAmountAsLong(index) > 0 && held.getFluid().defaultFluidState().is(DRILLING_FLUIDS)) {
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
        drain.serialize(output.child(DRAIN_KEY));
        buffer.serialize(output.child(BUFFER_KEY));
        product.serialize(output.child(PRODUCT_KEY));
        drilling.serialize(output.child(DRILLING_KEY));
        energy.serialize(output.child(ENERGY_KEY));
        output.putInt(BURN_KEY, burnLeft);
        output.putInt(BURN_TOTAL_KEY, burnTotal);
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
        drain.deserialize(input.childOrEmpty(DRAIN_KEY));
        buffer.deserialize(input.childOrEmpty(BUFFER_KEY));
        product.deserialize(input.childOrEmpty(PRODUCT_KEY));
        drilling.deserialize(input.childOrEmpty(DRILLING_KEY));
        energy.deserialize(input.childOrEmpty(ENERGY_KEY));
        burnLeft = input.getIntOr(BURN_KEY, 0);
        burnTotal = input.getIntOr(BURN_TOTAL_KEY, 0);
        progress = input.getIntOr(PROGRESS_KEY, 0);
        cursor = input.getIntOr(CURSOR_KEY, 0);
        held = input.read(HELD_KEY, Identifier.CODEC).orElse(null);
    }

    /** A broken Harvester drops what it holds, as a furnace does. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (ItemStacksResourceHandler slots : List.of(fuel, fill, drain, buffer)) {
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

    /** The Drilling Fluid tank: any fluid in {@code #fieldworks:drilling_fluids}, one kind at a time. */
    private final class DrillingTank extends FluidStacksResourceHandler {

        DrillingTank() {
            super(1, TANK_CAPACITY);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.getFluid().defaultFluidState().is(DRILLING_FLUIDS);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    }

    /**
     * Routes items by content (ADR 0004): the fuel slot, index 0, takes anything with burn time while the tier
     * burns fuel, and nothing on a tier that takes none (ADR 0005), the fill slot, index 1, empty containers
     * crude can go into, and the drain slot, index 2, filled containers of a Drilling Fluid; none gives anything
     * back. The output buffer takes nothing from outside and gives everything. Buffer slots report no room, so a hopper reads the
     * Harvester full once the slot its item goes to is.
     */
    private final class Face extends GuardedResourceHandler<ItemResource> {

        private static final int FUEL_SLOT = 0;
        private static final int FILL_SLOT = 1;
        private static final int DRAIN_SLOT = 2;

        Face(ResourceHandler<ItemResource> slots) {
            super(slots);
        }

        private boolean isInputSlot(int index) {
            return index == FUEL_SLOT || index == FILL_SLOT || index == DRAIN_SLOT;
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
                case DRAIN_SLOT -> holdsDrillingFluid(resource);
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

    /**
     * A tank as a pipe sees it: the product tank drains and takes nothing in, the Drilling Fluid tank takes a Drilling
     * Fluid in and gives nothing back, since the Harvester spends it itself (ADR 0004).
     */
    private static final class FluidFace extends GuardedResourceHandler<FluidResource> {

        private final boolean fills;

        FluidFace(ResourceHandler<FluidResource> tank, boolean fills) {
            super(tank);
            this.fills = fills;
        }

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
