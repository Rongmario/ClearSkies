/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import zone.rong.clearskies.api.Diagnostic;
import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.StarExpander;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

class StarExpanderSafetyTest {

    private static ExpandResult expand(String source) {
        return expand(source, "Sample.java");
    }

    private static ExpandResult expand(String source, String name) {
        return ClearSkies.defaultExpander().expand(ExpandRequest.of(source).withName(name));
    }

    @Test
    void missingBraceAbandonsExpansionAndReturnsTheOriginal() {
        String source = """
                package sample;

                import java.util.*;

                class Sample { List x;
                """;
        ExpandResult result = expand(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.FAILED);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.diagnostics().isEmpty()).as(result.diagnostics().toString()).isFalse();
    }

    @Test
    void unparseableSourceAbandonsExpansion() {
        String source = "import java.util.*;\nclass Sample { {{{ \n";
        ExpandResult result = expand(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.FAILED);
        assertThat(result.text()).isEqualTo(source);
    }

    @Test
    void classpathWildcardLoadsTypesFromSortedJars(@TempDir Path temp) throws Exception {
        Path lib = temp.resolve("lib");
        Files.createDirectories(lib);
        compileJar(temp, lib.resolve("foo.jar"), "foo", "Bar", "public class Bar {}");
        compileJar(temp, lib.resolve("unused.zip"), "foo", "Unused", "public class Unused {}");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.of(List.of(lib.resolve("*")))).build();
        String source = """
                package sample;

                import foo.*;

                class Sample { Bar x; }
                """;
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text().contains("import foo.Bar;")).as(result.text()).isTrue();
        assertThat(result.text().contains("class Sample { Bar x; }")).as(result.text()).isTrue();
        assertThat(result.text().contains("import foo.*;")).as(result.text()).isFalse();
    }

    @Test
    void missingClasspathEntryFailsClosed(@TempDir Path temp) {
        Path missing = temp.resolve("no-such.jar");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.of(List.of(missing))).build();
        String source = """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """;
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.FAILED);
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.message().contains("missing classpath entry"))).as(result.diagnostics().toString()).isTrue();
    }

    @Test
    void missingSourcePathEntryFailsClosed(@TempDir Path temp) {
        Path missing = temp.resolve("no-such-src");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(missing))).build();
        String source = """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """;
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.FAILED);
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.message().contains("missing source-path entry"))).as(result.diagnostics().toString()).isTrue();
    }

    @Test
    void commentInsideImportIsLeftUnchangedWithAWarning() {
        String source = """
                package sample;

                import java./* KEEP */util.*;

                class Sample { List x; }
                """;
        ExpandResult result = expand(source);
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.INCOMPLETE);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING)).as(result.diagnostics().toString()).isTrue();
        assertThat(result.text().contains("/* KEEP */")).as(result.text()).isTrue();
    }

    @Test
    void unusedStarWithTrailingCommentIsLeftUnchanged() {
        String source = """
                package sample;

                import java.util.*; // keep

                class Sample { int x; }
                """;
        ExpandResult result = expand(source);
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.INCOMPLETE);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.message().contains("trailing comment"))).as(result.diagnostics().toString()).isTrue();
    }

    @Test
    void sourceNameWithASpaceExpands() {
        String source = """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """;
        ExpandResult result = expand(source, "My File.java");
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text().contains("import java.util.List;")).as(result.text()).isTrue();
        assertThat(result.text().contains("class Sample { List x; }")).as(result.text()).isTrue();
    }

    @Test
    void duplicateIdenticalStarsAreNotAmbiguous() {
        String source = """
                package sample;

                import java.util.*;
                import java.util.*;

                class Sample { List x; }
                """;
        ExpandResult result = expand(source);
        assertThat(result.hasErrors()).as(result.diagnostics().toString()).isFalse();
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.message().contains("ambiguous"))).as(result.diagnostics().toString()).isFalse();
        assertThat(result.text().contains("import java.util.List;")).as(result.text()).isTrue();
        assertThat(result.text().split("import java.util.List;", -1).length - 1).as(result.text()).isEqualTo(1);
    }

    @Test
    void duplicateIdenticalStaticStarsAreNotAmbiguous() {
        String source = """
                package sample;

                import static java.lang.Math.*;
                import static java.lang.Math.*;

                class Sample { double y = abs(1); }
                """;
        ExpandResult result = expand(source);
        assertThat(result.hasErrors()).as(result.diagnostics().toString()).isFalse();
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.message().contains("ambiguous"))).as(result.diagnostics().toString()).isFalse();
        assertThat(result.text().split("import static java.lang.Math.abs;", -1).length - 1).as(result.text()).isEqualTo(1);
    }

    @Test
    void javadocLinkCountsAsAUse() {
        String source = """
                package sample;

                import java.util.*;

                /** {@link List} */
                class Sample {}
                """;
        ExpandResult result = expand(source);
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text().contains("import java.util.List;")).as(result.text()).isTrue();
        assertThat(result.text().contains("{@link List}")).as(result.text()).isTrue();
    }

    @Test
    void unicodeEscapedImportIsExpanded() {
        String source = "package sample;\n\n\\u0069mport java.util.*;\n\nclass Sample { List x; }\n";
        ExpandResult result = expand(source);
        assertThat(result.outcome()).as(result.diagnostics().toString()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text().contains("import java.util.List;")).as(result.text()).isTrue();
        assertThat(result.text().contains("class Sample { List x; }")).as(result.text()).isTrue();
    }

    @Test
    void unresolvedNeighbourStillExpandsAResolvedStar() {
        String source = """
                package sample;

                import com.missing.*;
                import java.util.*;

                class Sample { List x; Missing y; }
                """;
        ExpandResult result = expand(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.INCOMPLETE);
        assertThat(result.text().contains("import java.util.List;")).as(result.text()).isTrue();
        assertThat(result.text().contains("import com.missing.*;")).as(result.text()).isTrue();
    }

    @Test
    void atomicWriteReplacesTheTargetWithTheFullText(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("Sample.java");
        Files.writeString(file, "old", StandardCharsets.UTF_8);
        String expanded = "package sample;\n\nimport java.util.List;\n\nclass Sample { List x; }\n";
        AtomicFiles.writeString(file, expanded, StandardCharsets.UTF_8);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(expanded);
    }

    private static void compileJar(Path temp, Path jar, String packageName, String className, String body) throws Exception {
        Path src = temp.resolve("src-" + className);
        Path pkg = src.resolve(packageName);
        Files.createDirectories(pkg);
        Path javaFile = pkg.resolve(className + ".java");
        Files.writeString(javaFile, "package " + packageName + ";\n" + body + "\n", StandardCharsets.UTF_8);
        Path classes = temp.resolve("classes-" + className);
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(javaFile.toFile());
            Boolean ok = compiler.getTask(null, fileManager, null, List.of("-d", classes.toString()), null, units).call();
            assertThat(ok).as("failed to compile " + javaFile).isTrue();
        }
        Files.createDirectories(jar.getParent());
        Path classFile = classes.resolve(packageName).resolve(className + ".class");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(packageName + "/" + className + ".class"));
            out.write(Files.readAllBytes(classFile));
            out.closeEntry();
        }
    }

}
