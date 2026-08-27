package zone.rong.clearskies.gradle;

import zone.rong.clearskies.api.LanguageLevel;
import java.util.List;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code clearSkies { }} block.
 *
 * <pre>{@code
 * clearSkies {
 *     sourceSets("main", "test")
 *     languageLevel = LanguageLevel.JAVA_21
 * }
 * }</pre>
 */
public abstract class ClearSkiesExtension {

    /** Names of the source sets to expand. Defaults to every source set in the project. */
    public abstract ListProperty<String> getSourceSets();

    /**
     * Java release passed to javac as {@code --release}. When unset, each source set uses its
     * {@code JavaCompile} release (then source compatibility, then the running JDK).
     */
    public abstract Property<LanguageLevel> getLanguageLevel();

    /**
     * Charset used to read and write sources. When unset, each source set uses its
     * {@code JavaCompile} encoding, then UTF-8.
     */
    public abstract Property<String> getEncoding();

    /** Whether {@code check} depends on {@code clearSkiesCheck}. Defaults to true. */
    public abstract Property<Boolean> getEnforceOnCheck();

    /** Convenience for {@code sourceSets = listOf(...)}. */
    public void sourceSets(String... names) {
        getSourceSets().set(List.of(names));
    }

}
