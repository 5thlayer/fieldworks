// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

/** The patches the server has found, per dimension: a patch is found once, by whoever is first sent its centre chunk. */
public final class FoundPatches {

    public static final Codec<FoundPatches> CODEC =
            Codec.unboundedMap(Codec.STRING, PatchMarker.CODEC.listOf()).xmap(FoundPatches::decode, FoundPatches::encode);

    private final Map<String, Set<PatchMarker>> found = new HashMap<>();

    /** True if the patch was not already found. */
    public boolean add(String dimension, PatchMarker marker) {
        return found.computeIfAbsent(dimension, d -> new LinkedHashSet<>()).add(marker);
    }

    public Set<PatchMarker> in(String dimension) {
        return found.getOrDefault(dimension, Set.of());
    }

    private static FoundPatches decode(Map<String, List<PatchMarker>> encoded) {
        FoundPatches decoded = new FoundPatches();
        encoded.forEach((dimension, markers) -> decoded.found.put(dimension, new LinkedHashSet<>(markers)));
        return decoded;
    }

    private Map<String, List<PatchMarker>> encode() {
        Map<String, List<PatchMarker>> encoded = new HashMap<>();
        found.forEach((dimension, markers) -> encoded.put(dimension, new ArrayList<>(markers)));
        return encoded;
    }
}
