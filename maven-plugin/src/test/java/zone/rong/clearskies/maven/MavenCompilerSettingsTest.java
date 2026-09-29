/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.maven;

import zone.rong.clearskies.api.LanguageLevel;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MavenCompilerSettingsTest {

    @Test
    void explicitOverrideWinsOverCompilerRelease() {
        Properties properties = new Properties();
        properties.setProperty("maven.compiler.release", "17");
        assertThat(MavenCompilerSettings.languageLevel(21, properties)).isEqualTo(LanguageLevel.JAVA_21);
    }

    @Test
    void compilerReleaseIsUsedWhenClearSkiesDoesNotOverride() {
        Properties properties = new Properties();
        properties.setProperty("maven.compiler.release", "17");
        assertThat(MavenCompilerSettings.languageLevel(null, properties)).isEqualTo(LanguageLevel.JAVA_17);
    }

    @Test
    void compilerSourceIsUsedWhenReleaseIsAbsent() {
        Properties properties = new Properties();
        properties.setProperty("maven.compiler.source", "21");
        assertThat(MavenCompilerSettings.languageLevel(null, properties)).isEqualTo(LanguageLevel.JAVA_21);
    }

    @Test
    void processSourcesDoesNotWalkTestSources() {
        assertThat(AbstractClearSkiesMojo.processMainSources("process-sources")).isTrue();
        assertThat(AbstractClearSkiesMojo.processTestSources(true, "process-sources")).isFalse();
    }

    @Test
    void processTestSourcesWalksTestsOnly() {
        assertThat(AbstractClearSkiesMojo.processMainSources("process-test-sources")).isFalse();
        assertThat(AbstractClearSkiesMojo.processTestSources(true, "process-test-sources")).isTrue();
    }

    @Test
    void directInvocationWalksBothWhenTestsAreIncluded() {
        assertThat(AbstractClearSkiesMojo.processMainSources(null)).isTrue();
        assertThat(AbstractClearSkiesMojo.processTestSources(true, null)).isTrue();
        assertThat(AbstractClearSkiesMojo.processTestSources(false, null)).isFalse();
    }

}
