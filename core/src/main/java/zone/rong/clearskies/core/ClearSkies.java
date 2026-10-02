/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.LanguageLevel;
import zone.rong.clearskies.api.StarExpander;
import zone.rong.clearskies.api.StarExpanderBuilder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        private Set<String> keep = Set.of();
        private boolean expandStaticImports = true;

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
        public StarExpanderBuilder keep(Collection<String> owners) {
            this.keep = owners.stream()
                .map(owner -> owner.endsWith(".*") ? owner.substring(0, owner.length() - 2) : owner)
                .collect(Collectors.toUnmodifiableSet());
            return this;
        }

        @Override
        public StarExpanderBuilder expandStaticImports(boolean expandStaticImports) {
            this.expandStaticImports = expandStaticImports;
            return this;
        }

        @Override
        public StarExpander build() {
            return new DefaultStarExpander(classpath, languageLevel, encoding, keep, expandStaticImports);
        }

    }

}
