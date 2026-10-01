/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Resolves javac-style classpath entries.
 *
 * <p>{@code directory/*} expands to the sorted JAR files in that directory, as javac does. Missing
 * explicit (non-wildcard) entries are errors rather than silent drops.
 */
final class ClasspathEntries {

    private ClasspathEntries() { }

    record Resolution(List<Path> paths, List<String> errors) { }

    static Resolution classpath(List<Path> entries) {
        List<Path> resolved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path entry : entries) {
            if (entry == null) {
                continue;
            }
            if (isWildcard(entry)) {
                Path directory = parent(entry);
                if (!Files.isDirectory(directory)) {
                    errors.add("classpath directory does not exist: " + directory);
                    continue;
                }
                resolved.addAll(archivesIn(directory));
                continue;
            }
            if (!Files.exists(entry)) {
                errors.add("missing classpath entry: " + entry);
                continue;
            }
            resolved.add(entry);
        }
        return new Resolution(List.copyOf(resolved), List.copyOf(errors));
    }

    static Resolution sourceRoots(List<Path> roots) {
        List<Path> resolved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            if (isWildcard(root)) {
                errors.add("source-path wildcards are not supported: " + root);
                continue;
            }
            if (!Files.exists(root)) {
                errors.add("missing source-path entry: " + root);
                continue;
            }
            resolved.add(root);
        }
        return new Resolution(List.copyOf(resolved), List.copyOf(errors));
    }

    static boolean isWildcard(Path path) {
        Path name = path.getFileName();
        return name != null && name.toString().equals("*");
    }

    private static Path parent(Path path) {
        Path parent = path.getParent();
        return parent == null ? Path.of(".") : parent;
    }

    private static List<Path> archivesIn(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                .filter(ClasspathEntries::isArchive)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read classpath directory " + directory, e);
        }
    }

    private static boolean isArchive(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

}
