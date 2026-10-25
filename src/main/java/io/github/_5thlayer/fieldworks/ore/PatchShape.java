// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.util.BitSet;

/**
 * The columns a disc covers: where the spot's cone plus {@code (octaves - offset) * blob amplitude}
 * is above zero. Computed once at generation and saved with the piece, so placement reads the mask
 * rather than recomputing it.
 */
public final class PatchShape {

    /** One basis noise, read at already-scaled coordinates. */
    @FunctionalInterface
    public interface Noise {
        double at(double x, double z);
    }

    /** Whether a column may hold ore: a spot's columns that fall off the land are dropped. */
    @FunctionalInterface
    public interface Land {
        boolean at(int x, int z);
    }

    /** ImprovedNoise's range with margin, so the scan's square holds every column the edge can reach. */
    private static final double NOISE_BOUND = 1.1;

    private final int centreX;
    private final int centreZ;
    private final double radius;
    private final int reach;
    private final BitSet columns;

    private PatchShape(int centreX, int centreZ, double radius, int reach, BitSet columns) {
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.radius = radius;
        this.reach = reach;
        this.columns = columns;
    }

    public static PatchShape of(PatchLaw law, double sizeFactor, int centreX, int centreZ, Noise noise,
            Land land) {
        double distance = law.distance(centreX, centreZ);
        double radius = law.radius(sizeFactor, distance);
        int reach = reachOf(law, sizeFactor, centreX, centreZ);
        if (reach == 0) {
            return new PatchShape(centreX, centreZ, radius, 0, new BitSet());
        }
        double height = law.height(sizeFactor, distance);
        double amplitude = law.blobAmplitude(distance);
        OreCorpus.Edge edge = law.edge();

        int side = 2 * reach + 1;
        BitSet columns = new BitSet(side * side);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double cone = height * (1 - Math.sqrt(dx * dx + dz * dz) / radius);
                double octaves = 0;
                for (OreCorpus.Octave octave : edge.octaves()) {
                    octaves += octave.outputScale()
                            * noise.at((centreX + dx) * octave.inputScale(), (centreZ + dz) * octave.inputScale());
                }
                if (cone + (octaves - edge.offset()) * amplitude > 0 && land.at(centreX + dx, centreZ + dz)) {
                    columns.set((dx + reach) * side + dz + reach);
                }
            }
        }
        return new PatchShape(centreX, centreZ, radius, reach, columns);
    }

    /**
     * Half the side of the square every column of that disc lies in, from the law alone and no noise
     * sample: the radius the edge's noise bound can push out to. Zero where the disc is empty (inside
     * the minimum distance, or a radius that rounds below one block).
     */
    public static int reachOf(PatchLaw law, double sizeFactor, int centreX, int centreZ) {
        double distance = law.distance(centreX, centreZ);
        double radius = law.radius(sizeFactor, distance);
        if (!law.generatesAt(distance) || Math.round(radius) < 1) {
            return 0;
        }
        double height = law.height(sizeFactor, distance);
        double amplitude = law.blobAmplitude(distance);
        OreCorpus.Edge edge = law.edge();
        double weights = edge.octaves().stream().mapToDouble(OreCorpus.Octave::outputScale).sum();
        return (int) Math.ceil(radius * (1 + (weights * NOISE_BOUND - edge.offset()) * amplitude / height));
    }

    /** A shape read back from its piece's saved mask. */
    public static PatchShape of(int centreX, int centreZ, int reach, long[] mask) {
        return new PatchShape(centreX, centreZ, 0, reach, BitSet.valueOf(mask));
    }

    public long[] mask() {
        return columns.toLongArray();
    }

    public boolean contains(int x, int z) {
        int dx = x - centreX;
        int dz = z - centreZ;
        if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
            return false;
        }
        return columns.get((dx + reach) * (2 * reach + 1) + dz + reach);
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    public int blockCount() {
        return columns.cardinality();
    }

    public int centreX() {
        return centreX;
    }

    public int centreZ() {
        return centreZ;
    }

    /** Half the side of the square every column lies in. */
    public int reach() {
        return reach;
    }

    /** The law's radius before the edge moves it; zero on a shape read back from a mask. */
    public double radius() {
        return radius;
    }
}
