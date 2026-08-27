package zone.rong.clearskies.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the installed CLI launcher, not {@link CliRunner} in-process.
 *
 * <p>The launcher is the distribution entry point {@code bin/clearskies}. Two invocations of the same
 * arguments must agree, which is the smoke that a packaged build is not a one-shot accident.
 */
class PackagedCliSmokeTest {

    private static Path launcher() {
        String path = System.getProperty("clearskies.launcher");
        assertTrue(path != null && !path.isBlank(), "clearskies.launcher system property must point at bin/clearskies");
        Path file = Path.of(path);
        assertTrue(Files.isRegularFile(file), () -> "missing launcher: " + file);
        assertTrue(Files.isExecutable(file), () -> "launcher is not executable: " + file);
        return file;
    }

    @Test
    void helpRunsTwiceWithTheSameText() throws Exception {
        Run first = run(List.of("--help"));
        Run second = run(List.of("--help"));

        assertEquals(0, first.exitCode, first.err);
        assertEquals(0, second.exitCode, second.err);
        assertEquals(first.out, second.out);
        assertTrue(first.out.contains("--write"), first.out);
        assertTrue(first.out.contains("--classpath"), first.out);
    }

    @Test
    void writeThenCheckIsAFixedPointOnASmallFile(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(
                source,
                "package sample;\n\nimport java.util.*;\n\nclass Sample { List x; }\n",
                StandardCharsets.UTF_8);

        Run first = run(List.of("--write", source.toString()));
        assertEquals(0, first.exitCode, first.err);
        String expanded = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(expanded.contains("import java.util.List;"), expanded);
        assertTrue(expanded.contains("class Sample { List x; }"), expanded);

        Run second = run(List.of("--write", source.toString()));
        assertEquals(0, second.exitCode, second.err);
        assertEquals(expanded, Files.readString(source, StandardCharsets.UTF_8));

        Run check = run(List.of("--check", source.toString()));
        assertEquals(0, check.exitCode, check.err);
    }

    @Test
    void malformedFileLeavesOriginalBytesAndExitsNonZero(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Broken.java");
        String original = "package sample;\n\nimport java.util.*;\n\nclass Sample { List x;\n";
        Files.writeString(source, original, StandardCharsets.UTF_8);

        Run write = run(List.of("--write", source.toString()));
        assertEquals(2, write.exitCode, write.err);
        assertEquals(original, Files.readString(source, StandardCharsets.UTF_8));
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
            return new Run(
                    process.exitValue(),
                    Files.readString(outFile, StandardCharsets.UTF_8),
                    Files.readString(errFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outFile);
            Files.deleteIfExists(errFile);
        }
    }

    private record Run(int exitCode, String out, String err) { }

}
