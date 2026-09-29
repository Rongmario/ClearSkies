/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.gradle;

import zone.rong.clearskies.api.LanguageLevel;

import org.junit.jupiter.api.Test;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClearSkiesExtensionTest {

    private static ClearSkiesExtension extensionOf(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(ClearSkiesPlugin.class);
        return project.getExtensions().getByType(ClearSkiesExtension.class);
    }

    @Test
    void registersBothTasksAndTheExtension() {
        Project project = ProjectBuilder.builder().build();
        extensionOf(project);

        assertThat(project.getTasks().getNames().contains(ClearSkiesPlugin.APPLY_TASK_NAME)).isTrue();
        assertThat(project.getTasks().getNames().contains(ClearSkiesPlugin.CHECK_TASK_NAME)).isTrue();
    }

    @Test
    void defaultsMatchTheDocumentedConventions() {
        ClearSkiesExtension extension = extensionOf(ProjectBuilder.builder().build());

        assertThat(extension.getEnforceOnCheck().get()).isEqualTo(Boolean.TRUE);
        assertThat(extension.getEncoding().isPresent()).isFalse();
        assertThat(extension.getLanguageLevel().isPresent()).isFalse();
    }

    @Test
    void sourceSetWorkUsesJavaCompileReleaseAndEncoding() {
        Project project = ProjectBuilder.builder().build();
        extensionOf(project);
        project.getTasks().named("compileJava", JavaCompile.class).configure(compile -> {
            compile.getOptions().getRelease().set(17);
            compile.getOptions().setEncoding("ISO-8859-1");
        });

        ClearSkiesTask check = (ClearSkiesTask) project.getTasks().getByName(ClearSkiesPlugin.CHECK_TASK_NAME);
        SourceSetWork main = check.getTargets().get().stream().filter(work -> work.getName().equals("main")).findFirst().orElseThrow();
        assertThat(main.getLanguageLevel().get()).isEqualTo(LanguageLevel.JAVA_17);
        assertThat(main.getEncoding().get()).isEqualTo("ISO-8859-1");
    }

    @Test
    void explicitExtensionOverridesWin() {
        Project project = ProjectBuilder.builder().build();
        ClearSkiesExtension extension = extensionOf(project);
        project.getTasks().named("compileJava", JavaCompile.class).configure(compile -> {
            compile.getOptions().getRelease().set(17);
            compile.getOptions().setEncoding("ISO-8859-1");
        });
        extension.getLanguageLevel().set(LanguageLevel.JAVA_21);
        extension.getEncoding().set("UTF-16");

        ClearSkiesTask check = (ClearSkiesTask) project.getTasks().getByName(ClearSkiesPlugin.CHECK_TASK_NAME);
        SourceSetWork main = check.getTargets().get().stream().filter(work -> work.getName().equals("main")).findFirst().orElseThrow();
        assertThat(main.getLanguageLevel().get()).isEqualTo(LanguageLevel.JAVA_21);
        assertThat(main.getEncoding().get()).isEqualTo("UTF-16");
    }

    @Test
    void includeAndExcludeNarrowTheSources() throws IOException {
        Project project = ProjectBuilder.builder().build();
        ClearSkiesExtension extension = extensionOf(project);
        for (String path : List.of("a/Kept.java", "a/skip/Skipped.java", "b/Other.java")) {
            Path file = project.file("src/main/java/" + path).toPath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }
        extension.include("a/**");
        extension.exclude("**/skip/**");

        ClearSkiesTask check = (ClearSkiesTask) project.getTasks().getByName(ClearSkiesPlugin.CHECK_TASK_NAME);
        assertThat(check.getTargets().get().stream().filter(work -> work.getName().equals("main")).findFirst().orElseThrow().getSource().getFiles()).isEqualTo(
            Set.of(project.file("src/main/java/a/Kept.java"))
        );
    }

}
