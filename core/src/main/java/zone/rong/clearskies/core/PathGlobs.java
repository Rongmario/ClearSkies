/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Include/exclude globs matched against documented root-relative paths (slash-separated).
 */
public final class PathGlobs {

    private PathGlobs() { }

    public static boolean allowed(Path file, Path root, List<String> includes, List<String> excludes) {
        List<String> candidates = candidates(file, root);
        for (String exclude : excludes) {
            if (anyMatch(exclude, candidates)) {
                return false;
            }
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (String include : includes) {
            if (anyMatch(include, candidates)) {
                return true;
            }
        }
        return false;
    }

    static List<String> candidates(Path file, Path root) {
        List<String> candidates = new ArrayList<>();
        Path absoluteFile = file.toAbsolutePath().normalize();
        add(candidates, absoluteFile.toString().replace('\\', '/'));
        add(candidates, file.getFileName() == null ? null : file.getFileName().toString());
        if (root != null) {
            Path absoluteRoot = root.toAbsolutePath().normalize();
            if (absoluteFile.startsWith(absoluteRoot)) {
                add(candidates, absoluteRoot.relativize(absoluteFile).toString().replace('\\', '/'));
            }
        }
        Path current = absoluteFile.getParent();
        while (current != null) {
            add(candidates, current.relativize(absoluteFile).toString().replace('\\', '/'));
            current = current.getParent();
        }
        return candidates;
    }

    private static void add(List<String> candidates, String value) {
        if (value != null && !value.isEmpty() && !candidates.contains(value)) {
            candidates.add(value);
        }
    }

    private static boolean anyMatch(String glob, List<String> candidates) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        for (String candidate : candidates) {
            if (matcher.matches(Path.of(candidate))) {
                return true;
            }
        }
        return false;
    }

}
