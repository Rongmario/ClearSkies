/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDiffTest {

    @Test
    void identicalTextsProduceNoDiff() {
        assertThat(UnifiedDiff.between("Foo.java", "class A {}\n", "class A {}\n")).isEqualTo("");
    }

    @Test
    void aChangedLineShowsInTheHunk() {
        String diff = UnifiedDiff.between("Foo.java", "import java.util.*;\n", "import java.util.List;\n");
        assertThat(diff.contains("--- Foo.java")).as(diff).isTrue();
        assertThat(diff.contains("-import java.util.*;")).as(diff).isTrue();
        assertThat(diff.contains("+import java.util.List;")).as(diff).isTrue();
    }

    @Test
    void missingTerminalNewlineIsReported() {
        String diff = UnifiedDiff.between("Foo.java", "class A {}\n", "class A {}");
        assertThat(diff.contains("class A {}")).as(diff).isTrue();
        assertThat(diff.contains("\\ No newline at end of file")).as(diff).isTrue();
    }

    @Test
    void largeSourcesDoNotUseAQuadraticMatrix() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        before.append("package sample;\n\nimport java.util.*;\n\nclass Sample {\n");
        after.append("package sample;\n\nimport java.util.List;\n\nclass Sample {\n");
        for (int i = 0; i < 8000; i++) {
            before.append("    int f").append(i).append(";\n");
            after.append("    int f").append(i).append(";\n");
        }
        before.append("    List x;\n}\n");
        after.append("    List x;\n}\n");
        String diff = UnifiedDiff.between("Large.java", before.toString(), after.toString());
        assertThat(diff.contains("-import java.util.*;")).as(diff).isTrue();
        assertThat(diff.contains("+import java.util.List;")).as(diff).isTrue();
        assertThat(diff.contains("-    int f0;")).as(diff).isFalse();
    }

}
