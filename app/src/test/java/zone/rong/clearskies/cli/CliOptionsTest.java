package zone.rong.clearskies.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.clearskies.api.LanguageLevel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliOptionsTest {

    @Test
    void checkingIsTheDefaultMode() {
        CliOptions options = CliOptions.parse(new String[] {"src"});
        assertEquals(CliOptions.Mode.CHECK, options.mode());
        assertEquals(List.of(Path.of("src")), options.paths());
    }

    @Test
    void modesAndResolutionFlagsParse() {
        CliOptions options =
                CliOptions.parse(
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
                        });

        assertEquals(CliOptions.Mode.WRITE, options.mode());
        assertEquals(LanguageLevel.JAVA_21, options.languageLevel());
        assertEquals(3, options.parallelism());
        assertEquals(List.of("**/*.java"), options.includes());
        assertEquals(List.of("**/generated/**"), options.excludes());
        assertEquals(2, options.paths().size());
        assertEquals(2, options.classpath().size());
        assertEquals(List.of(Path.of("src")), options.sourcePath());
    }

    @Test
    void stdinDefaultsToWritingTheExpandedSourceOut() {
        CliOptions options = CliOptions.parse(new String[] {"--stdin-name", "Foo.java"});
        assertTrue(options.readStdin());
        assertEquals("Foo.java", options.stdinName());
        assertEquals(CliOptions.Mode.WRITE, options.mode());
    }

    @Test
    void malformedCommandLinesAreRejected() {
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {}));
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {"--nope", "src"}));
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {"--release"}));
    }

    @Test
    void helpNeedsNoPaths() {
        CliOptions options = CliOptions.parse(new String[] {"--help"});
        assertEquals(CliOptions.Mode.HELP, options.mode());
        assertTrue(options.paths().isEmpty());
        assertFalse(options.readStdin());
    }

}
