// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat;

import java.util.List;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlock;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatus;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatusLines;
import io.github._5thlayer.libworks.machine.Tank;
import io.github._5thlayer.libworks.machine.jade.MachineItems;
import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.github._5thlayer.libworks.machine.jade.MachineTanks;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * A Harvester in Jade (#13), through libworks-runtime's machine providers (#30): its state, worded by the same
 * {@link HarvesterStatusLines} as its menu, so the idle reason is the choice module's here too; its items as icons
 * with their counts; FE as Jade's own bar; and each tank as its own titled bar. The rest is the menu's.
 *
 * <p>Found by Jade's annotation scan and named nowhere else, so Jade stays a soft dependency. Every block of the
 * footprint shows this: Groundworks' own Jade plugin reads a part as its origin.
 */
@WailaPlugin
public class HarvesterJadePlugin implements IWailaPlugin {

    private static final Identifier ITEMS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester_items");
    private static final Identifier TANKS = Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester_tanks");

    private static final MachineReport<HarvesterBlockEntity, HarvesterStatus> REPORT = new MachineReport<>(
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvester"), HarvesterBlockEntity.class,
            HarvesterBlockEntity::status, HarvesterStatus.STREAM_CODEC);

    private static List<Tank> tanks(HarvesterBlockEntity harvester) {
        HarvesterStatus status = harvester.status();
        return List.of(
                new Tank("drilling", status.drillingFluidStack(), status.tankCapacity(), "fieldworks.harvester.tank_title.drilling"),
                new Tank("product", status.productStack(), status.tankCapacity(), "fieldworks.harvester.tank_title.product"));
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(REPORT, HarvesterBlockEntity.class);
        registration.registerFluidStorage(new MachineTanks<>(TANKS, HarvesterBlockEntity.class, HarvesterJadePlugin::tanks), HarvesterBlockEntity.class);
        registration.registerItemStorage(new MachineItems(ITEMS), HarvesterBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new MachineReport.Client<>(REPORT,
                (status, tooltip) -> tooltip.add(HarvesterStatusLines.state(status.idle()))), HarvesterBlock.class);
        registration.registerFluidStorageClient(new MachineTanks.Client(TANKS));
        registration.registerItemStorageClient(new MachineItems.Client(ITEMS));
    }
}
