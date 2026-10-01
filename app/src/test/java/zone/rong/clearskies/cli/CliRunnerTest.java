/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CliRunnerTest {

    @Test
    void writeExpandsAStarAndCheckThenPasses(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(
            source,
            """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """,
            StandardCharsets.UTF_8
        );

        Run write = run(new String[] { "--write", source.toString() });
        assertThat(write.exit).as(write.err).isEqualTo(0);
        String expanded = Files.readString(source, StandardCharsets.UTF_8);
        assertThat(expanded.contains("import java.util.List;")).as(expanded).isTrue();
        assertThat(expanded.contains("class Sample { List x; }")).as(expanded).isTrue();
        assertThat(write.out.contains("expanded")).as(write.out).isTrue();

        Run check = run(new String[] { "--check", source.toString() });
        assertThat(check.exit).as(check.err).isEqualTo(0);
    }

    @Test
    void checkReportsAFileThatWouldChange(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(
            source,
            """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """,
            StandardCharsets.UTF_8
        );

        Run check = run(new String[] { "--check", source.toString() });
        assertThat(check.exit).as(check.err).isEqualTo(1);
        assertThat(check.out.contains("Sample.java")).as(check.out).isTrue();
        assertThat(Files.readString(source).contains("import java.util.*;")).isTrue();
    }

    @Test
    void helpPrintsUsage() {
        Run help = run(new String[] { "--help" });
        assertThat(help.exit).as(help.err).isEqualTo(0);
        assertThat(help.out.contains("clearskies")).as(help.out).isTrue();
        assertThat(help.out.contains("--write")).as(help.out).isTrue();
    }

    @Test
    void malformedFileFailsAndLeavesBytesUnchanged(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Broken.java");
        String original = "package sample;\n\nimport java.util.*;\n\nclass Sample { List x;\n";
        Files.writeString(source, original, StandardCharsets.UTF_8);

        Run write = run(new String[] { "--write", source.toString() });
        assertThat(write.exit).as(write.err).isEqualTo(2);
        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    void checkFailsOnIncompleteAttribution(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(
            source,
            """
                package sample;

                import com.missing.*;

                class Sample { Missing x; }
                """,
            StandardCharsets.UTF_8
        );

        Run check = run(new String[] { "--check", source.toString() });
        assertThat(check.exit).as(check.err).isEqualTo(1);
        assertThat(check.out.contains("Sample.java")).as(check.out).isTrue();
    }

    @Test
    void overlappingPathsAreDeduplicated(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(
            source,
            """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """,
            StandardCharsets.UTF_8
        );

        Run write = run(new String[] { "--write", source.toString(), source.toString() });
        assertThat(write.exit).as(write.err).isEqualTo(0);
        assertThat(write.out.split("expanded", -1).length - 1).as(write.out).isEqualTo(1);
    }

    @Test
    void includeGlobMatchesRootRelativePaths(@TempDir Path temp) throws Exception {
        Path nested = Files.createDirectories(temp.resolve("src/main/java/sample"));
        Path source = nested.resolve("Sample.java");
        Files.writeString(
            source,
            """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """,
            StandardCharsets.UTF_8
        );
        Path other = Files.createDirectories(temp.resolve("other")).resolve("Skip.java");
        Files.writeString(other, "class Skip {}\n", StandardCharsets.UTF_8);

        Run check = run(new String[] { "--check", "--include", "src/main/java/**/*.java", temp.toString() });
        assertThat(check.exit).as(check.err).isEqualTo(1);
        assertThat(check.out.contains("Sample.java")).as(check.out).isTrue();
        assertThat(check.out.contains("Skip.java")).as(check.out).isFalse();
    }

    @Test
    void spaceInFileNameWrites(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("My File.java");
        Files.writeString(
            source,
            """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """,
            StandardCharsets.UTF_8
        );

        Run write = run(new String[] { "--write", source.toString() });
        assertThat(write.exit).as(write.err).isEqualTo(0);
        String expanded = Files.readString(source, StandardCharsets.UTF_8);
        assertThat(expanded.contains("import java.util.List;")).as(expanded).isTrue();
    }

    @Test
    void missingClasspathEntryExitsWithError(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(
            source,
            """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """,
            StandardCharsets.UTF_8
        );

        Run write = run(new String[] { "--write", "-cp", temp.resolve("missing.jar").toString(), source.toString() });
        assertThat(write.exit).as(write.err).isEqualTo(2);
        assertThat(Files.readString(source).contains("import java.util.*;")).isTrue();
    }

    @Test
    void standardInputWithNothingToExpandEchoesTheSource() {
        String source = "import java.util.List;\n\nclass Sample { List x; }\n";
        Run run = run(new String[] { "--stdin" }, source.getBytes(StandardCharsets.UTF_8));
        assertThat(run.exit).as(run.err).isEqualTo(0);
        assertThat(run.out).isEqualTo(source);
    }

    @Test
    void standardInputWithUnresolvedTypesEchoesTheSource() {
        String source = "import java.util.*;\n\nclass Sample { Missing x; }\n";
        Run run = run(new String[] { "--stdin" }, source.getBytes(StandardCharsets.UTF_8));
        assertThat(run.exit).as(run.err).isEqualTo(0);
        assertThat(run.out).isEqualTo(source);
    }

    @Test
    void checkReportsPathsRelativeToTheWorkingDirectory() throws Exception {
        Path directory = Files.createDirectories(Path.of("build", "tmp", "relativeCheck"));
        Path source = directory.resolve("Sample.java");
        Files.writeString(source, "import java.util.*;\n\nclass Sample { List x; }\n", StandardCharsets.UTF_8);
        Run check = run(new String[] { "--check", directory.toString() });
        assertThat(check.exit).as(check.err).isEqualTo(1);
        assertThat(check.out.strip()).isEqualTo(source.toString());
    }

    @Test
    void standardInputInTheWrongEncodingFailsInsteadOfReplacingBytes() {
        byte[] latin1 = "import java.util.*;\nclass Sample { List x; String s = \"café\"; }\n".getBytes(StandardCharsets.ISO_8859_1);
        Run run = run(new String[] { "--stdin" }, latin1);
        assertThat(run.exit).isEqualTo(2);
        assertThat(run.out).isEmpty();
        assertThat(run.err).contains("not valid UTF-8");
    }

    @Test
    void patternsThatMatchNothingAreAnError(@TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("Sample.java"), "class Sample {}\n", StandardCharsets.UTF_8);
        Run check = run(new String[] { "--check", "--include", "nope/**", temp.toString() });
        assertThat(check.exit).isEqualTo(2);
        assertThat(check.err).contains("no Java sources matched");
    }

    @Test
    void argumentFileSuppliesOptionsAndKeepLeavesAStarAlone(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        String original = """
            package sample;

            import java.util.*;
            import java.util.concurrent.*;
            import static java.lang.Math.*;

            class Sample { List x; Callable<Integer> c = () -> abs(-1); }
            """;
        Files.writeString(source, original, StandardCharsets.UTF_8);
        Path arguments = temp.resolve("args");
        Files.writeString(arguments, "--write\n--keep java.util.concurrent\n--no-static\n\"" + source + "\"\n", StandardCharsets.UTF_8);

        Run write = run(new String[] { "@" + arguments });
        assertThat(write.exit).as(write.err).isEqualTo(0);
        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo(original.replace("import java.util.*;", "import java.util.List;"));
        Run check = run(new String[] { "--check", "--keep", "java.util.concurrent", "--no-static", source.toString() });
        assertThat(check.exit).as(check.err + check.out).isEqualTo(0);
    }

    private static Run run(String[] arguments) {
        return run(arguments, new byte[0]);
    }

    private static Run run(String[] arguments, byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOptions options = CliOptions.parse(arguments);
        int exit = new CliRunner(
            options,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8),
            new ByteArrayInputStream(input)
        ).run();
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exit, String out, String err) { }

}
