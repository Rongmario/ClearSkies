/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportRegionTest {

    @Test
    void skipsModuleInfoByName() {
        assertThat(ImportRegion.skip("module m {}", "module-info.java")).isTrue();
        assertThat(ImportRegion.skip("module m {}", "src/module-info.java")).isTrue();
    }

    @Test
    void noStarsIsASkip() {
        assertThat(ImportRegion.skip("package sample;\nimport java.util.List;\nclass Sample {}\n", "Sample.java")).isTrue();
    }

    @Test
    void findsANonStaticStar() {
        assertThat(ImportRegion.skip("import java.util.*;\nclass Sample {}\n", "Sample.java")).isFalse();
    }

    @Test
    void findsAStaticStar() {
        assertThat(ImportRegion.skip("import static java.lang.Math.*;\nclass Sample {}\n", "Sample.java")).isFalse();
    }

    @Test
    void ignoresStarsInsideComments() {
        assertThat(ImportRegion.skip("/* import java.util.*; */\nimport java.util.List;\nclass Sample {}\n", "Sample.java")).isTrue();
        assertThat(ImportRegion.skip("// import java.util.*;\nclass Sample {}\n", "Sample.java")).isTrue();
    }

    @Test
    void findsAStarAfterPackageAndAnnotation() {
        assertThat(ImportRegion.skip("@Deprecated\npackage sample;\n\nimport java.util.*;\nclass Sample {}\n", "Sample.java")).isFalse();
    }

    @Test
    void ignoresModuleImports() {
        assertThat(ImportRegion.skip("import module java.base;\nclass Sample {}\n", "Sample.java")).isTrue();
    }

    @Test
    void unicodeEscapedImportIsAHit() {
        assertThat(ImportRegion.skip("\\u0069mport java.util.*;\nclass Sample {}\n", "Sample.java")).isFalse();
    }

}
