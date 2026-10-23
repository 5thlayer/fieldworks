// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github._5thlayer.fieldworks.harvester.HarvestRecipe.Result;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Choice;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Idle;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Machine;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import com.mojang.serialization.Codec;
import io.github._5thlayer.libworks.transfer.GuardedResourceHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester at work: each tick it exchanges the containers in its fill and drain slots with its tanks, pushes its
 * output buffer and its crude into the item and fluid handlers at its output face, then, with room for the result and
 * a tick of power, puts a tick into the operation the choice module names. A full buffer or product tank stops it;
 * nothing is dropped in the world. {@link HarvesterPower} pays for the work and {@link HarvesterFluids} holds the
 * tanks; this keeps the tick, the choice and what it shows of itself.
 */
public final class HarvesterBlockEntity extends BlockEntity implements MenuProvider {

    static final int BUFFER_SLOTS = 9;

    private static final String BUFFER_KEY = "buffer";
    private static final String PROGRESS_KEY = "progress";
    private static final String CURSOR_KEY = "cursor";
    private static final String HELD_KEY = "held_recipe";
    private static final String HEADS_KEY = "drill_heads";
    private static final Codec<List<Integer>> HEADS_CODEC = Codec.INT.listOf();

    private final ItemStacksResourceHandler buffer = new HarvesterSlots(BUFFER_SLOTS, this::setChanged);
    private final HarvesterPower power = new HarvesterPower(buffer, this::setChanged);
    private final HarvesterFluids fluids = new HarvesterFluids(buffer, this::setChanged);
    private final ResourceHandler<ItemResource> face =
            new Face(new CombinedResourceHandler<>(power.fuel(), fluids.fill(), fluids.drain(), buffer));

    /** Whether the energy capability was last seen exposed, so a reload that re-powers the tier invalidates it. */
    private boolean exposesEnergy;

