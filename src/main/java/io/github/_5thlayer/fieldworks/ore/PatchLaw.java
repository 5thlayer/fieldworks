// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

/**
 * A patch's law for one resource: a spot's quantity, size and edge, and a block's richness, by
 * distance from world origin. The quantity stops growing at 1600 blocks because density does;
 * richness has no cap. Distance is the centre's, in Overworld-equivalent blocks ({@link
 * PatchDimension}), and nothing generates below {@code minDistance} of it (ADR-0132).
 */
public record PatchLaw(OreCorpus.Patch spot, OreCorpus.DensityLaw density,
        OreCorpus.DistanceLaw richnessLaw, OreCorpus.Edge edge, PatchDimension dimension, int minDistance) {

    public static PatchLaw of(OreResource resource) {
        OreCorpus corpus = OreCorpus.get();
        OreCorpus.Resource entry = resource.corpus();
        return new PatchLaw(entry.patch(), corpus.densityLaw(), corpus.distanceLaw(), corpus.edge(),
                entry.dimension(), entry.minDistance());
    }

    /** A column's distance from origin as the laws count it: Nether blocks count eight times over. */
    public double distance(int x, int z) {
        return dimension.distance(x, z);
    }

    /** Whether a centre at that distance is far enough out for the resource to generate. */
    public boolean generatesAt(double distance) {
        return distance >= minDistance;
    }

    public double density(double distance) {
        return density.at(spot.baseDensity(), distance);
    }

    /** The spot's quantity at that distance, scaled by its own size factor. */
    public double quantity(double sizeFactor, double distance) {
        return sizeFactor * 1_000_000 / spot.baseSpotsPerKm2() * density(distance);
    }

    public double richness(double distance) {
        return richnessLaw.richnessAt(distance);
    }

    public long total(double sizeFactor, double distance) {
        return (long) (quantity(sizeFactor, distance) * richness(distance));
    }

    /** {@code random_penalty_between(minimum, maximum, 1)} for a uniform {@code unit} in [0, 1]. */
    public double sizeFactor(double unit) {
        return spot.spotSizeMinimum() + (spot.spotSizeMaximum() - spot.spotSizeMinimum()) * unit;
    }

    public double radius(double sizeFactor, double distance) {
        return Math.min(edge.radiusCap(), spot.rqFactor() * Math.cbrt(quantity(sizeFactor, distance)));
    }

    /** The peak of the spot's cone: a cone of this radius whose volume is the spot's quantity. */
    public double height(double sizeFactor, double distance) {
        double radius = radius(sizeFactor, distance);
        return radius == 0 ? 0 : quantity(sizeFactor, distance) / (Math.PI / 3 * radius * radius);
    }

    /** {@code regular_spot_height_typical_at}: the cone of a mean-sized spot, ignoring the cap. */
    public double typicalHeight(double distance) {
        return Math.cbrt(quantity(spot.meanSpotSize(), distance))
                / (Math.PI / 3 * spot.rqFactor() * spot.rqFactor());
    }

    public double blobAmplitude(double distance) {
        return spot.blobAmplitudeMultiplier()
                * Math.min(typicalHeight(spot.blobAmplitudeMaximumDistance()), typicalHeight(distance));
    }
}
