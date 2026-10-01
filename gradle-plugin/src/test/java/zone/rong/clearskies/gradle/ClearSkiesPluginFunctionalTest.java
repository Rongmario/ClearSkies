/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.gradle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
                """
        );
        Path source = Files.createDirectories(projectDirectory.resolve("src/main/java/sample"));
        Files.writeString(
            source.resolve("Sample.java"),
            """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """
        );
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner runner = GradleRunner.create().withProjectDir(projectDirectory.toFile()).withPluginClasspath().withArguments(arguments);
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
                """
        );

        BuildResult first = run("clearSkiesCheck");
        assertThat(first.task(":clearSkiesCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        BuildResult second = run("clearSkiesCheck");
        assertThat(second.task(":clearSkiesCheck").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void applyTaskExpandsStarImports() throws IOException {
        BuildResult result = run("clearSkiesApply");

        assertThat(result.task(":clearSkiesApply").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        String source = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        assertThat(source.contains("import java.util.List;")).as(source).isTrue();
        assertThat(source.contains("class Sample { List x; }")).as(source).isTrue();
    }

    @Test
    void applyTaskRewritesRestoredInputInsteadOfReusingCachedState() throws IOException {
        Path source = projectDirectory.resolve("src/main/java/sample/Sample.java");
        String starred = """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """;
        Files.writeString(source, starred);

        BuildResult first = run("clearSkiesApply", "--build-cache");
        assertThat(first.task(":clearSkiesApply").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(source).contains("import java.util.List;")).isTrue();

        Files.writeString(source, starred);
        BuildResult second = run("clearSkiesApply", "--build-cache");

        assertThat(second.task(":clearSkiesApply").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(source).contains("import java.util.List;")).as(Files.readString(source)).isTrue();
    }

    @Test
    void checkFailsWhenAStarWouldExpand() {
        BuildResult result = runner("clearSkiesCheck").buildAndFail();
        assertThat(result.getOutput().contains("Sample.java") || result.getOutput().contains("star")).as(result.getOutput()).isTrue();
    }

    @Test
    void checkFailsOnIncompleteAttribution() throws IOException {
        Files.writeString(
            projectDirectory.resolve("src/main/java/sample/Sample.java"),
            """
                package sample;

                import com.missing.*;

                class Sample { Missing x; }
                """
        );

        BuildResult result = runner("clearSkiesCheck").buildAndFail();
        assertThat(result.getOutput().contains("Sample.java") || result.getOutput().toLowerCase().contains("incomplete")).as(result.getOutput()).isTrue();
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
                """
        );
        Files.writeString(
            projectDirectory.resolve("src/main/java/sample/Sample.java"),
            """
                package sample;

                import java.util.*;

                class Sample { SequencedCollection x; }
                """
        );

        BuildResult apply = run("clearSkiesApply");
        assertThat(apply.task(":clearSkiesApply").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        String source = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        assertThat(source.contains("import java.util.*;")).as(source).isTrue();
        assertThat(source.contains("import java.util.SequencedCollection;")).as(source).isFalse();

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
                """
        );
        BuildResult overridden = run("clearSkiesApply");
        assertThat(overridden.task(":clearSkiesApply").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        String expanded = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        assertThat(expanded.contains("import java.util.SequencedCollection;")).as(expanded).isTrue();
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
                """
        );
        Path testSource = Files.createDirectories(projectDirectory.resolve("src/test/java/sample"));
        Files.writeString(
            testSource.resolve("SampleTest.java"),
            """
                package sample;

                import java.util.*;

                class SampleTest { List x; }
                """
        );
        assertThat(Files.isDirectory(projectDirectory.resolve("src/main/resources"))).isFalse();
        assertThat(Files.isDirectory(projectDirectory.resolve("src/test/resources"))).isFalse();
        assertThat(Files.exists(projectDirectory.resolve("build/resources/main"))).isFalse();
        assertThat(Files.exists(projectDirectory.resolve("build/resources/test"))).isFalse();

        BuildResult check = runner("clearSkiesCheck").buildAndFail();
        String checkOut = check.getOutput();
        assertThat(checkOut.contains("missing classpath entry")).as(checkOut).isFalse();
        assertThat(checkOut.contains("build/resources/main")).as(checkOut).isFalse();
        assertThat(checkOut.contains("Sample.java") || checkOut.contains("SampleTest.java") || checkOut.contains("star")).as(checkOut).isTrue();

        BuildResult apply = run("clearSkiesApply");
        assertThat(apply.task(":clearSkiesApply").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        String main = Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"));
        String test = Files.readString(testSource.resolve("SampleTest.java"));
        assertThat(main.contains("import java.util.List;")).as(main).isTrue();
        assertThat(test.contains("import java.util.List;")).as(test).isTrue();
        assertThat(apply.getOutput().contains("missing classpath entry")).as(apply.getOutput()).isFalse();

        BuildResult clean = run("clearSkiesCheck");
        assertThat(clean.task(":clearSkiesCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void checkStoresAndReusesTheConfigurationCache() throws IOException {
        Files.writeString(
            projectDirectory.resolve("src/main/java/sample/Sample.java"),
            """
                package sample;

                import java.util.List;

                class Sample { List x; }
                """
        );

        BuildResult first = run("clearSkiesCheck", "--configuration-cache");
        assertThat(first.getOutput().contains("Configuration cache entry stored")).as(first.getOutput()).isTrue();

        BuildResult second = run("clearSkiesCheck", "--configuration-cache");
        assertThat(second.getOutput().contains("Configuration cache entry reused")).as(second.getOutput()).isTrue();
        assertThat(second.task(":clearSkiesCheck").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void generatedSourcesUnderTheBuildDirectoryAreLeftAlone() throws IOException {
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """
                plugins {
                    java
                    id("zone.rong.clearskies")
                }

                sourceSets.main {
                    java.srcDir(layout.buildDirectory.dir("generated/sources/fixture"))
                }
                """
        );
        Files.writeString(
            projectDirectory.resolve("src/main/java/sample/Sample.java"),
            """
                package sample;

                import java.util.List;

                class Sample { List x; Generated g; }
                """
        );
        Path generated = Files.createDirectories(projectDirectory.resolve("build/generated/sources/fixture/sample"));
        String starred = """
                package sample;

                import java.util.*;

                class Generated { List x; }
                """;
        Files.writeString(generated.resolve("Generated.java"), starred);

        BuildResult check = run("clearSkiesCheck");
        assertThat(check.task(":clearSkiesCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        run("clearSkiesApply");
        assertThat(Files.readString(generated.resolve("Generated.java"))).isEqualTo(starred);
    }

    @Test
    void incompleteAttributionIsReportedAsAWarning() throws IOException {
        Files.writeString(
            projectDirectory.resolve("src/main/java/sample/Sample.java"),
            """
                package sample;

                import java.util.*;

                class Sample { Missing x; }
                """
        );

        BuildResult apply = run("clearSkiesApply");
        assertThat(apply.getOutput()).contains("because the file has unresolved types");
    }

    @Test
    void applyDoesNotDragInTestCompilation() {
        BuildResult result = run("clearSkiesApply", "--dry-run");

        assertThat(result.getOutput().contains(":compileTestJava")).as(result.getOutput()).isFalse();
    }

}
