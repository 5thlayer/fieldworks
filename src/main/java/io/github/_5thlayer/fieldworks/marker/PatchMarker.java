// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github._5thlayer.fieldworks.ore.PatchId;

/**
 * A patch's marker on the map: its resource, its centre and the units its blocks held when placed.
 * An oil field's resource is {@link #CRUDE_OIL}, and its total is the summed amount of its wells.
 */
public record PatchMarker(String resource, int x, int y, int z, long total) {

    public static final String CRUDE_OIL = "crude_oil";

    public static final Codec<PatchMarker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("resource").forGetter(PatchMarker::resource),
            Codec.INT.fieldOf("x").forGetter(PatchMarker::x),
            Codec.INT.fieldOf("y").forGetter(PatchMarker::y),
            Codec.INT.fieldOf("z").forGetter(PatchMarker::z),
            Codec.LONG.fieldOf("total").forGetter(PatchMarker::total))
            .apply(instance, PatchMarker::new));

    public boolean isOil() {
        return resource.equals(CRUDE_OIL);
    }

    public PatchId id(String dimension) {
        return new PatchId(dimension, resource, x, z);
    }
}
