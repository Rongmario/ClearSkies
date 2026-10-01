/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathGlobsTest {

    @Test
    void rootRelativeIncludeMatchesFromTheDocumentedRoot(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("src/main/java/sample/Sample.java");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "class Sample {}\n");
        assertThat(PathGlobs.allowed(file, List.of(temp), List.of("src/main/java/**/*.java"), List.of())).isTrue();
        assertThat(PathGlobs.allowed(file, List.of(temp), List.of("src/test/java/**/*.java"), List.of())).isFalse();
    }

    @Test
    void excludeWins(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("src/main/java/sample/Sample.java");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "class Sample {}\n");
        assertThat(PathGlobs.allowed(file, List.of(temp), List.of("src/main/java/**/*.java"), List.of("**/Sample.java"))).isFalse();
    }

    @Test
    void patternsDoNotMatchAnInnerSuffixOfThePath(@TempDir Path temp) {
        Path file = temp.resolve("src/com/x/build/B.java");
        assertThat(PathGlobs.allowed(file, List.of(temp), List.of(), List.of("build/**"))).isTrue();
        assertThat(PathGlobs.allowed(file, List.of(temp), List.of("B.java"), List.of())).isFalse();
        assertThat(PathGlobs.allowed(file, List.of(temp), List.of(), List.of("**/build/**"))).isFalse();
    }

    @Test
    void aFileOutsideEveryRootMatchesNoPattern(@TempDir Path temp) {
        Path file = temp.resolve("elsewhere/A.java");
        assertThat(PathGlobs.allowed(file, List.of(temp.resolve("src")), List.of("**"), List.of())).isFalse();
        assertThat(PathGlobs.allowed(file, List.of(temp.resolve("src")), List.of(), List.of("**"))).isTrue();
    }

}
