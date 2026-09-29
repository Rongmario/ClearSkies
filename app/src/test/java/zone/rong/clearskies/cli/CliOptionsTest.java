/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import zone.rong.clearskies.api.LanguageLevel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliOptionsTest {

    @Test
    void checkingIsTheDefaultMode() {
        CliOptions options = CliOptions.parse(new String[] { "src" });
        assertThat(options.mode()).isEqualTo(CliOptions.Mode.CHECK);
        assertThat(options.paths()).isEqualTo(List.of(Path.of("src")));
    }

    @Test
    void modesAndResolutionFlagsParse() {
        CliOptions options = CliOptions.parse(
            new String[] {
                "--write",
                "--classpath",
                "lib/a.jar" + java.io.File.pathSeparator + "lib/b.jar",
                "--source-path",
                "src",
                "--release",
                "21",
                "-j",
                "3",
                "--include",
                "**/*.java",
                "--exclude",
                "**/generated/**",
                "src",
                "test"
            }
        );

        assertThat(options.mode()).isEqualTo(CliOptions.Mode.WRITE);
        assertThat(options.languageLevel()).isEqualTo(LanguageLevel.JAVA_21);
        assertThat(options.parallelism()).isEqualTo(3);
        assertThat(options.includes()).isEqualTo(List.of("**/*.java"));
        assertThat(options.excludes()).isEqualTo(List.of("**/generated/**"));
        assertThat(options.paths().size()).isEqualTo(2);
        assertThat(options.classpath().size()).isEqualTo(2);
        assertThat(options.sourcePath()).isEqualTo(List.of(Path.of("src")));
    }

    @Test
    void stdinDefaultsToWritingTheExpandedSourceOut() {
        CliOptions options = CliOptions.parse(new String[] { "--stdin-name", "Foo.java" });
        assertThat(options.readStdin()).isTrue();
        assertThat(options.stdinName()).isEqualTo("Foo.java");
        assertThat(options.mode()).isEqualTo(CliOptions.Mode.WRITE);
    }

    @Test
    void malformedCommandLinesAreRejected() {
        assertThatThrownBy(() -> CliOptions.parse(new String[] {})).isInstanceOf(CliOptions.CliException.class);
        assertThatThrownBy(() -> CliOptions.parse(new String[] { "--nope", "src" })).isInstanceOf(CliOptions.CliException.class);
        assertThatThrownBy(() -> CliOptions.parse(new String[] { "--release" })).isInstanceOf(CliOptions.CliException.class);
    }

    @Test
    void helpNeedsNoPaths() {
        CliOptions options = CliOptions.parse(new String[] { "--help" });
        assertThat(options.mode()).isEqualTo(CliOptions.Mode.HELP);
        assertThat(options.paths().isEmpty()).isTrue();
        assertThat(options.readStdin()).isFalse();
    }

}
