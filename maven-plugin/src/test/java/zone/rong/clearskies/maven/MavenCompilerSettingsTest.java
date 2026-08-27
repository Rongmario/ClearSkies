package zone.rong.clearskies.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.clearskies.api.LanguageLevel;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MavenCompilerSettingsTest {

    @Test
    void explicitOverrideWinsOverCompilerRelease() {
        Properties properties = new Properties();
        properties.setProperty("maven.compiler.release", "17");
        assertEquals(LanguageLevel.JAVA_21, MavenCompilerSettings.languageLevel(21, properties));
    }

    @Test
    void compilerReleaseIsUsedWhenClearSkiesDoesNotOverride() {
        Properties properties = new Properties();
        properties.setProperty("maven.compiler.release", "17");
        assertEquals(LanguageLevel.JAVA_17, MavenCompilerSettings.languageLevel(null, properties));
    }

    @Test
    void compilerSourceIsUsedWhenReleaseIsAbsent() {
        Properties properties = new Properties();
        properties.setProperty("maven.compiler.source", "21");
        assertEquals(LanguageLevel.JAVA_21, MavenCompilerSettings.languageLevel(null, properties));
    }

    @Test
    void processSourcesDoesNotWalkTestSources() {
        assertTrue(AbstractClearSkiesMojo.processMainSources("process-sources"));
        assertFalse(AbstractClearSkiesMojo.processTestSources(true, "process-sources"));
    }

    @Test
    void processTestSourcesWalksTestsOnly() {
        assertFalse(AbstractClearSkiesMojo.processMainSources("process-test-sources"));
        assertTrue(AbstractClearSkiesMojo.processTestSources(true, "process-test-sources"));
    }

    @Test
    void directInvocationWalksBothWhenTestsAreIncluded() {
        assertTrue(AbstractClearSkiesMojo.processMainSources(null));
        assertTrue(AbstractClearSkiesMojo.processTestSources(true, null));
        assertFalse(AbstractClearSkiesMojo.processTestSources(false, null));
    }

}
