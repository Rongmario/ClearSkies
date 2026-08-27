package zone.rong.clearskies.api;

import java.nio.charset.Charset;

/** Builds an immutable {@link StarExpander}. */
public interface StarExpanderBuilder {

    /** Compile classpath and source roots used to attribute each file. */
    StarExpanderBuilder classpath(ExpandClasspath classpath);

    /** Java release passed to javac as {@code --release}. Defaults to the running JDK. */
    StarExpanderBuilder languageLevel(LanguageLevel languageLevel);

    /**
     * Charset javac uses when it reads files from {@link ExpandClasspath#sourceRoots()}. The request
     * source itself is already a {@code String}. Defaults to UTF-8.
     */
    StarExpanderBuilder encoding(Charset encoding);

    StarExpander build();

}
