// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each Jade plugin loads on a dedicated server (#56), which has none of the client's packages. Jade instantiates
 * every plugin class on both sides, and a plugin class whose own methods hold tooltip code fails verification there,
 * since Jade's UI types extend the client's. Jade drops such a plugin whole, its server data providers too, and logs
 * no cause.
 *
 * <p>The build hands the main classes and their compile classpath over as {@code fieldworks.serverClasspath}, and a
 * loader that refuses the client's packages stands in for the server.
 */
class JadePluginServerLoadTest {

    /** What a dedicated server's jar leaves out. */
    private static final List<String> CLIENT_PACKAGES = List.of("net.minecraft.client.", "com.mojang.blaze3d.", "com.mojang.realmsclient.");

    /** Every class Jade's annotation scan finds in the Module: the ones whose class file names {@code @WailaPlugin}. */
    static Stream<String> plugins() throws IOException {
        Path compat = classpath().filter(Files::isDirectory).findFirst().orElseThrow()
                .resolve(JadePluginServerLoadTest.class.getPackageName().replace('.', '/'));
        List<String> plugins;
        try (Stream<Path> classes = Files.list(compat)) {
            plugins = classes.filter(file -> file.toString().endsWith(".class") && isPlugin(file))
                    .map(file -> file.getFileName().toString().replace(".class", ""))
                    .toList();
        }
        assertFalse(plugins.isEmpty(), "no @WailaPlugin class in " + compat);
        return plugins.stream();
    }

    @ParameterizedTest
    @MethodSource("plugins")
    void loadsWithoutTheClient(String plugin) throws Exception {
        try (var server = new ServerLoader()) {
            Class.forName(JadePluginServerLoadTest.class.getPackageName() + "." + plugin, true, server);
        } catch (NoClassDefFoundError missing) {
            fail(plugin + " needs " + missing.getMessage() + " to load", missing);
        } catch (ExceptionInInitializerError ignored) {
            // Verified and linked: its static fields want a bootstrapped game, which this JVM isn't.
        }
    }

    private static boolean isPlugin(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains("Lsnownee/jade/api/WailaPlugin;");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The main classes first, then their compile classpath, as the build hands them over. */
    private static Stream<Path> classpath() {
        String classpath = System.getProperty("fieldworks.serverClasspath");
        assertNotNull(classpath, "the build sets fieldworks.serverClasspath");
        return Arrays.stream(classpath.split(File.pathSeparator)).map(Path::of);
    }

    /** Loads the Module and the game from the build's classpath, with none of the client's packages. */
    private static final class ServerLoader extends URLClassLoader {

        ServerLoader() {
            super(urls(), ClassLoader.getPlatformClassLoader());
        }

        private static URL[] urls() {
            return classpath().map(path -> {
                try {
                    return path.toUri().toURL();
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(path.toString(), e);
                }
            }).toArray(URL[]::new);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (CLIENT_PACKAGES.stream().anyMatch(name::startsWith)) {
                throw new ClassNotFoundException(name + " is client-only");
            }
            return super.findClass(name);
        }
    }
}
