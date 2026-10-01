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
 * Include/exclude globs matched against a file's path relative to each given root that contains
 * it, slash-separated. A file outside every root matches no pattern.
 */
public final class PathGlobs {

    private PathGlobs() { }

    public static boolean allowed(Path file, List<Path> roots, List<String> includes, List<String> excludes) {
        List<String> candidates = candidates(file, roots);
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

    static List<String> candidates(Path file, List<Path> roots) {
        Path absoluteFile = file.toAbsolutePath().normalize();
        List<String> candidates = new ArrayList<>();
        for (Path root : roots) {
            Path absoluteRoot = root.toAbsolutePath().normalize();
            if (!absoluteFile.startsWith(absoluteRoot)) {
                continue;
            }
            String relative = absoluteRoot.relativize(absoluteFile).toString().replace('\\', '/');
            if (!relative.isEmpty() && !candidates.contains(relative)) {
                candidates.add(relative);
            }
        }
        return candidates;
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
