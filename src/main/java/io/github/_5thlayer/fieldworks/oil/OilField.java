// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.oil;

import io.github._5thlayer.fieldworks.ore.PatchLaw;
import io.github._5thlayer.fieldworks.ore.PatchShape;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * A crude-oil field: the disc shape of crude's law, the wells a draw deals over it, and the
 * amount each starts with (ADR-0002).
 */
public final class OilField {

    public record Well(int x, int z, long amount) {
    }

    /** Whether a column already holds ore, which a well never displaces. */
    @FunctionalInterface
    public interface Ore {
        boolean at(int x, int z);
    }

    /** Crude's spot size is one fixed factor, so no draw decides it. */
    private static final double SIZE_FACTOR = 1.0;

    private final int centreX;
    private final int centreZ;
    private final PatchShape shape;

    private OilField(int centreX, int centreZ, PatchShape shape) {
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.shape = shape;
    }

    public static OilField of(int centreX, int centreZ, PatchShape.Noise noise, PatchShape.Land land) {
        return new OilField(centreX, centreZ, PatchShape.of(law(), SIZE_FACTOR, centreX, centreZ, noise, land));
    }

    /**
     * The draw leaves a positive value on {@code random_probability} of the columns, and that
     * value, uniform over (0, 1], is then itself rolled as a probability: half of it.
     */
    public static double columnProbability() {
        return OilCorpus.get().randomProbability() / 2;
    }

    /** The collision box's width, whole: two wells' boxes may not overlap. */
    public static int spacing() {
        return (int) Math.ceil(OilCorpus.get().collisionWidth());
    }

    public PatchShape shape() {
        return shape;
    }

    /**
     * The mask's columns in order, each taking one roll against {@link #columnProbability}; a
     * column that passes is a well unless one already stands within {@link #spacing}.
     */
    public List<Well> draw(DoubleSupplier unit) {
        double probability = columnProbability();
        int spacing = spacing();
        int reach = shape.reach();
        List<Well> wells = new ArrayList<>();
        for (int x = centreX - reach; x <= centreX + reach; x++) {
            for (int z = centreZ - reach; z <= centreZ + reach; z++) {
                if (!shape.contains(x, z) || unit.getAsDouble() >= probability) {
                    continue;
                }
                int wx = x;
                int wz = z;
                if (wells.stream().noneMatch(w -> Math.max(Math.abs(w.x() - wx), Math.abs(w.z() - wz)) < spacing)) {
                    wells.add(new Well(x, z, amountAt(x, z)));
                }
            }
        }
        return wells;
    }

    /**
     * {@code (patch / random_probability + additional_richness) × max((1000 + d) / 2600, 1)}, the
     * patch value being the spot's cone alone and {@code d} the column's own distance.
     */
    public long amountAt(int x, int z) {
        PatchLaw law = law();
        OilCorpus corpus = OilCorpus.get();
        double centreDistance = Math.hypot(centreX, centreZ);
        double radius = law.radius(SIZE_FACTOR, centreDistance);
        double cone = radius == 0 ? 0
                : law.height(SIZE_FACTOR, centreDistance) * Math.max(0, 1 - Math.hypot(x - centreX, z - centreZ) / radius);
        return (long) ((cone / corpus.richnessDivisor() + corpus.additionalRichness()) * law.richness(Math.hypot(x, z)));
    }

    public static List<Well> placeable(List<Well> wells, Ore ore) {
        return wells.stream().filter(well -> !ore.at(well.x(), well.z())).toList();
    }

    public static long total(List<Well> wells) {
        return wells.stream().mapToLong(Well::amount).sum();
    }

    private static PatchLaw law() {
        return OilCorpus.get().law();
    }
}
