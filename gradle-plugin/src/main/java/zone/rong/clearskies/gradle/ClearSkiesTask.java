/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.gradle;

import zone.rong.clearskies.api.Diagnostic;
import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.StarExpander;
import zone.rong.clearskies.core.AtomicFiles;
import zone.rong.clearskies.core.ClearSkies;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Expands or checks star imports in a set of Java sources, one source set at a time.
 */
@CacheableTask
public abstract class ClearSkiesTask extends DefaultTask {

    @Nested
    public abstract ListProperty<SourceSetWork> getTargets();

    /** When true the task reports files that would change instead of rewriting them. */
    @Input
    public abstract Property<Boolean> getCheckOnly();

    /**
     * A marker written when the task completes.
     *
     * <p>Expansion rewrites its own inputs and produces no other output, but Gradle needs an output
     * to track incremental state against, so the task declares this one.
     */
    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @TaskAction
    public void execute() {
        List<String> wouldChange = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int expanded = 0;

        for (SourceSetWork target : getTargets().get()) {
            Charset charset = Charset.forName(target.getEncoding().get());
            StarExpander expander = expander(target, charset);
            for (File file : target.getSource().getFiles()) {
                if (!file.isFile() || !file.getName().endsWith(".java")) {
                    continue;
                }
                ExpandResult result = expand(expander, file, charset);
                for (Diagnostic diagnostic : result.diagnostics()) {
                    if (result.outcome() == ExpandResult.Outcome.FAILED) {
                        failures.add(diagnostic.format(file.getPath()));
                    } else {
                        getLogger().info("{}", diagnostic.format(file.getPath()));
                    }
                }
                switch (result.outcome()) {
                    case FAILED -> {
                        if (result.diagnostics().isEmpty()) {
                            failures.add(file.getPath() + ": expansion abandoned");
                        }
                    }
                    case INCOMPLETE -> {
                        if (getCheckOnly().get()) {
                            wouldChange.add(file.getPath());
                        } else if (!result.isUnchanged()) {
                            write(file.toPath(), result.text(), charset);
                            expanded++;
                            getLogger().lifecycle("expanded {}", file.getPath());
                        }
                    }
                    case EXPANDED -> {
                        if (getCheckOnly().get()) {
                            wouldChange.add(file.getPath());
                        } else {
                            write(file.toPath(), result.text(), charset);
                            expanded++;
                            getLogger().lifecycle("expanded {}", file.getPath());
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            throw new GradleException("ClearSkies could not expand " + failures.size() + " file(s):\n" + String.join("\n", failures));
        }
        if (!wouldChange.isEmpty()) {
            throw new GradleException(
                "ClearSkies found " + wouldChange.size() + " file(s) with expandable star imports or incomplete attribution. Run clearSkiesApply.\n" + String.join("\n", wouldChange)
            );
        }
        getLogger().info("ClearSkies expanded {} file(s)", expanded);
        writeMarker(expanded);
    }

    private StarExpander expander(SourceSetWork target, Charset charset) {
        List<Path> classpath = target.getClasspath().getFiles().stream().map(File::toPath).filter(Files::exists).toList();
        List<Path> sourceRoots = target.getSourceRoots().getFiles().stream().map(File::toPath).filter(Files::exists).toList();
        return ClearSkies.newExpander()
            .classpath(ExpandClasspath.of(classpath).withSourceRoots(sourceRoots))
            .languageLevel(target.getLanguageLevel().get())
            .encoding(charset)
            .build();
    }

    private void writeMarker(int expanded) {
        Path marker = getMarkerFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "expanded " + expanded + " file(s)\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + marker, e);
        }
    }

    private static ExpandResult expand(StarExpander expander, File file, Charset charset) {
        try {
            String source = Files.readString(file.toPath(), charset);
            return expander.expand(ExpandRequest.of(source).withName(file.getPath()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static void write(Path file, String text, Charset charset) {
        try {
            AtomicFiles.writeString(file, text, charset);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

}
