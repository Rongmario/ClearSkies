/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.gradle;

import zone.rong.clearskies.api.LanguageLevel;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies ClearSkies to a Gradle project.
 *
 * <p>Registers {@code clearSkiesApply} and {@code clearSkiesCheck}, and wires the check task into
 * {@code check} so a leftover star import fails the build the same way a failing test does.
 */
public class ClearSkiesPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "clearSkies";
    public static final String APPLY_TASK_NAME = "clearSkiesApply";
    public static final String CHECK_TASK_NAME = "clearSkiesCheck";
    public static final String TASK_GROUP = "verification";

    @Override
    public void apply(Project project) {
        ClearSkiesExtension extension = project.getExtensions().create(EXTENSION_NAME, ClearSkiesExtension.class);
        extension.getEnforceOnCheck().convention(true);

        ObjectFactory objects = project.getObjects();
        Provider<List<SourceSetWork>> targets = project.provider(() -> sourceSetWork(project, extension, objects));

        TaskProvider<ClearSkiesTask> apply = project.getTasks().register(APPLY_TASK_NAME, ClearSkiesTask.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription("Expands star imports in Java sources in place with ClearSkies.");
            configure(task, targets);
            task.getCheckOnly().set(false);
            task.getMarkerFile().set(project.getLayout().getBuildDirectory().file("clearskies/apply.marker"));
            task.getOutputs().doNotCacheIf("the task rewrites its source inputs in place", ignored -> true);
            task.getOutputs().upToDateWhen(ignored -> false);
        });

        TaskProvider<ClearSkiesTask> check = project.getTasks().register(CHECK_TASK_NAME, ClearSkiesTask.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription("Fails if any Java source still has expandable star imports.");
            configure(task, targets);
            task.getCheckOnly().set(true);
            task.getMarkerFile().set(project.getLayout().getBuildDirectory().file("clearskies/check.marker"));
        });

        project.getPlugins()
            .withType(
                LifecycleBasePlugin.class,
                ignored -> project.getTasks()
                    .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                    .configure(task -> task.dependsOn(project.provider(() -> extension.getEnforceOnCheck().get() ? List.of(check) : List.of())))
            );

        check.configure(task -> task.mustRunAfter(apply));
    }

    private static void configure(ClearSkiesTask task, Provider<List<SourceSetWork>> targets) {
        task.getTargets().addAll(targets);
    }

    private static List<SourceSetWork> sourceSetWork(Project project, ClearSkiesExtension extension, ObjectFactory objects) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return List.of();
        }
        SourceSetContainer sourceSets = java.getSourceSets();
        List<String> selected = extension.getSourceSets().getOrElse(List.of());
        SourceSet main = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        List<SourceSetWork> works = new ArrayList<>();
        for (SourceSet sourceSet : sourceSets) {
            if (!selected.isEmpty() && !selected.contains(sourceSet.getName())) {
                continue;
            }
            SourceSetWork work = objects.newInstance(SourceSetWork.class, sourceSet.getName());
            work.getSource()
                .from(
                    sourceSet.getAllJava()
                        .matching(patterns -> patterns.include(extension.getIncludes().get()).exclude(extension.getExcludes().get()))
                        .filter(file -> file.getName().endsWith(".java"))
                );
            work.getClasspath().from(existing(sourceSet.getCompileClasspath()));
            work.getSourceRoots().from(existing(sourceSet.getAllJava().getSourceDirectories()));
            if (main != null && !SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                work.getSourceRoots().from(existing(main.getAllJava().getSourceDirectories()));
            }
            JavaCompile compile = (JavaCompile) project.getTasks().findByName(sourceSet.getCompileJavaTaskName());
            if (extension.getLanguageLevel().isPresent()) {
                work.getLanguageLevel().set(extension.getLanguageLevel());
            } else {
                work.getLanguageLevel().set(languageLevelOf(compile));
            }
            if (extension.getEncoding().isPresent()) {
                work.getEncoding().set(extension.getEncoding());
            } else {
                work.getEncoding().set(encodingOf(compile));
            }
            works.add(work);
        }
        return works;
    }

    static LanguageLevel languageLevelOf(JavaCompile compile) {
        if (compile != null) {
            Integer release = compile.getOptions().getRelease().getOrNull();
            if (release != null) {
                return LanguageLevel.ofRelease(release);
            }
            String compatibility = compile.getSourceCompatibility();
            LanguageLevel fromCompat = fromCompatibility(compatibility);
            if (fromCompat != null) {
                return fromCompat;
            }
        }
        return LanguageLevel.ofRuntime();
    }

    static String encodingOf(JavaCompile compile) {
        if (compile != null) {
            String encoding = compile.getOptions().getEncoding();
            if (encoding != null && !encoding.isBlank()) {
                return encoding;
            }
        }
        return StandardCharsets.UTF_8.name();
    }

    private static LanguageLevel fromCompatibility(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.trim();
        if (digits.startsWith("1.")) {
            digits = digits.substring(2);
        }
        try {
            return LanguageLevel.ofRelease(Integer.parseInt(digits));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static FileCollection existing(FileCollection files) {
        return files.filter(file -> file.exists());
    }

}
