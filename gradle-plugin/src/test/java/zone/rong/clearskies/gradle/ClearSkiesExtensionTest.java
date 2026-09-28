package zone.rong.clearskies.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.clearskies.api.LanguageLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

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

        assertTrue(project.getTasks().getNames().contains(ClearSkiesPlugin.APPLY_TASK_NAME));
        assertTrue(project.getTasks().getNames().contains(ClearSkiesPlugin.CHECK_TASK_NAME));
    }

    @Test
    void defaultsMatchTheDocumentedConventions() {
        ClearSkiesExtension extension = extensionOf(ProjectBuilder.builder().build());

        assertEquals(Boolean.TRUE, extension.getEnforceOnCheck().get());
        assertFalse(extension.getEncoding().isPresent());
        assertFalse(extension.getLanguageLevel().isPresent());
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
        SourceSetWork main = check.getTargets().get().stream()
                .filter(work -> work.getName().equals("main"))
                .findFirst()
                .orElseThrow();
        assertEquals(LanguageLevel.JAVA_17, main.getLanguageLevel().get());
        assertEquals("ISO-8859-1", main.getEncoding().get());
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
        SourceSetWork main = check.getTargets().get().stream()
                .filter(work -> work.getName().equals("main"))
                .findFirst()
                .orElseThrow();
        assertEquals(LanguageLevel.JAVA_21, main.getLanguageLevel().get());
        assertEquals("UTF-16", main.getEncoding().get());
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
        assertEquals(Set.of(project.file("src/main/java/a/Kept.java")), check.getTargets().get().stream()
                .filter(work -> work.getName().equals("main"))
                .findFirst()
                .orElseThrow()
                .getSource()
                .getFiles());
    }

}
