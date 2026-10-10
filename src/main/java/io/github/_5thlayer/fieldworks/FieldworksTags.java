// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/** The tags Fieldworks reads, each set by data a pack may replace or extend. */
public final class FieldworksTags {

    /** The blocks, besides ore blocks, that a Harvester breaks for their loot. */
    public static final TagKey<Block> HARVESTABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvestable"));

    /** The fluids a Harvest Recipe may consume, and so the only ones a Harvester's Drilling Fluid tank takes (#1). */
    public static final TagKey<Fluid> DRILLING_FLUIDS = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "drilling_fluids"));

    private FieldworksTags() {
    }
}
