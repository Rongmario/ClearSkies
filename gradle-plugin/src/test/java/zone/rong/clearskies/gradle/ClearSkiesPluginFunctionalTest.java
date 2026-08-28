package zone.rong.clearskies.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClearSkiesPluginFunctionalTest {

    @TempDir
    Path projectDirectory;

    @BeforeEach
    void writeProject() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n");
        Files.writeString(
                projectDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("zone.rong.clearskies")
                }

                clearSkies {
                    sourceSets("main")
                }
                """);
        Path source = Files.createDirectories(projectDirectory.resolve("src/main/java/sample"));
        Files.writeString(
                source.resolve("Sample.java"),
                """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """);
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
        String version = System.getProperty("clearskies.gradle.version");
        return version == null || version.isBlank() ? runner : runner.withGradleVersion(version);
    }

    @Test
    void checkTaskRunsAndIsUpToDateOnASecondInvocation() throws IOException {
        Path source = projectDirectory.resolve("src/main/java/sample/Sample.java");
        Files.writeString(
                source,
                """
                package sample;

                import java.util.List;

                class Sample { List x; }
                """);

        BuildResult first = run("clearSkiesCheck");
        assertEquals(TaskOutcome.SUCCESS, first.task(":clearSkiesCheck").getOutcome());

        BuildResult second = run("clearSkiesCheck");
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":clearSkiesCheck").getOutcome());
    }

    @Test
    void applyTaskExpandsStarImports() throws IOException {
        BuildResult result = run("clearSkiesApply");

        assertEquals(TaskOutcome.SUCCESS, result.task(":clearSkiesApply").getOutcome());
        String source = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        assertTrue(source.contains("import java.util.List;"), source);
        assertTrue(source.contains("class Sample { List x; }"), source);
    }

    @Test
    void applyTaskRewritesRestoredInputInsteadOfReusingCachedState() throws IOException {
        Path source = projectDirectory.resolve("src/main/java/sample/Sample.java");
        String starred =
                """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """;
        Files.writeString(source, starred);

        BuildResult first = run("clearSkiesApply", "--build-cache");
        assertEquals(TaskOutcome.SUCCESS, first.task(":clearSkiesApply").getOutcome());
        assertTrue(Files.readString(source).contains("import java.util.List;"));

        Files.writeString(source, starred);
        BuildResult second = run("clearSkiesApply", "--build-cache");

        assertEquals(TaskOutcome.SUCCESS, second.task(":clearSkiesApply").getOutcome());
        assertTrue(Files.readString(source).contains("import java.util.List;"), Files.readString(source));
    }

    @Test
    void checkFailsWhenAStarWouldExpand() {
        BuildResult result = runner("clearSkiesCheck")
                .buildAndFail();
        assertTrue(result.getOutput().contains("Sample.java") || result.getOutput().contains("star"), result.getOutput());
    }

    @Test
    void checkFailsOnIncompleteAttribution() throws IOException {
        Files.writeString(
                projectDirectory.resolve("src/main/java/sample/Sample.java"),
                """
                package sample;

                import com.missing.*;

                class Sample { Missing x; }
                """);

        BuildResult result = runner("clearSkiesCheck")
                .buildAndFail();
        assertTrue(
                result.getOutput().contains("Sample.java") || result.getOutput().toLowerCase().contains("incomplete"),
                result.getOutput());
    }

    @Test
    void javaCompileReleaseIsUsedUnlessOverridden() throws IOException {
        Files.writeString(
                projectDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("zone.rong.clearskies")
                }

                tasks.named<JavaCompile>("compileJava") {
                    options.release.set(17)
                }

                clearSkies {
                    sourceSets("main")
                }
                """);
        Files.writeString(
                projectDirectory.resolve("src/main/java/sample/Sample.java"),
                """
                package sample;

                import java.util.*;

                class Sample { SequencedCollection x; }
                """);

        BuildResult apply = run("clearSkiesApply");
        assertEquals(TaskOutcome.SUCCESS, apply.task(":clearSkiesApply").getOutcome());
        String source = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        assertTrue(source.contains("import java.util.*;"), source);
        assertFalse(source.contains("import java.util.SequencedCollection;"), source);

        Files.writeString(
                projectDirectory.resolve("build.gradle.kts"),
                """
                import zone.rong.clearskies.api.LanguageLevel

                plugins {
                    java
                    id("zone.rong.clearskies")
                }

                tasks.named<JavaCompile>("compileJava") {
                    options.release.set(17)
                }

                clearSkies {
                    sourceSets("main")
                    languageLevel = LanguageLevel.JAVA_21
                }
                """);
        BuildResult overridden = run("clearSkiesApply");
        assertEquals(TaskOutcome.SUCCESS, overridden.task(":clearSkiesApply").getOutcome());
        String expanded = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        assertTrue(expanded.contains("import java.util.SequencedCollection;"), expanded);
    }

    @Test
    void defaultMainAndTestSourceSetsIgnoreMissingResourceOutputDirs() throws IOException {
        Files.writeString(
                projectDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("zone.rong.clearskies")
                }
                """);
        Path testSource = Files.createDirectories(projectDirectory.resolve("src/test/java/sample"));
        Files.writeString(
                testSource.resolve("SampleTest.java"),
                """
                package sample;

                import java.util.*;

                class SampleTest { List x; }
                """);
        assertFalse(Files.isDirectory(projectDirectory.resolve("src/main/resources")));
        assertFalse(Files.isDirectory(projectDirectory.resolve("src/test/resources")));
        assertFalse(Files.exists(projectDirectory.resolve("build/resources/main")));
        assertFalse(Files.exists(projectDirectory.resolve("build/resources/test")));

        BuildResult check = runner("clearSkiesCheck")
                .buildAndFail();
        String checkOut = check.getOutput();
        assertFalse(checkOut.contains("missing classpath entry"), checkOut);
        assertFalse(checkOut.contains("build/resources/main"), checkOut);
        assertTrue(
                checkOut.contains("Sample.java") || checkOut.contains("SampleTest.java") || checkOut.contains("star"),
                checkOut);

        BuildResult apply = run("clearSkiesApply");
        assertEquals(TaskOutcome.SUCCESS, apply.task(":clearSkiesApply").getOutcome());
        String main = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        String test = Files.readString(testSource.resolve("SampleTest.java"));
        assertTrue(main.contains("import java.util.List;"), main);
        assertTrue(test.contains("import java.util.List;"), test);
        assertFalse(apply.getOutput().contains("missing classpath entry"), apply.getOutput());

        BuildResult clean = run("clearSkiesCheck");
        assertEquals(TaskOutcome.SUCCESS, clean.task(":clearSkiesCheck").getOutcome());
    }

    @Test
    void checkStoresAndReusesTheConfigurationCache() throws IOException {
        Files.writeString(
                projectDirectory.resolve("src/main/java/sample/Sample.java"),
                """
                package sample;

                import java.util.List;

                class Sample { List x; }
                """);

        BuildResult first = run("clearSkiesCheck", "--configuration-cache");
        assertTrue(first.getOutput().contains("Configuration cache entry stored"), first.getOutput());

        BuildResult second = run("clearSkiesCheck", "--configuration-cache");
        assertTrue(second.getOutput().contains("Configuration cache entry reused"), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":clearSkiesCheck").getOutcome());
    }

    @Test
    void applyDoesNotDragInTestCompilation() {
        BuildResult result = run("clearSkiesApply", "--dry-run");

        assertFalse(result.getOutput().contains(":compileTestJava"), result.getOutput());
    }

}
