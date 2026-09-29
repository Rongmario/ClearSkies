/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.maven;

import zone.rong.clearskies.api.LanguageLevel;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Reads javac release and encoding from Maven compiler configuration. */
final class MavenCompilerSettings {

    private MavenCompilerSettings() { }

    static LanguageLevel languageLevel(Integer override, MavenProject project) {
        if (override != null) {
            return LanguageLevel.ofRelease(override);
        }
        Integer release = firstInteger(
            property(project, "maven.compiler.release"),
            pluginChild(project, "release"),
            property(project, "maven.compiler.source"),
            pluginChild(project, "source")
        );
        return release == null ? LanguageLevel.ofRuntime() : LanguageLevel.ofRelease(release);
    }

    static Charset encoding(String override, MavenProject project) {
        if (override != null && !override.isBlank()) {
            return Charset.forName(override);
        }
        String encoding = firstNonBlank(
            pluginChild(project, "encoding"),
            property(project, "project.build.sourceEncoding"),
            property(project, "maven.compiler.encoding")
        );
        return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    static LanguageLevel languageLevel(Integer override, Properties properties) {
        if (override != null) {
            return LanguageLevel.ofRelease(override);
        }
        Integer release = firstInteger(properties.getProperty("maven.compiler.release"), properties.getProperty("maven.compiler.source"));
        return release == null ? LanguageLevel.ofRuntime() : LanguageLevel.ofRelease(release);
    }

    private static String property(MavenProject project, String name) {
        Object value = project.getProperties().get(name);
        return value == null ? null : value.toString();
    }

    private static String pluginChild(MavenProject project, String name) {
        for (Plugin plugin : project.getBuildPlugins()) {
            if (!"maven-compiler-plugin".equals(plugin.getArtifactId())) {
                continue;
            }
            Object configuration = plugin.getConfiguration();
            if (configuration == null) {
                continue;
            }
            try {
                Method getChild = configuration.getClass().getMethod("getChild", String.class);
                Object child = getChild.invoke(configuration, name);
                if (child == null) {
                    continue;
                }
                Method getValue = child.getClass().getMethod("getValue");
                Object value = getValue.invoke(child);
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer firstInteger(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String digits = value.trim();
            if (digits.startsWith("1.")) {
                digits = digits.substring(2);
            }
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

}
