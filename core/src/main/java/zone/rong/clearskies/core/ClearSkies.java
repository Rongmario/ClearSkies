package zone.rong.clearskies.core;

import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.LanguageLevel;
import zone.rong.clearskies.api.StarExpander;
import zone.rong.clearskies.api.StarExpanderBuilder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Entry point to the expander.
 *
 * <pre>{@code
 * StarExpander expander = ClearSkies.newExpander()
 *         .classpath(ExpandClasspath.platformOnly())
 *         .build();
 * String expanded = expander.expand(source);
 * }</pre>
 */
public final class ClearSkies {

    private ClearSkies() { }

    /** A builder using platform modules only and the running JDK's release. */
    public static StarExpanderBuilder newExpander() {
        return new DefaultStarExpanderBuilder();
    }

    /** An expander for {@code java.*} stars, for the common case of not configuring anything. */
    public static StarExpander defaultExpander() {
        return newExpander().build();
    }

    private static final class DefaultStarExpanderBuilder implements StarExpanderBuilder {

        private ExpandClasspath classpath = ExpandClasspath.platformOnly();
        private LanguageLevel languageLevel = LanguageLevel.ofRuntime();
        private Charset encoding = StandardCharsets.UTF_8;

        @Override
        public StarExpanderBuilder classpath(ExpandClasspath classpath) {
            this.classpath = Objects.requireNonNull(classpath, "classpath");
            return this;
        }

        @Override
        public StarExpanderBuilder languageLevel(LanguageLevel languageLevel) {
            this.languageLevel = Objects.requireNonNull(languageLevel, "languageLevel");
            return this;
        }

        @Override
        public StarExpanderBuilder encoding(Charset encoding) {
            this.encoding = Objects.requireNonNull(encoding, "encoding");
            return this;
        }

        @Override
        public StarExpander build() {
            return new DefaultStarExpander(classpath, languageLevel, encoding);
        }

    }

}
