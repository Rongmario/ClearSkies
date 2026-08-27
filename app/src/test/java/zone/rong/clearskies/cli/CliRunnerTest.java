package zone.rong.clearskies.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                StandardCharsets.UTF_8);

        Run write = run(new String[] {"--write", source.toString()});
        assertEquals(0, write.exit, write.err);
        String expanded = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(expanded.contains("import java.util.List;"), expanded);
        assertTrue(expanded.contains("class Sample { List x; }"), expanded);
        assertTrue(write.out.contains("expanded"), write.out);

        Run check = run(new String[] {"--check", source.toString()});
        assertEquals(0, check.exit, check.err);
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
                StandardCharsets.UTF_8);

        Run check = run(new String[] {"--check", source.toString()});
        assertEquals(1, check.exit, check.err);
        assertTrue(check.out.contains("Sample.java"), check.out);
        assertTrue(Files.readString(source).contains("import java.util.*;"));
    }

    @Test
    void helpPrintsUsage() {
        Run help = run(new String[] {"--help"});
        assertEquals(0, help.exit, help.err);
        assertTrue(help.out.contains("clearskies"), help.out);
        assertTrue(help.out.contains("--write"), help.out);
    }

    @Test
    void malformedFileFailsAndLeavesBytesUnchanged(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Broken.java");
        String original = "package sample;\n\nimport java.util.*;\n\nclass Sample { List x;\n";
        Files.writeString(source, original, StandardCharsets.UTF_8);

        Run write = run(new String[] {"--write", source.toString()});
        assertEquals(2, write.exit, write.err);
        assertEquals(original, Files.readString(source, StandardCharsets.UTF_8));
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
                StandardCharsets.UTF_8);

        Run check = run(new String[] {"--check", source.toString()});
        assertEquals(1, check.exit, check.err);
        assertTrue(check.out.contains("Sample.java"), check.out);
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
                StandardCharsets.UTF_8);

        Run write = run(new String[] {"--write", source.toString(), source.toString()});
        assertEquals(0, write.exit, write.err);
        assertEquals(1, write.out.split("expanded", -1).length - 1, write.out);
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
                StandardCharsets.UTF_8);
        Path other = Files.createDirectories(temp.resolve("other")).resolve("Skip.java");
        Files.writeString(other, "class Skip {}\n", StandardCharsets.UTF_8);

        Run check = run(
                new String[] {
                    "--check",
                    "--include",
                    "src/main/java/**/*.java",
                    temp.toString()
                });
        assertEquals(1, check.exit, check.err);
        assertTrue(check.out.contains("Sample.java"), check.out);
        assertFalse(check.out.contains("Skip.java"), check.out);
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
                StandardCharsets.UTF_8);

        Run write = run(new String[] {"--write", source.toString()});
        assertEquals(0, write.exit, write.err);
        String expanded = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(expanded.contains("import java.util.List;"), expanded);
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
                StandardCharsets.UTF_8);

        Run write = run(new String[] {"--write", "-cp", temp.resolve("missing.jar").toString(), source.toString()});
        assertEquals(2, write.exit, write.err);
        assertTrue(Files.readString(source).contains("import java.util.*;"));
    }

    private static Run run(String[] arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOptions options = CliOptions.parse(arguments);
        int exit = new CliRunner(
                        options,
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        new ByteArrayInputStream(new byte[0]))
                .run();
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exit, String out, String err) { }

}
