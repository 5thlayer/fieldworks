// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.Requirement;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * How much ore is left in the block the player is looking at. The stages give the survey and this
 * gives the count; both read the same amount. The count is server-side, so it is asked for. Found by
 * Jade's annotation scan and named nowhere else, so Jade stays a compile-time dependency.
 */
@WailaPlugin
public class OreJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "ore_amount");

    private static final String REMAINING = "OreRemaining";
    private static final String INITIAL = "OreInitial";
    private static final String REQUIREMENT = "OreRequirement";

    private static final IServerDataProvider<BlockAccessor> DATA = new IServerDataProvider<>() {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlock() instanceof OreBlock ore)
                    || !(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            tag.putInt(REMAINING, OreMining.remaining(level, accessor.getPosition(), ore));
            tag.putInt(INITIAL, OreMining.initialAmount(level, ore, accessor.getPosition()));
            Requirement.of(level, accessor.getBlockState()).ifPresent(requirement ->
                    tag.store(REQUIREMENT, ComponentSerialization.CODEC, level.registryAccess().createSerializationContext(NbtOps.INSTANCE), requirement));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    };

    private static final IBlockComponentProvider TOOLTIP = new IBlockComponentProvider() {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(REMAINING)) {
                return;
            }
            tooltip.add(Component.translatable(
                    "tooltip.fieldworks.ore.jade.amount", data.getIntOr(REMAINING, 0), data.getIntOr(INITIAL, 0)));
            requirementOf(accessor).ifPresent(tooltip::add);
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    };

    /** The requirement the server sent, when the block's Default recipe is replaced. */
    private static Optional<Component> requirementOf(BlockAccessor accessor) {
        return accessor.getServerData().read(REQUIREMENT, ComponentSerialization.CODEC,
                accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE));
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DATA, OreBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TOOLTIP, OreBlock.class);
        // The pickaxe icon says hand mining is the way, which the requirement line contradicts.
        registration.addTooltipCollectedCallback((box, accessor) -> {
            if (accessor instanceof BlockAccessor block && block.getBlock() instanceof OreBlock && requirementOf(block).isPresent()) {
                ITooltip tooltip = box.getTooltip();
                for (Identifier id : List.of(JadeIds.MC_HARVEST_TOOL, JadeIds.MC_HARVEST_TOOL_NEW_LINE, JadeIds.MC_HARVEST_TOOL_CREATIVE)) {
                    tooltip.remove(id);
                }
            }
        });
    }
}
