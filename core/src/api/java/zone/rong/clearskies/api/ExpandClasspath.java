/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The compile classpath and source roots javac uses to attribute a file.
 *
 * <p>Classpath entries are jars and class directories. Source roots are trees of {@code .java}
 * files, so same-package types resolve without a prior compile of this source set. Platform modules
 * ({@code java.base} and friends) are always visible.
 */
public final class ExpandClasspath {

    private final List<Path> entries;
    private final List<Path> sourceRoots;

    private ExpandClasspath(List<Path> entries, List<Path> sourceRoots) {
        this.entries = List.copyOf(entries);
        this.sourceRoots = List.copyOf(sourceRoots);
    }

    /** A classpath of the given jars and class directories, with no extra source roots. */
    public static ExpandClasspath of(Collection<Path> entries) {
        return new ExpandClasspath(List.copyOf(entries), List.of());
    }

    /** Platform modules only. Enough to expand {@code java.*} / {@code javax.*} stars. */
    public static ExpandClasspath platformOnly() {
        return new ExpandClasspath(List.of(), List.of());
    }

    /** The same classpath with source roots for same-tree types. */
    public ExpandClasspath withSourceRoots(Collection<Path> roots) {
        Objects.requireNonNull(roots, "roots");
        List<Path> merged = new ArrayList<>(sourceRoots);
        merged.addAll(roots);
        return new ExpandClasspath(entries, merged);
    }

    /** Jars and class directories on the compile classpath. */
    public List<Path> entries() {
        return entries;
    }

    /** Source directories searched for compilation units besides the file being expanded. */
    public List<Path> sourceRoots() {
        return sourceRoots;
    }

}
