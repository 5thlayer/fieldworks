// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Map;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Harvester Tiers' files, read with the server's data and again on each reload. A file the codec
 * refuses is logged by the load with why and left out; a Harvester whose tier is left out stays idle,
 * and the load logs that too.
 */
public final class HarvesterTiers extends SimpleJsonResourceReloadListener<HarvesterTier> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "harvester_tier";

    private static volatile Map<Identifier, HarvesterTier> loaded = Map.of();

    private HarvesterTiers() {
        super(HarvesterTier.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    /** The tier a Harvester block works at, or null when its file is missing or was refused. */
    public static @Nullable HarvesterTier get(TierBlock block) {
        return loaded.get(block.id());
    }

    static void addListener(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, DIRECTORY), new HarvesterTiers());
    }

    @Override
    protected void apply(Map<Identifier, HarvesterTier> tiers, ResourceManager manager, ProfilerFiller profiler) {
        loaded = Map.copyOf(tiers);
        for (TierBlock block : TierBlock.values()) {
            if (!tiers.containsKey(block.id())) {
                LOGGER.error("{} has no valid tier file at data/{}/{}/{}.json, so every one stays idle",
                        block.id(), block.id().getNamespace(), DIRECTORY, block.id().getPath());
            }
        }
    }
}
