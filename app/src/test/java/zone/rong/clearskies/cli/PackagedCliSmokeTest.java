/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the installed CLI launcher, not {@link CliRunner} in-process.
 *
 * <p>The launcher is the distribution entry point {@code bin/clearskies}. Two invocations of the same
 * arguments must agree, which is the smoke that a packaged build is not a one-shot accident.
 */
class PackagedCliSmokeTest {

    private static Path launcher() {
        String path = System.getProperty("clearskies.launcher");
        assertThat(path != null && !path.isBlank()).as("clearskies.launcher system property must point at bin/clearskies").isTrue();
        Path file = Path.of(path);
        assertThat(Files.isRegularFile(file)).as(() -> "missing launcher: " + file).isTrue();
        assertThat(Files.isExecutable(file)).as(() -> "launcher is not executable: " + file).isTrue();
        return file;
    }

    @Test
    void helpRunsTwiceWithTheSameText() throws Exception {
        Run first = run(List.of("--help"));
        Run second = run(List.of("--help"));

        assertThat(first.exitCode).as(first.err).isEqualTo(0);
        assertThat(second.exitCode).as(second.err).isEqualTo(0);
        assertThat(second.out).isEqualTo(first.out);
        assertThat(first.out.contains("--write")).as(first.out).isTrue();
        assertThat(first.out.contains("--classpath")).as(first.out).isTrue();
    }

    @Test
    void writeThenCheckIsAFixedPointOnASmallFile(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(source, "package sample;\n\nimport java.util.*;\n\nclass Sample { List x; }\n", StandardCharsets.UTF_8);

        Run first = run(List.of("--write", source.toString()));
        assertThat(first.exitCode).as(first.err).isEqualTo(0);
        String expanded = Files.readString(source, StandardCharsets.UTF_8);
        assertThat(expanded.contains("import java.util.List;")).as(expanded).isTrue();
        assertThat(expanded.contains("class Sample { List x; }")).as(expanded).isTrue();

        Run second = run(List.of("--write", source.toString()));
        assertThat(second.exitCode).as(second.err).isEqualTo(0);
        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo(expanded);

        Run check = run(List.of("--check", source.toString()));
        assertThat(check.exitCode).as(check.err).isEqualTo(0);
    }

    @Test
    void malformedFileLeavesOriginalBytesAndExitsNonZero(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Broken.java");
        String original = "package sample;\n\nimport java.util.*;\n\nclass Sample { List x;\n";
        Files.writeString(source, original, StandardCharsets.UTF_8);

        Run write = run(List.of("--write", source.toString()));
        assertThat(write.exitCode).as(write.err).isEqualTo(2);
        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    private static Run run(List<String> arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(launcher().toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Path outFile = Files.createTempFile("clearskies-cli-", ".out");
        Path errFile = Files.createTempFile("clearskies-cli-", ".err");
        try {
            builder.redirectOutput(outFile.toFile());
            builder.redirectError(errFile.toFile());
            Process process = builder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IllegalStateException("launcher timed out");
            }
            return new Run(process.exitValue(), Files.readString(outFile, StandardCharsets.UTF_8), Files.readString(errFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outFile);
            Files.deleteIfExists(errFile);
        }
    }

    private record Run(int exitCode, String out, String err) { }

}
