// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** How a chunk's ore deltas are saved. Positions are packed longs, written as strings for map keys. */
public final class OreCodecs {

    private static final Codec<Long> POSITION = Codec.STRING.xmap(Long::parseLong, String::valueOf);

    private static final Codec<Map<Long, Integer>> DRAWN = Codec.unboundedMap(POSITION, Codec.INT);

    public static final Codec<OreDelta> DELTA = RecordCodecBuilder.create(instance -> instance.group(
                    DRAWN.fieldOf("drawn").forGetter(OreDelta::drawn))
            .apply(instance, OreDelta::of));

    private OreCodecs() {
    }
}
