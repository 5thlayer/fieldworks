// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.Optional;

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
 * Offers {@code gametest_packs/lifted_diamond_gate/}, when its test asks: a pack that drops
 * {@code fieldworks:diamond_from_ore}. {@link HandGateTests} turns it on for the one test that needs it, which reloads
 * the server's data, so that test runs in a batch of its own.
 */
@EventBusSubscriber(modid = Fieldworks.MOD_ID)
final class LiftedGatePack {

    static final String FOLDER = "lifted_diamond_gate";
    static final String ID = Fieldworks.MOD_ID + "_" + FOLDER;

    private static volatile boolean offered;

    private LiftedGatePack() {
    }

    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !"gameTestServer".equals(System.getProperty("libworks.run", ""))) {
            return;
        }
        var contents = ModList.get().getModFileById(Fieldworks.MOD_ID).getFile().getContents();
        event.addRepositorySource(packs -> {
            if (offered) {
                packs.accept(Pack.readMetaAndCreate(
                        new PackLocationInfo(ID, Component.literal(ID), PackSource.create(PackSource.BUILT_IN::decorate, false), Optional.empty()),
                        new JarContentsPackResources.JarContentsResourcesSupplier(contents, "gametest_packs/" + FOLDER),
                        PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false)));
            }
        });
    }

    /**
     * Whether the server's pack repository offers the pack. The game test server switches on every pack it is offered
     * when it starts, so the pack is offered only from the moment its test asks, and the repository is read again.
     */
    static void offer(MinecraftServer server, boolean on) {
        offered = on;
        server.getPackRepository().reload();
    }
}
