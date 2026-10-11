// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlock;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatus;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatusLines;
import io.github._5thlayer.fieldworks.harvester.HeldRecipeView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * A Harvester's status in Jade (#13): the same {@link HarvesterStatus} its menu shows, worded by the same
 * {@link HarvesterStatusLines}, so the idle reason is the choice module's here too. The server sends the status whole;
 * the client only writes the lines.
 *
 * <p>Found by Jade's annotation scan and named nowhere else, so Jade stays a soft dependency. Every block of the
 * footprint shows this: Groundworks' own Jade plugin reads a part as its origin. The item list Jade draws is its own,
 * from the item capability.
 */
@WailaPlugin
public class HarvesterJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester");

    /** What the server tells Jade: the status and the Held recipe with what it makes. */
    record Data(HarvesterStatus status, Optional<HeldRecipeView> held) {
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Data> CODEC = StreamCodec.composite(
            HarvesterStatus.STREAM_CODEC, Data::status,
            ByteBufCodecs.optional(HeldRecipeView.STREAM_CODEC), Data::held,
            Data::new);

    private static class Report implements StreamServerDataProvider<BlockAccessor, Data> {

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel level) || !(accessor.getBlockEntity() instanceof HarvesterBlockEntity harvester)) {
                return null;
            }
            return new Data(harvester.status(), harvester.heldView(level));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return CODEC;
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    /** Draws the lines; apart from {@link Report} so that a dedicated server never loads Jade's drawing. */
    private static final class Client extends Report implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            decodeFromData(accessor).ifPresent(data -> HarvesterStatusLines.lines(data.status(), data.held()).forEach(tooltip::add));
        }
    }

    private static final Identifier TANKS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester_tanks");
    private static final String DRILLING = "drilling";
    private static final String PRODUCT = "product";

    /** Each tank its own titled group: Jade's own reading of the fluid capability drew both as one bar. */
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
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new Client(), HarvesterBlock.class);
        registration.registerFluidStorageClient(TANK_LINES);
    }
}
