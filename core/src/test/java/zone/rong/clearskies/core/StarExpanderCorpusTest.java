/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.StarExpander;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixed corpus of shapes called out in the review: unicode, comments, malformed files,
 * package-info, javadocs, unusual paths, and ambiguous imports.
 */
class StarExpanderCorpusTest {

    private static ExpandResult expand(String source, String name) {
        return ClearSkies.defaultExpander().expand(ExpandRequest.of(source).withName(name));
    }

    @ParameterizedTest
    @CsvSource({ "Sample.java", "My File.java", "weird-name.java", "package-info.java" })
    void unusualFileNamesDoNotCrashTheExpander(String name) {
        String source = "package sample;\n\nimport java.util.*;\n\nclass Sample { List x; }\n";
        if (name.equals("package-info.java")) {
            source = "@Deprecated\npackage sample;\n\nimport java.util.*;\n";
        }
        ExpandResult result = expand(source, name);
        assertThat(result.outcome() == ExpandResult.Outcome.FAILED).as(result.diagnostics().toString()).isFalse();
        assertThat(source.equals(result.text()) || result.text().contains("import ") || result.text().contains("package ")).as(result.text()).isTrue();
    }

    @Test
    void unicodeEscapeInTheImportKeywordIsAttributed() {
        String source = "package sample;\n\n\\u0069mport java.util.*;\n\nclass Sample { List x; }\n";
        ExpandResult result = expand(source, "Sample.java");
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text().contains("java.util.List")).as(result.text()).isTrue();
    }

    @Test
    void commentedStarIsNotTreatedAsARewriteTarget() {
        String source = """
                package sample;

                // import java.util.*;
                import java.util.List;

                class Sample { List x; }
                """;
        ExpandResult result = expand(source, "Sample.java");
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.UNCHANGED);
        assertThat(result.text()).isEqualTo(source);
    }

    @Test
    void malformedFileKeepsOriginalBytes() {
        String source = "import java.util.*; class Sample { List x;";
        ExpandResult result = expand(source, "Broken.java");
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.FAILED);
        assertThat(result.text()).isEqualTo(source);
    }

    @Test
    void packageInfoStarIsExpanded(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(
            root.resolve("foo/Anno.java"),
            "package foo;\nimport java.lang.annotation.Retention;\nimport java.lang.annotation.RetentionPolicy;\n" + "@Retention(RetentionPolicy.RUNTIME)\npublic @interface Anno {}\n"
        );
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = "@Anno\npackage sample;\n\nimport foo.*;\n";
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("package-info.java"));
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text().contains("import foo.Anno;")).as(result.text()).isTrue();
    }

    @Test
    void javadocSeeTagKeepsTheStarOwner() {
        String source = """
                package sample;

                import java.util.*;

                /** @see Map */
                class Sample {}
                """;
        ExpandResult result = expand(source, "Sample.java");
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text().contains("import java.util.Map;")).as(result.text()).isTrue();
    }

    @Test
    void ambiguousDistinctOwnersStayIncomplete(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.createDirectories(root.resolve("bar"));
        Files.writeString(root.resolve("foo/List.java"), "package foo;\npublic class List {}\n");
        Files.writeString(root.resolve("bar/List.java"), "package bar;\npublic class List {}\n");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = "package sample;\n\nimport foo.*;\nimport bar.*;\n\nclass Sample { List x; }\n";
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.INCOMPLETE);
        assertThat(result.text()).isEqualTo(source);
    }

}
