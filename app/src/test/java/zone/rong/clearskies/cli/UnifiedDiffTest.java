package zone.rong.clearskies.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnifiedDiffTest {

    @Test
    void identicalTextsProduceNoDiff() {
        assertEquals("", UnifiedDiff.between("Foo.java", "class A {}\n", "class A {}\n"));
    }

    @Test
    void aChangedLineShowsInTheHunk() {
        String diff = UnifiedDiff.between("Foo.java", "import java.util.*;\n", "import java.util.List;\n");
        assertTrue(diff.contains("--- Foo.java"), diff);
        assertTrue(diff.contains("-import java.util.*;"), diff);
        assertTrue(diff.contains("+import java.util.List;"), diff);
    }

    @Test
    void missingTerminalNewlineIsReported() {
        String diff = UnifiedDiff.between("Foo.java", "class A {}\n", "class A {}");
        assertTrue(diff.contains("class A {}"), diff);
        assertTrue(diff.contains("\\ No newline at end of file"), diff);
    }

    @Test
    void largeSourcesDoNotUseAQuadraticMatrix() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        before.append("package sample;\n\nimport java.util.*;\n\nclass Sample {\n");
        after.append("package sample;\n\nimport java.util.List;\n\nclass Sample {\n");
        for (int i = 0; i < 8000; i++) {
            before.append("    int f").append(i).append(";\n");
            after.append("    int f").append(i).append(";\n");
        }
        before.append("    List x;\n}\n");
        after.append("    List x;\n}\n");
        String diff = UnifiedDiff.between("Large.java", before.toString(), after.toString());
        assertTrue(diff.contains("-import java.util.*;"), diff);
        assertTrue(diff.contains("+import java.util.List;"), diff);
        assertFalse(diff.contains("-    int f0;"), diff);
    }

}
