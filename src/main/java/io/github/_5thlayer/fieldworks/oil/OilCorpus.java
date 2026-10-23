// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.oil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.github._5thlayer.fieldworks.ore.OreCorpus;
import io.github._5thlayer.fieldworks.ore.PatchDimension;
import io.github._5thlayer.fieldworks.ore.PatchLaw;

/** Crude oil's frozen numbers from {@code fieldworks/oil/crude.json}, beside the law it shares with the ores. */
public final class OilCorpus {

    private static final String PATH = "/fieldworks/oil/crude.json";

    private static final OilCorpus INSTANCE = load(PATH);

    private final int amountPerCycle;
    private final long normal;
    private final long minimum;
    private final long depletion;
    private final double randomProbability;
    private final double richnessDivisor;
    private final double additionalRichness;
    private final double collisionWidth;
    private final PatchLaw law;

    private OilCorpus(JsonObject crude, PatchLaw law) {
        this.amountPerCycle = crude.get("amount_per_cycle").getAsInt();
        this.normal = crude.get("normal").getAsLong();
        this.minimum = crude.get("minimum").getAsLong();
        this.depletion = crude.get("infinite_depletion_amount").getAsLong();
        this.randomProbability = crude.get("random_probability").getAsDouble();
        this.richnessDivisor = crude.get("richness_divisor").getAsDouble();
        this.additionalRichness = crude.get("additional_richness").getAsDouble();
        this.collisionWidth = crude.get("collision_width").getAsDouble();
        this.law = law;
    }

    public static OilCorpus get() {
        return INSTANCE;
    }

    static OilCorpus load(String path) {
        try (InputStream stream = OilCorpus.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("no " + path + " on the classpath");
            }
            JsonObject crude = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            OreCorpus ores = OreCorpus.get();
            PatchLaw law = new PatchLaw(OreCorpus.patch(crude.getAsJsonObject("patch")),
                    ores.densityLaw(), ores.distanceLaw(), ores.edge(), PatchDimension.OVERWORLD, 0);
            return new OilCorpus(crude, law);
        } catch (IOException broken) {
            throw new IllegalStateException("could not read " + path, broken);
        }
    }

    /** Crude per cycle at a well holding {@link #normal()}. */
    public int amountPerCycle() {
        return amountPerCycle;
    }

    /** The amount that yields 100%. */
    public long normal() {
        return normal;
    }

    /** The lowest a well's amount decays to. */
    public long minimum() {
        return minimum;
    }

    /** What one cycle takes off a well. */
    public long depletion() {
        return depletion;
    }

    /** The fraction of a field's columns a draw leaves positive: 1/48. */
    public double randomProbability() {
        return randomProbability;
    }

    public double richnessDivisor() {
        return richnessDivisor;
    }

    public double additionalRichness() {
        return additionalRichness;
    }

    public double collisionWidth() {
        return collisionWidth;
    }

    public PatchLaw law() {
        return law;
    }
}
