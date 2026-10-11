// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

/**
 * Fieldworks plays alone on Groundworks (5thlayer/factoryworks ADR-0127, #20): nothing it ships names
 * another suite mod's id or puts data in its namespace. Groundworks, Minecraft, {@code c} and NeoForge
 * are allowed. A structure's {@code .nbt} is read unzipped, so the block ids saved in it are checked too.
 */
class SuiteNamespaceTest {

    private static final String OTHER_MODS = "beltworks|pipeworks|wireworks|craftworks|factoryworks";
    private static final Pattern ID = Pattern.compile("\\b(" + OTHER_MODS + "):[a-z0-9_./-]+");
    private static final Pattern DATA_PATH = Pattern.compile("(^|/)(data|assets)/(" + OTHER_MODS + ")/");

    @Test
    void nothingShippedNamesAnotherSuiteModsNamespace() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/resources"), Path.of("src/main/java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String path = root.relativize(file).toString().replace('\\', '/');
                    if (DATA_PATH.matcher(path).find()) {
                        found.add(root.resolve(path) + ": a path in another suite mod's namespace");
                    }
                    var id = ID.matcher(text(file));
                    while (id.find()) {
                        found.add(root.resolve(path) + ": " + id.group());
                    }
                }
            }
        }
        assertEquals(List.of(), found);
    }

    private static String text(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (file.toString().endsWith(".nbt")) {
            try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
                bytes = in.readAllBytes();
            }
        }
        // Latin-1 maps every byte to a char, so a binary file still reads and an id in it still matches.
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
