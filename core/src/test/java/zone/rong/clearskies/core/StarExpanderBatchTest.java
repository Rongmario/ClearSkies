/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.StarExpander;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code expandAll} has to give each file the result it gets from {@code expand} alone. */
class StarExpanderBatchTest {

    private static final ExpandRequest USES_LIST = request(
        "a/Sample.java",
        """
            package a;

            import java.util.*;

            class Sample { List<String> x; }
            """
    );

    private static final ExpandRequest SAME_FILE_NAME = request(
        "b/Sample.java",
        """
            package b;

            import java.io.*;
            import java.util.*;

            class Sample { Map<String, File> x; }
            """
    );

    private static final ExpandRequest NO_STAR = request(
        "a/Plain.java",
        """
            package a;

            import java.util.List;

            class Plain { List<String> x; }
            """
    );

    private static final ExpandRequest UNUSED_STAR = request(
        "a/Unused.java",
        """
            package a;

            import java.util.*;

            class Unused { }
            """
    );

    private static final ExpandRequest UNRESOLVED = request(
        "a/Unresolved.java",
        """
            package a;

            import java.util.*;

            class Unresolved { List<Missing> x; }
            """
    );

    private static final ExpandRequest AMBIGUOUS = request(
        "a/Ambiguous.java",
        """
            package a;

            import java.awt.*;
            import java.util.*;

            class Ambiguous { List x; }
            """
    );

    private static final ExpandRequest BROKEN = request(
        "a/Broken.java",
        """
            package a;

            import java.util.*;

            class Broken { List x;
            """
    );

    private static ExpandRequest request(String name, String source) {
        return ExpandRequest.of(source).withName(name);
    }

    private static void assertMatchesOneByOne(ExpandRequest... requests) {
        StarExpander expander = ClearSkies.defaultExpander();
        List<ExpandResult> batch = expander.expandAll(List.of(requests));
        assertThat(batch.size()).isEqualTo(requests.length);
        List<ExpandResult.Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < requests.length; i++) {
            ExpandResult alone = expander.expand(requests[i]);
            assertThat(batch.get(i).outcome()).as(requests[i].name()).isEqualTo(alone.outcome());
            assertThat(batch.get(i).text()).as(requests[i].name()).isEqualTo(alone.text());
            assertThat(batch.get(i).diagnostics()).as(requests[i].name()).isEqualTo(alone.diagnostics());
            outcomes.add(alone.outcome());
        }
        assertThat(outcomes.contains(ExpandResult.Outcome.EXPANDED)).as(outcomes.toString()).isTrue();
    }

    @Test
    void cleanFilesExpandTogether() {
        assertMatchesOneByOne(USES_LIST, NO_STAR, SAME_FILE_NAME, UNUSED_STAR);
    }

    @Test
    void anUnresolvedTypeElsewhereDoesNotKeepAnUnusedStar() {
        assertMatchesOneByOne(UNUSED_STAR, UNRESOLVED, USES_LIST);
    }

    @Test
    void anAmbiguousNameFreezesOnlyItsOwnFile() {
        assertMatchesOneByOne(AMBIGUOUS, USES_LIST, SAME_FILE_NAME);
    }

    @Test
    void aBrokenFileFailsAlone() {
        assertMatchesOneByOne(USES_LIST, BROKEN, UNUSED_STAR);
    }

    @Test
    void anEmptyBatchIsEmpty() {
        assertThat(ClearSkies.defaultExpander().expandAll(List.of()).isEmpty()).isTrue();
    }

}
