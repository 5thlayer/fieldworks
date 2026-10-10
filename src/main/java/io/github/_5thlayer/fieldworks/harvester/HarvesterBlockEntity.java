// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.Operation;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
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
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester at work: each tick it pushes its output buffer into the item handler at its output face,
 * then, with room for the result and a tick of power, puts a tick into the operation the choice module
 * names. One burn tick buys one tick of work. A full buffer stops it; nothing is dropped in the world.
 */
public final class HarvesterBlockEntity extends BlockEntity {

    /** The output buffer's slots. */
    static final int BUFFER_SLOTS = 9;

    private static final String FUEL_KEY = "fuel";
    private static final String BUFFER_KEY = "buffer";
    private static final String BURN_KEY = "burn_left";
    private static final String PROGRESS_KEY = "progress";
    private static final String CURSOR_KEY = "cursor";
    private static final String HELD_KEY = "held_recipe";

    private final ItemStacksResourceHandler fuel = new Slots(1);
    private final ItemStacksResourceHandler buffer = new Slots(BUFFER_SLOTS);
    private final ResourceHandler<ItemResource> face = new Face(new CombinedResourceHandler<>(fuel, buffer));

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

    public void serverTick(ServerLevel level) {
        pushOutput(level);
        HarvesterTier tier = HarvesterTiers.get(((HarvesterBlock) getBlockState().getBlock()).tier());
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
        OreBlock ore = (OreBlock) level.getBlockState(at).getBlock();
        ItemStack stack = operation.recipe()
                .map(recipe -> recipes.byId().get(recipe.id()).recipe().result().create())
                .orElseGet(() -> drop(ore));
        ItemResource result = ItemResource.of(stack);
        if (!fits(result, stack.getCount()) || !burnTick(level, tier)) {
            return;
        }
        progress++;
        if (progress >= tier.ticks(operation.ticks())) {
            progress = 0;
            cursor = operation.nextCursor();
            if (OreMining.draw(level, ore, at).paid() > 0 && !result.isEmpty()) {
                ResourceHandlerUtil.insertStacking(buffer, result, stack.getCount(), null);
            }
        }
        setChanged();
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
            sources.add(block instanceof OreBlock
                    ? Source.oreBlock(BuiltInRegistries.BLOCK.getKey(block).toString(), tagsOf(block))
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

    private boolean fits(ItemResource result, int amount) {
        if (result.isEmpty()) {
            return true;
        }
        try (Transaction simulated = Transaction.openRoot()) {
            return ResourceHandlerUtil.insertStacking(buffer, result, amount, simulated) == amount;
        }
    }

    /**
     * Spends one burn tick, lighting the next fuel item when the last is spent. A fuel item's remainder,
     * the bucket of a lava bucket, goes to the output buffer, so a fuel item whose remainder has no room
     * there is not lit.
     */
    private boolean burnTick(ServerLevel level, HarvesterTier tier) {
        if (burnLeft == 0 && tier.power().fuel()) {
            ItemResource item = fuel.getResource(0);
            int burnTime = item.isEmpty() ? 0 : item.toStack(1).getBurnTime(null, level.fuelValues());
            if (burnTime > 0) {
                try (Transaction lighting = Transaction.openRoot()) {
                    fuel.extract(0, item, 1, lighting);
                    ItemStackTemplate remainder = item.toStack(1).getCraftingRemainder();
                    if (remainder == null || ResourceHandlerUtil.insertStacking(buffer,
                            ItemResource.of(remainder.create()), 1, lighting) == 1) {
                        lighting.commit();
                        burnLeft = burnTime;
                    }
                }
            }
        }
        if (burnLeft == 0) {
            return false;
        }
        burnLeft--;
        return true;
    }

    /**
     * Pushes the buffer out of the output face, the way the Harvester faces, into the block one past the
     * footprint's edge at the face's bottom centre.
     */
    private void pushOutput(ServerLevel level) {
        if (ResourceHandlerUtil.isEmpty(buffer)) {
            return;
        }
        Direction out = getBlockState().getValue(HarvesterBlock.FACING);
        ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(out, 2), out.getOpposite());
        if (target != null) {
            ResourceHandlerUtil.moveStacking(buffer, target, resource -> true, Integer.MAX_VALUE, null);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fuel.serialize(output.child(FUEL_KEY));
        buffer.serialize(output.child(BUFFER_KEY));
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
        buffer.deserialize(input.childOrEmpty(BUFFER_KEY));
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
            for (ItemStacksResourceHandler slots : List.of(fuel, buffer)) {
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
     * The fuel slot, index 0, takes anything with burn time and gives nothing back; the output buffer takes
     * nothing from outside and gives everything. Buffer slots report no room, so a hopper reads the
     * Harvester full once the fuel slot is.
     */
    private final class Face extends GuardedResourceHandler<ItemResource> {

        Face(ResourceHandler<ItemResource> slots) {
            super(slots);
        }

        private boolean isFuelSlot(int index) {
            return index == 0;
        }

        private boolean burns(ItemResource resource) {
            return level != null && resource.toStack(1).getBurnTime(null, level.fuelValues()) > 0;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return isFuelSlot(index) && burns(resource);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            if (!isFuelSlot(index) || !resource.isEmpty() && !burns(resource)) {
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
            return isFuelSlot(index) ? 0 : super.extract(index, resource, amount, transaction);
        }
    }
}
