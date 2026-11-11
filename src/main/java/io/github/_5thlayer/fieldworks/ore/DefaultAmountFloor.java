// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * An ore block left without a {@code fieldworks:default_amount} entry starts at 1, the floor a patch block already has
 * (ADR 0013). Free of Minecraft so it loads in a plain unit test; {@link OreDefaultAmount} reads the data map.
 */
final class DefaultAmountFloor {

    static final int FLOOR = 1;

    private DefaultAmountFloor() {
    }

    static int orFloor(@Nullable Integer entry) {
        return entry != null ? entry : FLOOR;
    }

    /** The resources whose ore block has no entry, in the enum's order. */
    static List<OreResource> missing(Function<OreResource, @Nullable Integer> entries) {
        List<OreResource> missing = new ArrayList<>();
        for (OreResource resource : OreResource.values()) {
            if (entries.apply(resource) == null) {
                missing.add(resource);
            }
        }
        return missing;
    }
}
