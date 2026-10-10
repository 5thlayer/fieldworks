// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Locale;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.resources.Identifier;

/**
 * The fixed Harvester blocks, one per Harvester Tier (ADR 0005). A block's id names its tier's file,
 * {@code data/<ns>/harvester_tier/<path>.json}, which gives every figure; its name comes from lang.
 */
public enum TierBlock {
    HARVESTER_1;

    /** The block and item name, {@code harvester_1}. */
    public String blockName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String partBlockName() {
        return blockName() + "_part";
    }

    /** The block's id, which is also its tier file's. */
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, blockName());
    }
}