    private int progress;
    /** Ticks the operation under way takes on this tier, for the menu's progress bar; not saved, set as it works (#13). */
    private int operationTicks;
    private int cursor;
    /**
     * What each of the nine columns shows (ADR 0008), in the cursor's order: 0 for no source, 1 for a source directly
     * under the Harvester, whose bottom block is the drill head, and more for a source that deep, whose drill head
     * stands in the cell above it. Saved, so a head placed before a reload is taken away when its source changes, and
     * sent to watchers, who draw it.
     */
    private int[] heads = new int[DrillParts.COLUMNS];

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
        return fluids.face();
    }

    /**
     * The energy capability on every face of the footprint, sized by the tier's FE buffer: it takes FE and gives
     * none back. A tier that takes no FE exposes none (ADR 0005), and neither does one whose file is missing.
     */
    @Nullable EnergyHandler energyFace() {
        return power.energyFace(tier());
    }

    private TierBlock tierBlock() {
        return ((HarvesterBlock) getBlockState().getBlock()).tier();
    }

    private @Nullable HarvesterTier tier() {
        return HarvesterTiers.get(tierBlock());
    }

    public void serverTick(ServerLevel level) {
        List<Column> columns = columns(level);
        followWorld(level, columns);
        fluids.exchangeContainers();
        pushOutput(level);
        HarvesterTier tier = tier();
        exposeEnergy(level, tier != null && tier.power().fe());
        if (tier == null) {
            return;
        }
        HarvestRecipes.Loaded recipes = HarvestRecipes.loaded(level);
        Optional<OperationChoice.Recipe<Result>> heldChoice = Optional.empty();
        if (held != null) {
            OperationChoice.Recipe<Result> recipe = recipes.byId().get(held);
            if (recipe == null) {
                // A Held recipe a reload removed matches nothing; it never falls back to the Default recipes (#10).
                idle = IdleReason.NOTHING_MATCHING;
                return;
            }
            heldChoice = Optional.of(recipe);
        }
        List<BlockPos> layer = columns.stream().map(Column::pos).toList();
        Machine<Result> machine = new Machine<>(tier.rank(), fluids.choiceTank(), operation -> work(level, layer, operation) != null,
                power.canPay(level, tier));
        Choice<Result> choice = OperationChoice.choose(sources(level, layer), cursor, recipes.choices(), heldChoice, machine);
        if (!(choice instanceof Operation<Result> operation)) {
            idle = ((Idle<Result>) choice).reason();
            return;
        }
        Runnable work = work(level, layer, operation);
        if (work == null) {
            return;
        }
        int ticks = tier.ticks(operation.ticks());
        boolean completes = progress + 1 >= ticks;
        // The fluid is taken on completion, so it is checked before that tick is paid for: one short keeps its place (#11).
        if (completes && !fluids.takeDrillingFluid(operation.drillingFluid(), false)) {
            idle = IdleReason.NO_FLUID;
            return;
        }
        if (!power.pay(level, tier, progress, ticks)) {
            return;
        }
        idle = null;
        progress++;
        operationTicks = ticks;
        if (completes) {
            fluids.takeDrillingFluid(operation.drillingFluid(), true);
            work.run();
            progress = 0;
            cursor = operation.nextCursor();
        }
        setChanged();
    }

    /** The operation's work, or null while there is no room for what it yields. */
    private @Nullable Runnable work(ServerLevel level, List<BlockPos> layer, Operation<Result> operation) {
        BlockPos at = layer.get(operation.position());
        Optional<Result> result = operation.recipe().map(OperationChoice.Recipe::result);
        return switch (HarvestSources.kindOf(level.getBlockState(at))) {
            case WELL -> level.getBlockEntity(at) instanceof OilWellBlockEntity well ? pump(well, result) : null;
            case ORE_BLOCK -> draw(level, at, result);
            case HARVESTABLE -> breakBlock(level, at, result);
            case NONE -> null;
        };
    }

    /**
     * One draw of the ore block at {@code at}, paying the recipe's result or, with none, the block's own item,
     * or null while the buffer has no room for it.
     */
    private @Nullable Runnable draw(ServerLevel level, BlockPos at, Optional<Result> recipeResult) {
        OreBlock ore = (OreBlock) level.getBlockState(at).getBlock();
        ItemStack stack = recipeResult.map(Result::stack).orElseGet(() -> drop(ore));
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
     * The harvestable block at {@code at} broken to air, its loot, or the recipe's result in its place,
     * going to the buffer; or null while the buffer has no room for all of it. The loot table runs against an
     * unenchanted pick and nothing checks the tool, so a diamond ore breaks for its diamond (#1).
     */
    private @Nullable Runnable breakBlock(ServerLevel level, BlockPos at, Optional<Result> recipeResult) {
        BlockState state = level.getBlockState(at);
        List<ItemStack> loot = recipeResult.map(result -> List.of(result.stack()))
                .orElseGet(() -> Block.getDrops(state, level, at, level.getBlockEntity(at), null, new ItemStack(Items.DIAMOND_PICKAXE)));
        if (!fitsAll(loot)) {
            return null;
        }
        return () -> {
            // Not removeBlock or destroyBlock: a waterlogged block would leave water, not air (#6).
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
     * with a fixed crude result pumps that many mB in place of the cycle's
     * yield; the cycle still takes its step off the well, so a well above its floor goes on decaying (#12). A recipe
     * whose result is the well's own cycle, or an item, which only a block tag naming the well could give it, pumps the
     * cycle's own yield, which never rises above the well's floor (ADR 0081).
     */
    private @Nullable Runnable pump(OilWellBlockEntity well, Optional<Result> recipeResult) {
        OptionalInt recipeCrude = switch (recipeResult.orElse(null)) {
            case Result.Crude fixed -> OptionalInt.of(fixed.millibuckets());
            case Result.Cycle cycle -> OptionalInt.empty();
            case Result.Item item -> OptionalInt.empty();
            case null -> OptionalInt.empty();
        };
        if (!fluids.fitsCrude(recipeCrude.orElseGet(well::nextCycle))) {
            return null;
        }
        return () -> {
            int cycled = well.cycle();
            int crude = recipeCrude.orElse(cycled);
            if (crude > 0) {
                fluids.addCrude(crude);
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
        int energy = power.energy(tier);
        FluidStack drillingFluid = fluids.drillingFluid();
        FluidStack crude = fluids.crude();
        return new HarvesterStatus(takesFuel, takesEnergy, takesFuel ? power.burnLeft() : 0, takesFuel ? power.burnTotal() : 0,
                takesFuel ? tier.power().burnPerOperation() : 0,
                takesEnergy ? energy : 0, takesEnergy ? tier.feBuffer() : 0, progress, operationTicks, idleReason(),
                drillingFluid.getFluid(), drillingFluid.getAmount(), crude.getFluid(), crude.getAmount(), HarvesterFluids.TANK_CAPACITY);
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
        return power.fuel();
    }

    ItemStacksResourceHandler fillSlots() {
        return fluids.fill();
    }

    ItemStacksResourceHandler drainSlots() {
        return fluids.drain();
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

    /**
     * What one of the nine columns under the footprint looks down to: the first block it does not pass at {@code
     * pos}, {@code depth} blocks under the origin, or with no such block within reach the block directly under, with
     * depth {@link Reach#NONE}. {@code source} is whether that block is one a Harvester works.
     */
    record Column(BlockPos pos, int depth, boolean source) {

        /** What the column shows (see {@code heads}). */
        int shown() {
            return source ? depth : Reach.NONE;
        }
    }

    /**
     * The nine columns, in the fixed order the cursor counts in, whatever way the Harvester faces. Each looks down from
     * the block directly under the Harvester through air, replaceable blocks, fluids and drill heads, up to the tier's
     * reach past that block (ADR 0008); a Harvester whose tier is missing has none.
     */
    private List<Column> columns(ServerLevel level) {
        HarvesterTier tier = tier();
        int reach = tier == null ? 0 : tier.reach();
        List<Column> columns = new ArrayList<>(DrillParts.COLUMNS);
        for (int index = 0; index < DrillParts.COLUMNS; index++) {
            BlockPos top = worldPosition.offset(DrillParts.dx(index), 0, DrillParts.dz(index));
            int depth = Reach.firstBlock(reach, d -> passes(level.getBlockState(top.below(d))));
            BlockPos at = top.below(Math.max(depth, 1));
            columns.add(new Column(at, depth, depth != Reach.NONE && HarvestSources.kindOf(level.getBlockState(at)) != OperationChoice.Kind.NONE));
        }
        return columns;
    }

    /** Whether a column looks through this block: air, a replaceable block, a fluid or a drill head. */
    private static boolean passes(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof DrillHeadBlock;
    }

    /**
     * Brings the bottom blocks and the placed drill heads to what the columns show, whether or not the Harvester is
     * working (ADR 0008): a head is placed in the cell above a source lower than the block directly under the Harvester
     * and taken away when its source changes, and a bottom block is the drill head where its source lies directly under.
     */
    private void followWorld(ServerLevel level, List<Column> columns) {
        boolean changed = false;
        for (int index = 0; index < DrillParts.COLUMNS; index++) {
            int shown = columns.get(index).shown();
            int dx = DrillParts.dx(index);
            int dz = DrillParts.dz(index);
            if (heads[index] != shown) {
                if (DrillParts.placesHead(heads[index])) {
                    DrillHeadBlock.removeAll(level, List.of(headCell(dx, dz, heads[index])));
                }
                heads[index] = shown;
                changed = true;
            }
            if (DrillParts.placesHead(shown)) {
                BlockPos cell = headCell(dx, dz, shown);
                if (!(level.getBlockState(cell).getBlock() instanceof DrillHeadBlock)) {
                    level.setBlock(cell, DrillHeadBlock.placedAt(level, cell), Block.UPDATE_ALL);
                }
            }
            BlockPos bottom = worldPosition.offset(dx, 0, dz);
            BlockState state = level.getBlockState(bottom);
            if (state.hasProperty(HarvesterBlock.DRILL_HEAD) && state.getValue(HarvesterBlock.DRILL_HEAD) != (shown == DrillParts.UNDER)) {
                level.setBlock(bottom, state.setValue(HarvesterBlock.DRILL_HEAD, shown == DrillParts.UNDER), Block.UPDATE_CLIENTS);
            }
        }
        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Where the drill head of the column at {@code dx}, {@code dz} showing {@code depth} stands: over its source. */
    private BlockPos headCell(int dx, int dz, int depth) {
        return worldPosition.offset(dx, -DrillParts.headBelow(depth), dz);
    }

    /** Whether this Harvester shows a placed drill head at {@code pos}. */
    boolean shows(BlockPos pos) {
        return placedHeads().contains(pos);
    }

    /** Where this Harvester's placed drill heads stand. */
    private List<BlockPos> placedHeads() {
        List<BlockPos> placed = new ArrayList<>();
        for (int index = 0; index < DrillParts.COLUMNS; index++) {
            if (DrillParts.placesHead(heads[index])) {
                placed.add(headCell(DrillParts.dx(index), DrillParts.dz(index), heads[index]));
            }
        }
        return placed;
    }

    /** What each column shows, for the renderer; see {@link DrillParts}. */
    public int[] drillHeads() {
        return heads.clone();
    }

    private static List<Source> sources(ServerLevel level, List<BlockPos> layer) {
        return layer.stream().map(pos -> HarvestSources.at(level, pos)).toList();
    }

    static ItemStack drop(OreBlock ore) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(ore.resource().drop())));
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
        fluids.pushCrude(level, next, out.getOpposite());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        buffer.serialize(output.child(BUFFER_KEY));
        power.save(output);
        fluids.save(output);
        output.putInt(PROGRESS_KEY, progress);
        output.putInt(CURSOR_KEY, cursor);
        if (held != null) {
            output.store(HELD_KEY, Identifier.CODEC, held);
        }
        output.store(HEADS_KEY, HEADS_CODEC, Arrays.stream(heads).boxed().toList());
    }

    /** Only what its watchers draw: the drill heads. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store(HEADS_KEY, HEADS_CODEC, Arrays.stream(heads).boxed().toList());
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer.deserialize(input.childOrEmpty(BUFFER_KEY));
        power.load(input);
        fluids.load(input);
        progress = input.getIntOr(PROGRESS_KEY, 0);
        cursor = input.getIntOr(CURSOR_KEY, 0);
        held = input.read(HELD_KEY, Identifier.CODEC).orElse(null);
        heads = input.read(HEADS_KEY, HEADS_CODEC).filter(saved -> saved.size() == DrillParts.COLUMNS)
                .map(saved -> saved.stream().mapToInt(Integer::intValue).toArray()).orElseGet(() -> new int[DrillParts.COLUMNS]);
    }

    /** A broken Harvester drops what it holds, as a furnace does. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            DrillHeadBlock.removeAll(level, placedHeads());
            for (ItemStacksResourceHandler slots : List.of(power.fuel(), fluids.fill(), fluids.drain(), buffer)) {
                for (ItemStack stack : slots.copyToList()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
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
                case FILL_SLOT -> HarvesterFluids.acceptsCrude(resource);
                case DRAIN_SLOT -> HarvesterFluids.holdsDrillingFluid(resource);
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
}
