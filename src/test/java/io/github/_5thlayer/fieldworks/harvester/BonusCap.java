// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github._5thlayer.fieldworks.ore.OreResource;

/**
 * The bonus cap over the Harvest Recipes a data root holds, read as plain JSON so it runs on a plain JVM.
 *
 * <p>A <em>bonus</em> is a Harvest Recipe that spends a Drilling Fluid on a source that keeps its Default recipe and
 * yields more of the item the Default does: it may yield up to {@link #CAP} times the Default. A recipe that spends no
 * fluid may yield no more than the Default. Counts are per operation: a Default draw of an ore block yields one
 * of the ore's drop. Only Fieldworks' ore blocks have a Default known here; a source whose Default any recipe in the
 * root replaces is not judged, and neither is a harvestable block, whose Default is its loot, which only a loot table
 * evaluated in game could tell. Block tags are read from the root's own tag files.
 *
 * <p>A well recipe is crude's floor role, not a bonus: a crude result that restores a share of the well's starting
 * yield may restore at most the whole of it, and one in fixed mB is not judged, a well's Default yield depending on
 * the well.
 */
final class BonusCap {

    /**
     * The most a bonus may yield, as a multiple of its source's Default recipe. It caps Fieldworks' Sink
     * tier (5thlayer/fieldworks#1); a pack's own datapack is never checked or clamped against it.
     */
    static final int CAP = 2;

    private static final int DEFAULT_DRAW = 1;

    private BonusCap() {
    }

    /** One line per recipe under {@code root/data} that yields more than its cap allows. */
    static List<String> violations(Path root) {
        Path recipes = root.resolve("data/fieldworks/recipe");
        List<String> violations = new ArrayList<>();
        try {
            Map<Path, JsonObject> harvesting = new LinkedHashMap<>();
            try (Stream<Path> files = Files.walk(recipes)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                    JsonObject recipe = read(file);
                    if ("fieldworks:harvesting".equals(recipe.get("type").getAsString())) {
                        harvesting.put(file, recipe);
                    }
                }
            }
            Set<String> replaced = new HashSet<>();
            for (JsonObject recipe : harvesting.values()) {
                if (recipe.has("replaces_default") && recipe.get("replaces_default").getAsBoolean()) {
                    replaced.addAll(blocks(root, recipe.get("source")));
                }
            }
            for (Map.Entry<Path, JsonObject> entry : harvesting.entrySet()) {
                String file = root.relativize(entry.getKey()).toString();
                JsonObject recipe = entry.getValue();
                JsonElement crude = recipe.get("result").isJsonObject() ? recipe.get("result").getAsJsonObject().get("crude_oil") : null;
                if (crude != null) {
                    if (crude.isJsonObject() && crude.getAsJsonObject().get("restores").getAsDouble() > 1) {
                        violations.add(file + " restores " + crude.getAsJsonObject().get("restores").getAsDouble()
                                + " of its well's starting yield, over the whole of it");
                    }
                    continue;
                }
                String item = itemOf(recipe.get("result"));
                int count = countOf(recipe.get("result"));
                int cap = recipe.has("drilling_fluid") ? CAP : 1;
                for (String block : blocks(root, recipe.get("source"))) {
                    OreResource ore = oreOf(block);
                    if (ore != null && !replaced.contains(block) && ore.drop().equals(item) && count > cap * DEFAULT_DRAW) {
                        violations.add(file + " yields " + count + " " + item + " per operation from " + block + ", over "
                                + cap + " times its Default recipe's " + DEFAULT_DRAW
                                + (recipe.has("drilling_fluid") ? "" : " with no Drilling Fluid spent"));
                    }
                }
            }
        } catch (IOException broken) {
            throw new IllegalStateException("could not read " + recipes, broken);
        }
        return violations;
    }

    private static String itemOf(JsonElement result) {
        return result.isJsonObject() ? result.getAsJsonObject().get("id").getAsString() : result.getAsString();
    }

    private static int countOf(JsonElement result) {
        return result.isJsonObject() && result.getAsJsonObject().has("count")
                ? result.getAsJsonObject().get("count").getAsInt() : 1;
    }

    /** The ore a Fieldworks ore block id names, or null when it is not one. */
    private static OreResource oreOf(String block) {
        for (OreResource ore : OreResource.values()) {
            if (block.equals("fieldworks:" + ore.blockName())) {
                return ore;
            }
        }
        return null;
    }

    /** The block ids a source names: one id, a list of them, or a {@code #tag} read from the root's tag files. */
    private static Set<String> blocks(Path root, JsonElement source) throws IOException {
        Set<String> blocks = new LinkedHashSet<>();
        if (source.isJsonArray()) {
            for (JsonElement entry : source.getAsJsonArray()) {
                blocks.addAll(blocks(root, entry));
            }
        } else if (source.getAsString().startsWith("#")) {
            blocks.addAll(tag(root, source.getAsString().substring(1)));
        } else {
            blocks.add(source.getAsString());
        }
        return blocks;
    }

    /** The block ids a tag holds, following tags it holds; empty when the root has no such tag file. */
    private static Set<String> tag(Path root, String id) throws IOException {
        String[] parts = id.split(":", 2);
        Path file = root.resolve("data/" + parts[0] + "/tags/block/" + parts[1] + ".json");
        Set<String> blocks = new LinkedHashSet<>();
        try {
            for (JsonElement entry : read(file).getAsJsonArray("values")) {
                String value = entry.getAsString();
                if (value.startsWith("#")) {
                    blocks.addAll(tag(root, value.substring(1)));
                } else {
                    blocks.add(value);
                }
            }
        } catch (NoSuchFileException absent) {
            return Set.of();
        }
        return blocks;
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return new Gson().fromJson(reader, JsonElement.class).getAsJsonObject();
        }
    }
}
