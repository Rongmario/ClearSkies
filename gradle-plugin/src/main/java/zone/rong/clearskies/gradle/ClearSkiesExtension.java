/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.gradle;

import zone.rong.clearskies.api.LanguageLevel;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import java.util.List;

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

    /** Ant-style patterns of the files to expand, relative to each source directory. Defaults to every file. */
    public abstract SetProperty<String> getIncludes();

    /** Ant-style patterns of the files to leave alone, relative to each source directory. */
    public abstract SetProperty<String> getExcludes();

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

    /**
     * Owners whose star imports are left alone: a package such as {@code org.lwjgl.opengl}, or a
     * type such as {@code org.junit.jupiter.api.Assertions}.
     */
    public abstract SetProperty<String> getKeep();

    /** Whether {@code import static pkg.Type.*;} is expanded too. Defaults to true. */
    public abstract Property<Boolean> getExpandStaticImports();

    /** Convenience for {@code sourceSets = listOf(...)}. */
    public void sourceSets(String... names) {
        getSourceSets().set(List.of(names));
    }

    /** Adds include patterns. */
    public void include(String... patterns) {
        getIncludes().addAll(patterns);
    }

    /** Adds exclude patterns. */
    public void exclude(String... patterns) {
        getExcludes().addAll(patterns);
    }

    /** Adds owners whose star imports are left alone. */
    public void keep(String... owners) {
        getKeep().addAll(owners);
    }

}
