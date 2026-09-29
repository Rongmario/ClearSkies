/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.api;

import java.util.Objects;

/**
 * One unit of work: the source text and a display name for diagnostics, usually a file path.
 */
public final class ExpandRequest {

    private final String source;
    private final String name;

    private ExpandRequest(String source, String name) {
        this.source = Objects.requireNonNull(source, "source");
        this.name = Objects.requireNonNull(name, "name");
    }

    /** A request to expand the whole of {@code source}. */
    public static ExpandRequest of(String source) {
        return new ExpandRequest(source, "<source>");
    }

    /** The same request with a name used in diagnostics, usually a file path. */
    public ExpandRequest withName(String name) {
        return new ExpandRequest(source, name);
    }

    public String source() {
        return source;
    }

    public String name() {
        return name;
    }

}
