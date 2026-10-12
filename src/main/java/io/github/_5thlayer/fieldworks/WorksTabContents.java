// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.harvester.Harvesters;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.oil.Tar;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.libworks.WorksTab;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Every Fieldworks item, in the suite's Works tab and no other (factoryworks ADR-0128). One listener,
 * since separate ones would add in registration order rather than this one (#4).
 */
final class WorksTabContents {

    private WorksTabContents() {
    }

    static void add(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != WorksTab.KEY) {
            return;
        }
        for (TierBlock tier : TierBlock.values()) {
            event.accept(Harvesters.item(tier).get());
        }
        for (OreResource resource : OreResource.values()) {
            event.accept(FieldworksRegistries.oreItem(resource).get());
        }
        event.accept(FieldworksRegistries.SULFUR.get());
        event.accept(FieldworksRegistries.RAW_URANIUM.get());
        event.accept(FieldworksRegistries.URANIUM_INGOT.get());
        event.accept(Tar.ITEM.get());
        event.accept(CrudeOil.BUCKET.get());
        event.accept(DrillingMud.BOTTLE.get());
        event.accept(DrillingMud.BUCKET.get());
    }
}
