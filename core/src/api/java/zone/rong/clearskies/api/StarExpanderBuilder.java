/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.api;

import java.nio.charset.Charset;
import java.util.Collection;

/**
 * Builds an immutable {@link StarExpander}. Obtain one from {@code ClearSkies.newExpander()}. This
 * interface is not meant to be implemented outside ClearSkies, and may gain methods in minor releases.
 */
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

    /**
     * Star imports to leave as they are, named by what precedes the {@code .*}: a package such as
     * {@code org.lwjgl.opengl}, or a type such as {@code org.junit.jupiter.api.Assertions}. A name
     * matches both the static and the non-static star of that owner. A trailing {@code .*} is
     * ignored. Defaults to none.
     */
    StarExpanderBuilder keep(Collection<String> owners);

    /** Whether {@code import static pkg.Type.*;} is expanded too. Defaults to true. */
    StarExpanderBuilder expandStaticImports(boolean expandStaticImports);

    StarExpander build();

}
