// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlock;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatus;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatusLines;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

/**
 * A Harvester in Jade (#13): its state, worded by the same {@link HarvesterStatusLines} as its menu, so the idle
 * reason is the choice module's here too; its items as icons with their counts; FE as Jade's own bar; and each tank
 * as its own bar. The rest is the menu's.
 *
 * <p>Found by Jade's annotation scan and named nowhere else, so Jade stays a soft dependency. Every block of the
 * footprint shows this: Groundworks' own Jade plugin reads a part as its origin.
 */
@WailaPlugin
public class HarvesterJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester");
    private static final Identifier TANKS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester_tanks");
    private static final Identifier ITEMS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester_items");
    private static final String DRILLING = "drilling";
    private static final String PRODUCT = "product";

    private static class Report implements StreamServerDataProvider<BlockAccessor, HarvesterStatus> {

        @Override
        public @Nullable HarvesterStatus streamData(BlockAccessor accessor) {
            return accessor.getBlockEntity() instanceof HarvesterBlockEntity harvester ? harvester.status() : null;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, HarvesterStatus> streamCodec() {
            return HarvesterStatus.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    /** Draws the state; apart from {@link Report} so that a dedicated server never loads Jade's drawing. */
    private static final class Client extends Report implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            decodeFromData(accessor).ifPresent(status -> tooltip.add(HarvesterStatusLines.state(status.idle())));
        }
    }

    /** What the item capability holds, slot by slot. */
    private static final IServerExtensionProvider<ItemStack> ITEM_DATA = new IServerExtensionProvider<>() {
        @Override
        public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlockEntity() instanceof HarvesterBlockEntity)) {
                return null;
            }
            ResourceHandler<ItemResource> items = block.getLevel().getCapability(Capabilities.Item.BLOCK, block.getPosition(), null);
            if (items == null) {
                return null;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                if (!items.getResource(index).isEmpty()) {
                    stacks.add(items.getResource(index).toStack(items.getAmountAsInt(index)));
                }
            }
            return List.of(new ViewGroup<>(stacks));
        }

        @Override
        public Identifier getUid() {
            return ITEMS;
        }
    };

    /** Each stack its count as amount text, which makes Jade draw icons where a few stacks would get their names. */
    private static final IClientExtensionProvider<ItemStack, ItemView> ITEM_ICONS = new IClientExtensionProvider<>() {
        @Override
        public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            return ClientViewGroup.map(groups, stack -> new ItemView(stack)
                    .amountText(IDisplayHelper.get().humanReadableNumber(stack.getCount(), "", false, null)), null);
        }

        @Override
        public Identifier getUid() {
            return ITEMS;
        }
    };

    /** Each tank its own titled group, where Jade's own reading of the fluid capability would merge both into one bar. */
    private static final IServerExtensionProvider<FluidView.Data> TANK_DATA = new IServerExtensionProvider<>() {
        @Override
        public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlockEntity() instanceof HarvesterBlockEntity harvester)) {
                return null;
            }
            HarvesterStatus status = harvester.status();
            return List.of(tank(DRILLING, status.drillingFluidStack(), status.tankCapacity()),
                    tank(PRODUCT, status.productStack(), status.tankCapacity()));
        }

        @Override
        public Identifier getUid() {
            return TANKS;
        }
    };

    private static ViewGroup<FluidView.Data> tank(String id, FluidStack stack, int capacity) {
        JadeFluidObject shown = stack.isEmpty() ? JadeFluidObject.empty() : JadeFluidObject.of(stack.getFluid(), stack.getAmount());
        return new ViewGroup<>(List.of(new FluidView.Data(shown, capacity)), Optional.of(id), Optional.empty());
    }

    private static final IClientExtensionProvider<FluidView.Data, FluidView> TANK_LINES = new IClientExtensionProvider<>() {
        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, (group, shown) ->
                    shown.title = Component.translatable("fieldworks.harvester.tank_title." + group.id));
        }

        @Override
        public Identifier getUid() {
            return TANKS;
        }
    };

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new Report(), HarvesterBlockEntity.class);
        registration.registerFluidStorage(TANK_DATA, HarvesterBlockEntity.class);
        registration.registerItemStorage(ITEM_DATA, HarvesterBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new Client(), HarvesterBlock.class);
        registration.registerFluidStorageClient(TANK_LINES);
        registration.registerItemStorageClient(ITEM_ICONS);
    }
}
