// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

/**
 * The one dimension a resource's Patches lie in (ADR-0132). Distances are in Overworld-equivalent
 * blocks, so a Nether centre counts for {@link #distanceScale()} times its distance from origin and
 * one set of law numbers serves both dimensions. Free of Minecraft so it loads in a plain unit test.
 */
public enum PatchDimension {
    OVERWORLD("overworld", "minecraft:overworld", 1),
    NETHER("nether", "minecraft:the_nether", 8);

    private final String key;
    private final String levelId;
    private final int distanceScale;

    PatchDimension(String key, String levelId, int distanceScale) {
        this.key = key;
        this.levelId = levelId;
        this.distanceScale = distanceScale;
    }

    /** The name the Patch numbers give it. */
    public String key() {
        return key;
    }

    /** The id of the level it is. */
    public String levelId() {
        return levelId;
    }

    public int distanceScale() {
        return distanceScale;
    }

    /** A centre's distance from origin in Overworld-equivalent blocks. */
    public double distance(int x, int z) {
        return distanceScale * Math.sqrt((double) x * x + (double) z * z);
    }

    public static PatchDimension of(String key) {
        for (PatchDimension dimension : values()) {
            if (dimension.key.equals(key)) {
                return dimension;
            }
        }
        throw new IllegalArgumentException(key + " is not a Patch dimension");
    }
}
