// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * The frozen numbers behind the patches, read from {@code fieldworks/ore/amounts.json}.
 *
 * <p>A classpath resource rather than a datapack file: the stage count sizes a blockstate property,
 * which is fixed at registration, before any datapack exists. Free of Minecraft so it loads in a
 * plain unit test.
 */
public final class OreCorpus {

    private static final String PATH = "/fieldworks/ore/amounts.json";

    private static final OreCorpus INSTANCE = load(PATH);

    private final Map<String, Resource> resources;
    private final DistanceLaw law;
    private final DensityLaw density;
    private final Edge edge;

    private OreCorpus(Map<String, Resource> resources, DistanceLaw law, DensityLaw density, Edge edge) {
        this.resources = resources;
        this.law = law;
        this.density = density;
        this.edge = edge;
    }

    public static OreCorpus get() {
        return INSTANCE;
    }

    static OreCorpus load(String path) {
        try (InputStream stream = OreCorpus.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("no " + path + " on the classpath");
            }
            JsonObject root = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            Map<String, Resource> resources = new LinkedHashMap<>();
            JsonObject entries = root.getAsJsonObject("resources");
            for (String name : entries.keySet()) {
                JsonObject entry = entries.getAsJsonObject(name);
                List<Double> ratios = new ArrayList<>();
                entry.getAsJsonArray("stage_ratios").forEach(value -> ratios.add(value.getAsDouble()));
                resources.put(name, new Resource(name, entry.get("default_amount").getAsInt(),
                        PatchDimension.of(entry.get("dimension").getAsString()), entry.get("min_distance").getAsInt(),
                        List.copyOf(ratios), patch(entry.getAsJsonObject("patch"))));
            }
            JsonObject distance = root.getAsJsonObject("distance_law");
            JsonObject density = root.getAsJsonObject("density_law");
            JsonObject edge = root.getAsJsonObject("patch_edge");
            List<Octave> octaves = new ArrayList<>();
            edge.getAsJsonArray("octaves").forEach(value -> octaves.add(new Octave(
                    value.getAsJsonObject().get("input_scale").getAsDouble(),
                    value.getAsJsonObject().get("output_scale").getAsDouble())));
            return new OreCorpus(
                    Map.copyOf(resources),
                    new DistanceLaw(distance.get("offset").getAsInt(), distance.get("divisor").getAsInt()),
                    new DensityLaw(
                            density.get("starting_resource_placement_radius").getAsDouble(),
                            density.get("regular_patch_fade_in_distance").getAsDouble(),
                            density.get("double_density_distance").getAsDouble()),
                    new Edge(edge.get("radius_cap").getAsDouble(), List.copyOf(octaves),
                            edge.get("offset").getAsDouble()));
        } catch (IOException broken) {
            throw new IllegalStateException("could not read " + path, broken);
        }
    }

    /** A spot's arguments as a JSON object holds them; oil's law is read the same way. */
    public static Patch patch(JsonObject spot) {
        return new Patch(
                spot.get("base_density").getAsDouble(),
                spot.get("base_spots_per_km2").getAsDouble(),
                spot.get("random_spot_size_minimum").getAsDouble(),
                spot.get("random_spot_size_maximum").getAsDouble(),
                spot.get("regular_rq_factor").getAsDouble(),
                spot.get("regular_blob_amplitude_multiplier").getAsDouble(),
                spot.get("regular_blob_amplitude_maximum_distance").getAsDouble());
    }

    public Resource resource(String name) {
        Resource found = resources.get(name);
        if (found == null) {
            throw new IllegalArgumentException(name + " is not one of the ore resources: " + resources.keySet());
        }
        return found;
    }

    public Map<String, Resource> resources() {
        return resources;
    }

    /** The longest stage ladder, which sizes the blockstate property every ore block shares. */
    public int stageCount() {
        return resources.values().stream()
                .mapToInt(resource -> OreStage.count(resource.stageRatios()))
                .max()
                .orElse(1);
    }

    public DistanceLaw distanceLaw() {
        return law;
    }

    public DensityLaw densityLaw() {
        return density;
    }

    public Edge edge() {
        return edge;
    }

    /**
     * @param defaultAmount what an ore block no patch placed starts with (ADR-0002)
     * @param dimension the one dimension its Patches lie in (ADR 0007)
     * @param minDistance the distance from origin, in Overworld-equivalent blocks, below which none of its Patches generate
     * @param stageRatios fractions of a block's own initial amount at which each stage begins, richest first
     */
    public record Resource(String name, int defaultAmount, PatchDimension dimension, int minDistance,
            List<Double> stageRatios, Patch patch) {
    }

    /** A resource's autoplace arguments, and the range a spot's size factor is drawn from. */
    public record Patch(double baseDensity, double baseSpotsPerKm2, double spotSizeMinimum,
            double spotSizeMaximum, double rqFactor, double blobAmplitudeMultiplier,
            double blobAmplitudeMaximumDistance) {

        public double meanSpotSize() {
            return (spotSizeMinimum + spotSizeMaximum) / 2;
        }
    }

    /** A spot's radius cap and its ragged edge: {@code (octaves - offset) * blob amplitude}. */
    public record Edge(double radiusCap, List<Octave> octaves, double offset) {
    }

    public record Octave(double inputScale, double outputScale) {
    }

    /** Density by distance: nothing inside the placement radius, fading in, doubling by distance. */
    public record DensityLaw(double placementRadius, double fadeIn, double doubleDensityDistance) {

        public double at(double baseDensity, double distance) {
            double fade = clamp((distance - placementRadius) / fadeIn);
            double doubling = 1 + clamp((distance - fadeIn) / doubleDensityDistance);
            return baseDensity * fade * doubling;
        }

        private static double clamp(double value) {
            return Math.max(0, Math.min(1, value));
        }
    }

    /** Richness by distance, {@code max((offset + distance) / divisor, 1)}: flat near origin, uncapped beyond. */
    public record DistanceLaw(int offset, int divisor) {

        public double richnessAt(double distance) {
            return Math.max((offset + distance) / divisor, 1.0);
        }

        public int flatWithin() {
            return divisor - offset;
        }
    }
}
