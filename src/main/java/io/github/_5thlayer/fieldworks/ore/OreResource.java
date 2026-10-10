// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

/** The six resources a patch holds, and the item one draw pays out. */
public enum OreResource {
    IRON("iron", "minecraft:raw_iron", 3.0f, Tier.STONE),
    COPPER("copper", "minecraft:raw_copper", 3.0f, Tier.STONE),
    COAL("coal", "minecraft:coal", 3.0f, Tier.WOOD),
    STONE("stone", "minecraft:cobblestone", 1.5f, Tier.WOOD),
    URANIUM("uranium", "fieldworks:raw_uranium", 3.0f, Tier.IRON),
    SULFUR("sulfur", "fieldworks:sulfur", 3.0f, Tier.STONE);

    /** The lowest pickaxe that mines the ore, in vanilla's tiers. */
    public enum Tier {
        WOOD, STONE, IRON
    }

    private final String key;
    private final String drop;
    private final float hardness;
    private final Tier tier;

    OreResource(String key, String drop, float hardness, Tier tier) {
        this.key = key;
        this.drop = drop;
        this.hardness = hardness;
        this.tier = tier;
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

    public Tier tier() {
        return tier;
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
