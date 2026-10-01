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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StarExpanderTest {

    private static StarExpander platform() {
        return ClearSkies.defaultExpander();
    }

    private static ExpandResult expand(String source) {
        return platform().expand(ExpandRequest.of(source).withName("Sample.java"));
    }

    private static String text(String source) {
        return expand(source).text();
    }

    @Test
    void keptOwnersAndStaticStarsCanBeLeftAlone() {
        String source = """
                package sample;

                import java.util.*;
                import java.util.concurrent.*;
                import static java.lang.Math.*;

                class Sample { List x; Callable<Integer> c = () -> abs(-1); }
                """;
        StarExpander expander = ClearSkies.newExpander().keep(List.of("java.util.concurrent.*")).expandStaticImports(false).build();
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));

        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text()).isEqualTo(source.replace("import java.util.*;", "import java.util.List;"));
    }

    @Test
    void noStarsReturnsTheSameString() {
        String source = "package sample;\n\nimport java.util.List;\n\nclass Sample { List x; }\n";
        ExpandResult result = expand(source);
        assertThat(result.isUnchanged()).isTrue();
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.UNCHANGED);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.text()).isSameAs(source);
    }

    @Test
    void expandsUtilStarToTheTypesTheBodyUsesAndLeavesTheBodyAlone() {
        String source = """
                package sample;

                import java.util.*;

                class Sample {List x;Map y;}
                """;
        String expected = """
                package sample;

                import java.util.List;
                import java.util.Map;

                class Sample {List x;Map y;}
                """;
        ExpandResult result = expand(source);
        assertThat(result.hasErrors()).as(result.diagnostics().toString()).isFalse();
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.EXPANDED);
        assertThat(result.text()).isEqualTo(expected);
    }

    @Test
    void deletesAnUnusedStar() {
        String source = """
                package sample;

                import java.util.*;

                class Sample { int x; }
                """;
        String expected = """
                package sample;


                class Sample { int x; }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void expandsStaticStarToTheMembersTheBodyUses() {
        String source = """
                package sample;

                import static java.lang.Math.*;

                class Sample {
                    double y = abs(PI);
                }
                """;
        String expected = """
                package sample;

                import static java.lang.Math.PI;
                import static java.lang.Math.abs;

                class Sample {
                    double y = abs(PI);
                }
                """;
        ExpandResult result = expand(source);
        assertThat(result.text()).as(result.diagnostics().toString()).isEqualTo(expected);
    }

    @Test
    void expandsStaticStarToAnEnumConstant() {
        String source = """
                package sample;

                import static java.time.DayOfWeek.*;

                class Sample { Object day = MONDAY; }
                """;
        String expected = """
                package sample;

                import static java.time.DayOfWeek.MONDAY;

                class Sample { Object day = MONDAY; }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void expandsStaticStarToAMemberType() {
        String source = """
                package sample;

                import static java.util.Map.*;

                class Sample { Entry<String, String> entry; }
                """;
        String expected = """
                package sample;

                import static java.util.Map.Entry;

                class Sample { Entry<String, String> entry; }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void expandsStarOfAClassDeclaredInTheSameFileWhenTheHeaderUsesItsMember() {
        String source = """
                package sample;

                import sample.Sample.*;

                class Sample extends java.util.ArrayList<Inner> {
                    static class Inner {}
                }
                """;
        String expected = """
                package sample;

                import sample.Sample.Inner;

                class Sample extends java.util.ArrayList<Inner> {
                    static class Inner {}
                }
                """;
        ExpandResult result = expand(source);
        assertThat(result.hasErrors()).as(result.diagnostics().toString()).isFalse();
        assertThat(result.text()).isEqualTo(expected);
    }

    @Test
    void doesNotReemitAStaticMemberAlreadyImportedExplicitly() {
        String source = """
                package sample;

                import static java.lang.Math.abs;
                import static java.lang.Math.*;

                class Sample { double y = abs(1); }
                """;
        String expected = """
                package sample;

                import static java.lang.Math.abs;

                class Sample { double y = abs(1); }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void doesNotReemitATypeAlreadyImportedExplicitly() {
        String source = """
                package sample;

                import java.util.List;
                import java.util.*;

                class Sample { List x; Map y; }
                """;
        String expected = """
                package sample;

                import java.util.List;
                import java.util.Map;

                class Sample { List x; Map y; }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void keepsAmbiguousStarsAndReportsThem(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.createDirectories(root.resolve("bar"));
        Files.writeString(root.resolve("foo/List.java"), "package foo;\npublic class List {}\n");
        Files.writeString(root.resolve("bar/List.java"), "package bar;\npublic class List {}\n");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = """
                package sample;

                import foo.*;
                import bar.*;

                class Sample { List x; }
                """;
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.INCOMPLETE);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.message().contains("ambiguous"))).as(result.diagnostics().toString()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void keepsAmbiguousStaticStarsAndReportsThem(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(root.resolve("foo/A.java"), "package foo;\npublic class A { public static int VALUE; }\n");
        Files.writeString(root.resolve("foo/B.java"), "package foo;\npublic class B { public static int VALUE; }\n");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = """
                package sample;

                import static foo.A.*;
                import static foo.B.*;

                class Sample { int value = VALUE; }
                """;
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.INCOMPLETE);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.message().contains("ambiguous"))).as(result.diagnostics().toString()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void movesATrailingCommentOntoTheFirstExpandedLine() {
        String source = """
                package sample;

                import java.util.*; // keep

                class Sample { List x; Map y; }
                """;
        String expected = """
                package sample;

                import java.util.List; // keep
                import java.util.Map;

                class Sample { List x; Map y; }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void copiesTheStarLinesIndent() {
        String source = "package sample;\n\n  import java.util.*;\n\nclass Sample { List x; }\n";
        String expected = "package sample;\n\n  import java.util.List;\n\nclass Sample { List x; }\n";
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void preservesACrlfTerminatorOnTheStarLine() {
        String source = "package sample;\r\n\r\nimport java.util.*;\r\n\nclass Sample { List x; }\n";
        String expected = "package sample;\r\n\r\nimport java.util.List;\r\n\nclass Sample { List x; }\n";
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void secondExpandIsANoOp() {
        String source = """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """;
        String once = text(source);
        ExpandResult twice = expand(once);
        assertThat(twice.isUnchanged()).isTrue();
        assertThat(twice.text()).isSameAs(once);
    }

    @Test
    void keepsAnUnusedLookingStarWhenTheFileHasUnresolvedTypes() {
        String source = """
                package sample;

                import com.missing.*;

                class Sample { Missing x; }
                """;
        ExpandResult result = expand(source);
        assertThat(result.text()).isEqualTo(source);
        assertThat(result.outcome()).isEqualTo(ExpandResult.Outcome.INCOMPLETE);
        assertThat(result.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING)).as(result.diagnostics().toString()).isTrue();
    }

    @Test
    void expandsAResolvedStarAndKeepsAnUnresolvedNeighbour() {
        String source = """
                package sample;

                import com.missing.*;
                import java.util.*;

                class Sample { List x; Missing y; }
                """;
        String expected = """
                package sample;

                import com.missing.*;
                import java.util.List;

                class Sample { List x; Missing y; }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void moduleInfoIsLeftAlone() {
        String source = "module sample {}\n";
        ExpandResult result = platform().expand(ExpandRequest.of(source).withName("module-info.java"));
        assertThat(result.text()).isSameAs(source);
    }

    @Test
    void expandsAMemberTypeOnDemand(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(root.resolve("foo/Bar.java"), "package foo;\npublic class Bar { public static class Inner {} }\n");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = """
                package sample;

                import foo.Bar.*;

                class Sample { Inner x; }
                """;
        String expected = """
                package sample;

                import foo.Bar.Inner;

                class Sample { Inner x; }
                """;
        assertThat(expander.expand(ExpandRequest.of(source).withName("Sample.java")).text()).isEqualTo(expected);
    }

    @Test
    void importOfAPackageDoesNotImportMemberTypes(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(root.resolve("foo/Bar.java"), "package foo;\npublic class Bar { public static class Inner {} }\n");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = """
                package sample;

                import foo.*;

                class Sample { Bar.Inner x; }
                """;
        String expected = """
                package sample;

                import foo.Bar;

                class Sample { Bar.Inner x; }
                """;
        assertThat(expander.expand(ExpandRequest.of(source).withName("Sample.java")).text()).isEqualTo(expected);
    }

    @Test
    void samePackageStarIsDeleted(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("sample"));
        Files.writeString(root.resolve("sample/Other.java"), "package sample;\npublic class Other {}\n");
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = """
                package sample;

                import sample.*;

                class Sample { Other x; }
                """;
        String expected = """
                package sample;


                class Sample { Other x; }
                """;
        assertThat(expander.expand(ExpandRequest.of(source).withName("Sample.java")).text()).isEqualTo(expected);
    }

    @Test
    void packageInfoAnnotationIsAUse(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(
            root.resolve("foo/Anno.java"),
            "package foo;\nimport java.lang.annotation.Retention;\nimport java.lang.annotation.RetentionPolicy;\n" + "@Retention(RetentionPolicy.RUNTIME)\npublic @interface Anno {}\n"
        );
        StarExpander expander = ClearSkies.newExpander().classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root))).build();
        String source = """
                @Anno
                package sample;

                import foo.*;
                """;
        String expected = """
                @Anno
                package sample;

                import foo.Anno;
                """;
        assertThat(expander.expand(ExpandRequest.of(source).withName("package-info.java")).text()).isEqualTo(expected);
    }

    @Test
    void deletesRedundantJavaLangStar() {
        String source = """
                package sample;

                import java.lang.*;

                class Sample { String x; }
                """;
        String expected = """
                package sample;


                class Sample { String x; }
                """;
        assertThat(text(source)).isEqualTo(expected);
    }

    @Test
    void failedResultReturnsTheOriginalSource() {
        ExpandResult failed = ExpandResult.failed("class X {}", List.of(Diagnostic.error("boom")));
        assertThat(failed.outcome()).isEqualTo(ExpandResult.Outcome.FAILED);
        assertThat(failed.text()).isEqualTo("class X {}");
        assertThat(failed.hasErrors()).isTrue();
        assertThat(failed.isUnchanged()).isTrue();
    }

}
