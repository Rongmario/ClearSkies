package zone.rong.clearskies.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.clearskies.api.Diagnostic;
import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.StarExpander;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void noStarsReturnsTheSameString() {
        String source = "package sample;\n\nimport java.util.List;\n\nclass Sample { List x; }\n";
        ExpandResult result = expand(source);
        assertTrue(result.isUnchanged());
        assertEquals(ExpandResult.Outcome.UNCHANGED, result.outcome());
        assertFalse(result.hasErrors());
        assertSame(source, result.text());
    }

    @Test
    void expandsUtilStarToTheTypesTheBodyUsesAndLeavesTheBodyAlone() {
        String source =
                """
                package sample;

                import java.util.*;

                class Sample {List x;Map y;}
                """;
        String expected =
                """
                package sample;

                import java.util.List;
                import java.util.Map;

                class Sample {List x;Map y;}
                """;
        ExpandResult result = expand(source);
        assertFalse(result.hasErrors(), result.diagnostics().toString());
        assertEquals(ExpandResult.Outcome.EXPANDED, result.outcome());
        assertEquals(expected, result.text());
    }

    @Test
    void deletesAnUnusedStar() {
        String source =
                """
                package sample;

                import java.util.*;

                class Sample { int x; }
                """;
        String expected =
                """
                package sample;


                class Sample { int x; }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void expandsStaticStarToTheMembersTheBodyUses() {
        String source =
                """
                package sample;

                import static java.lang.Math.*;

                class Sample {
                    double y = abs(PI);
                }
                """;
        String expected =
                """
                package sample;

                import static java.lang.Math.PI;
                import static java.lang.Math.abs;

                class Sample {
                    double y = abs(PI);
                }
                """;
        ExpandResult result = expand(source);
        assertEquals(expected, result.text(), result.diagnostics().toString());
    }

    @Test
    void expandsStaticStarToAnEnumConstant() {
        String source =
                """
                package sample;

                import static java.time.DayOfWeek.*;

                class Sample { Object day = MONDAY; }
                """;
        String expected =
                """
                package sample;

                import static java.time.DayOfWeek.MONDAY;

                class Sample { Object day = MONDAY; }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void expandsStaticStarToAMemberType() {
        String source =
                """
                package sample;

                import static java.util.Map.*;

                class Sample { Entry<String, String> entry; }
                """;
        String expected =
                """
                package sample;

                import static java.util.Map.Entry;

                class Sample { Entry<String, String> entry; }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void doesNotReemitAStaticMemberAlreadyImportedExplicitly() {
        String source =
                """
                package sample;

                import static java.lang.Math.abs;
                import static java.lang.Math.*;

                class Sample { double y = abs(1); }
                """;
        String expected =
                """
                package sample;

                import static java.lang.Math.abs;

                class Sample { double y = abs(1); }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void doesNotReemitATypeAlreadyImportedExplicitly() {
        String source =
                """
                package sample;

                import java.util.List;
                import java.util.*;

                class Sample { List x; Map y; }
                """;
        String expected =
                """
                package sample;

                import java.util.List;
                import java.util.Map;

                class Sample { List x; Map y; }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void keepsAmbiguousStarsAndReportsThem(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.createDirectories(root.resolve("bar"));
        Files.writeString(root.resolve("foo/List.java"), "package foo;\npublic class List {}\n");
        Files.writeString(root.resolve("bar/List.java"), "package bar;\npublic class List {}\n");
        StarExpander expander = ClearSkies.newExpander()
                .classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root)))
                .build();
        String source =
                """
                package sample;

                import foo.*;
                import bar.*;

                class Sample { List x; }
                """;
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertEquals(source, result.text());
        assertEquals(ExpandResult.Outcome.INCOMPLETE, result.outcome());
        assertTrue(
                result.diagnostics().stream().anyMatch(d -> d.message().contains("ambiguous")),
                result.diagnostics().toString());
        assertFalse(result.hasErrors());
    }

    @Test
    void keepsAmbiguousStaticStarsAndReportsThem(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(root.resolve("foo/A.java"), "package foo;\npublic class A { public static int VALUE; }\n");
        Files.writeString(root.resolve("foo/B.java"), "package foo;\npublic class B { public static int VALUE; }\n");
        StarExpander expander = ClearSkies.newExpander()
                .classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root)))
                .build();
        String source =
                """
                package sample;

                import static foo.A.*;
                import static foo.B.*;

                class Sample { int value = VALUE; }
                """;
        ExpandResult result = expander.expand(ExpandRequest.of(source).withName("Sample.java"));
        assertEquals(source, result.text());
        assertEquals(ExpandResult.Outcome.INCOMPLETE, result.outcome());
        assertTrue(
                result.diagnostics().stream().anyMatch(d -> d.message().contains("ambiguous")),
                result.diagnostics().toString());
        assertFalse(result.hasErrors());
    }

    @Test
    void movesATrailingCommentOntoTheFirstExpandedLine() {
        String source =
                """
                package sample;

                import java.util.*; // keep

                class Sample { List x; Map y; }
                """;
        String expected =
                """
                package sample;

                import java.util.List; // keep
                import java.util.Map;

                class Sample { List x; Map y; }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void copiesTheStarLinesIndent() {
        String source = "package sample;\n\n  import java.util.*;\n\nclass Sample { List x; }\n";
        String expected = "package sample;\n\n  import java.util.List;\n\nclass Sample { List x; }\n";
        assertEquals(expected, text(source));
    }

    @Test
    void preservesACrlfTerminatorOnTheStarLine() {
        String source = "package sample;\r\n\r\nimport java.util.*;\r\n\nclass Sample { List x; }\n";
        String expected = "package sample;\r\n\r\nimport java.util.List;\r\n\nclass Sample { List x; }\n";
        assertEquals(expected, text(source));
    }

    @Test
    void secondExpandIsANoOp() {
        String source =
                """
                package sample;

                import java.util.*;

                class Sample { List x; }
                """;
        String once = text(source);
        ExpandResult twice = expand(once);
        assertTrue(twice.isUnchanged());
        assertSame(once, twice.text());
    }

    @Test
    void keepsAnUnusedLookingStarWhenTheFileHasUnresolvedTypes() {
        String source =
                """
                package sample;

                import com.missing.*;

                class Sample { Missing x; }
                """;
        ExpandResult result = expand(source);
        assertEquals(source, result.text());
        assertEquals(ExpandResult.Outcome.INCOMPLETE, result.outcome());
        assertTrue(
                result.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING),
                result.diagnostics().toString());
    }

    @Test
    void expandsAResolvedStarAndKeepsAnUnresolvedNeighbour() {
        String source =
                """
                package sample;

                import com.missing.*;
                import java.util.*;

                class Sample { List x; Missing y; }
                """;
        String expected =
                """
                package sample;

                import com.missing.*;
                import java.util.List;

                class Sample { List x; Missing y; }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void moduleInfoIsLeftAlone() {
        String source = "module sample {}\n";
        ExpandResult result = platform().expand(ExpandRequest.of(source).withName("module-info.java"));
        assertSame(source, result.text());
    }

    @Test
    void expandsAMemberTypeOnDemand(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(
                root.resolve("foo/Bar.java"),
                "package foo;\npublic class Bar { public static class Inner {} }\n");
        StarExpander expander = ClearSkies.newExpander()
                .classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root)))
                .build();
        String source =
                """
                package sample;

                import foo.Bar.*;

                class Sample { Inner x; }
                """;
        String expected =
                """
                package sample;

                import foo.Bar.Inner;

                class Sample { Inner x; }
                """;
        assertEquals(expected, expander.expand(ExpandRequest.of(source).withName("Sample.java")).text());
    }

    @Test
    void importOfAPackageDoesNotImportMemberTypes(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(
                root.resolve("foo/Bar.java"),
                "package foo;\npublic class Bar { public static class Inner {} }\n");
        StarExpander expander = ClearSkies.newExpander()
                .classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root)))
                .build();
        String source =
                """
                package sample;

                import foo.*;

                class Sample { Bar.Inner x; }
                """;
        String expected =
                """
                package sample;

                import foo.Bar;

                class Sample { Bar.Inner x; }
                """;
        assertEquals(expected, expander.expand(ExpandRequest.of(source).withName("Sample.java")).text());
    }

    @Test
    void samePackageStarIsDeleted(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("sample"));
        Files.writeString(root.resolve("sample/Other.java"), "package sample;\npublic class Other {}\n");
        StarExpander expander = ClearSkies.newExpander()
                .classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root)))
                .build();
        String source =
                """
                package sample;

                import sample.*;

                class Sample { Other x; }
                """;
        String expected =
                """
                package sample;


                class Sample { Other x; }
                """;
        assertEquals(expected, expander.expand(ExpandRequest.of(source).withName("Sample.java")).text());
    }

    @Test
    void packageInfoAnnotationIsAUse(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("src");
        Files.createDirectories(root.resolve("foo"));
        Files.writeString(
                root.resolve("foo/Anno.java"),
                "package foo;\nimport java.lang.annotation.Retention;\nimport java.lang.annotation.RetentionPolicy;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\npublic @interface Anno {}\n");
        StarExpander expander = ClearSkies.newExpander()
                .classpath(ExpandClasspath.platformOnly().withSourceRoots(List.of(root)))
                .build();
        String source =
                """
                @Anno
                package sample;

                import foo.*;
                """;
        String expected =
                """
                @Anno
                package sample;

                import foo.Anno;
                """;
        assertEquals(
                expected, expander.expand(ExpandRequest.of(source).withName("package-info.java")).text());
    }

    @Test
    void deletesRedundantJavaLangStar() {
        String source =
                """
                package sample;

                import java.lang.*;

                class Sample { String x; }
                """;
        String expected =
                """
                package sample;


                class Sample { String x; }
                """;
        assertEquals(expected, text(source));
    }

    @Test
    void failedResultReturnsTheOriginalSource() {
        ExpandResult failed = ExpandResult.failed("class X {}", List.of(Diagnostic.error("boom")));
        assertEquals(ExpandResult.Outcome.FAILED, failed.outcome());
        assertEquals("class X {}", failed.text());
        assertTrue(failed.hasErrors());
        assertTrue(failed.isUnchanged());
    }

}
