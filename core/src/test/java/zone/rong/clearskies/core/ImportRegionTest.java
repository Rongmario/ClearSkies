package zone.rong.clearskies.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImportRegionTest {

    @Test
    void skipsModuleInfoByName() {
        assertTrue(ImportRegion.skip("module m {}", "module-info.java"));
        assertTrue(ImportRegion.skip("module m {}", "src/module-info.java"));
    }

    @Test
    void noStarsIsASkip() {
        assertTrue(ImportRegion.skip("package sample;\nimport java.util.List;\nclass Sample {}\n", "Sample.java"));
    }

    @Test
    void findsANonStaticStar() {
        assertFalse(ImportRegion.skip("import java.util.*;\nclass Sample {}\n", "Sample.java"));
    }

    @Test
    void findsAStaticStar() {
        assertFalse(ImportRegion.skip("import static java.lang.Math.*;\nclass Sample {}\n", "Sample.java"));
    }

    @Test
    void ignoresStarsInsideComments() {
        assertTrue(ImportRegion.skip("/* import java.util.*; */\nimport java.util.List;\nclass Sample {}\n", "Sample.java"));
        assertTrue(ImportRegion.skip("// import java.util.*;\nclass Sample {}\n", "Sample.java"));
    }

    @Test
    void findsAStarAfterPackageAndAnnotation() {
        assertFalse(ImportRegion.skip(
                "@Deprecated\npackage sample;\n\nimport java.util.*;\nclass Sample {}\n", "Sample.java"));
    }

    @Test
    void ignoresModuleImports() {
        assertTrue(ImportRegion.skip("import module java.base;\nclass Sample {}\n", "Sample.java"));
    }

    @Test
    void unicodeEscapedImportIsAHit() {
        assertFalse(ImportRegion.skip("\\u0069mport java.util.*;\nclass Sample {}\n", "Sample.java"));
    }

}
