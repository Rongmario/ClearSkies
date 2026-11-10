/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands star imports in Java source text.
 *
 * <p>Implementations are immutable and safe to share across threads, so one expander can be reused
 * for a whole source set and handed to a thread pool. File-manager state stays inside the
 * implementation.
 */
public interface StarExpander {

    /** Expands one request. */
    ExpandResult expand(ExpandRequest request);

    /**
     * Expands many requests and returns their results in request order. Attribution is shared between
     * the requests, so this is far faster than expanding the files of a source set one by one.
     */
    default List<ExpandResult> expandAll(List<ExpandRequest> requests) {
        List<ExpandResult> results = new ArrayList<>(requests.size());
        for (ExpandRequest request : requests) {
            results.add(expand(request));
        }
        return results;
    }

    /** The classpath this expander attributes against. */
    ExpandClasspath classpath();

    /** The {@code --release} this expander passes to javac. */
    LanguageLevel languageLevel();

    /** Convenience for expanding a string with no name. */
    default String expand(String source) {
        return expand(ExpandRequest.of(source)).text();
    }

}
