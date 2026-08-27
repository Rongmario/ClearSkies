package zone.rong.clearskies.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathGlobsTest {

    @Test
    void rootRelativeIncludeMatchesFromTheDocumentedRoot(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("src/main/java/sample/Sample.java");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "class Sample {}\n");
        assertTrue(PathGlobs.allowed(file, temp, List.of("src/main/java/**/*.java"), List.of()));
        assertFalse(PathGlobs.allowed(file, temp, List.of("src/test/java/**/*.java"), List.of()));
    }

    @Test
    void excludeWins(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("src/main/java/sample/Sample.java");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "class Sample {}\n");
        assertFalse(PathGlobs.allowed(
                file, temp, List.of("src/main/java/**/*.java"), List.of("**/Sample.java")));
    }

}
