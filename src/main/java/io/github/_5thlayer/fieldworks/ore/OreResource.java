// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

/**
 * The six resources a patch holds, the item one draw pays out and the block's vanilla hardness. The
 * pickaxe tier each needs is in the {@code needs_*_tool} tags.
 */
public enum OreResource {
    IRON("iron", "minecraft:raw_iron", 3.0f),
    COPPER("copper", "minecraft:raw_copper", 3.0f),
    COAL("coal", "minecraft:coal", 3.0f),
    STONE("stone", "minecraft:cobblestone", 1.5f),
    URANIUM("uranium", "fieldworks:raw_uranium", 3.0f),
    SULFUR("sulfur", "fieldworks:sulfur", 3.0f);

    private final String key;
    private final String drop;
    private final float hardness;

    OreResource(String key, String drop, float hardness) {
        this.key = key;
        this.drop = drop;
        this.hardness = hardness;
    }

    /** The data key and the block-id stem: {@code iron} gives {@code fieldworks:iron_ore}. */
    public String key() {
        return key;
    }

    public String blockName() {
        return key + "_ore";
    }

    /** The item id one draw pays out; a string so nothing here needs a registry. */
    public String drop() {
        return drop;
    }

    public float hardness() {
        return hardness;
    }

    public OreCorpus.Resource corpus() {
        return OreCorpus.get().resource(key);
    }

    public static OreResource of(String key) {
        for (OreResource resource : values()) {
            if (resource.key.equals(key)) {
                return resource;
            }
        }
        throw new IllegalArgumentException(key + " is not one of the ore resources");
    }
}
