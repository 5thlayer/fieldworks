// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.JarContentsPackResources;

/**
 * Offers a pack under {@code gametest_packs/<folder>/} when its test asks. Turning one on reloads the server's data,
 * so a test that does runs in a batch of its own.
 */
@EventBusSubscriber(modid = Fieldworks.MOD_ID)
final class OfferedPacks {

    /** Drops {@code fieldworks:diamond_from_ore}, for {@link HandGateTests}. */
    static final String LIFTED_DIAMOND_GATE = "lifted_diamond_gate";
    /** Sets iron ore's default amount to 3 and leaves netherite ore without one, for {@link DefaultAmountTests}. */
    static final String ORE_AMOUNT = "ore_amount";

    private static final Set<String> offered = ConcurrentHashMap.newKeySet();

    private OfferedPacks() {
    }

    static String id(String folder) {
        return Fieldworks.MOD_ID + "_" + folder;
    }

    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !"gameTestServer".equals(System.getProperty("libworks.run", ""))) {
            return;
        }
        var contents = ModList.get().getModFileById(Fieldworks.MOD_ID).getFile().getContents();
        event.addRepositorySource(packs -> {
            for (String folder : offered) {
                String id = id(folder);
                packs.accept(Pack.readMetaAndCreate(
                        new PackLocationInfo(id, Component.literal(id), PackSource.create(PackSource.BUILT_IN::decorate, false), Optional.empty()),
                        new JarContentsPackResources.JarContentsResourcesSupplier(contents, "gametest_packs/" + folder),
                        PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false)));
            }
        });
    }

    /**
     * Whether the server's pack repository offers the pack in {@code folder}. The game test server switches on every pack it is offered
     * when it starts, so the pack is offered only from the moment its test asks, and the repository is read again.
     */
    static void offer(MinecraftServer server, String folder, boolean on) {
        if (on) {
            offered.add(folder);
        } else {
            offered.remove(folder);
        }
        server.getPackRepository().reload();
    }
}
