// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
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

    private static final IServerDataProvider<BlockAccessor> DATA = new IServerDataProvider<>() {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlock() instanceof OreBlock ore)
                    || !(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            tag.putInt(REMAINING, OreMining.remaining(level, accessor.getPosition(), ore));
            tag.putInt(INITIAL, OreMining.initialAmount(level, ore, accessor.getPosition()));
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
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    };

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DATA, OreBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TOOLTIP, OreBlock.class);
    }
}
