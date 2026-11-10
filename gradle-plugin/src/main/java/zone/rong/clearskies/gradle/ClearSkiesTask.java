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
import org.gradle.api.provider.SetProperty;
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

    /** Owners whose star imports are left alone. */
    @Input
    public abstract SetProperty<String> getKeep();

    /** Whether static star imports are expanded too. */
    @Input
    public abstract Property<Boolean> getExpandStaticImports();

    /**
     * A marker written when the task completes.
     *
     * <p>Expansion rewrites its own inputs and produces no other output, but Gradle needs an output
     * to track incremental state against, so the task declares this one.
     */
    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    private record Batch(Charset charset, StarExpander expander, List<File> files, List<ExpandRequest> requests) { }

    @TaskAction
    public void execute() {
        List<Batch> batches = new ArrayList<>();
        for (SourceSetWork target : getTargets().get()) {
            Charset charset = Charset.forName(target.getEncoding().get());
            Batch batch = new Batch(charset, expander(target, charset), new ArrayList<>(), new ArrayList<>());
            for (File file : target.getSource().getFiles()) {
                if (file.isFile() && file.getName().endsWith(".java")) {
                    batch.files.add(file);
                    batch.requests.add(request(file, charset));
                }
            }
            batches.add(batch);
        }
        // Gradle state is read above on the task thread. Only the expansion itself runs in parallel.
        List<List<ExpandResult>> results = batches.parallelStream().map(batch -> batch.expander.expandAll(batch.requests)).toList();

        List<String> wouldChange = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int expanded = 0;
        for (int b = 0; b < batches.size(); b++) {
            Batch batch = batches.get(b);
            for (int i = 0; i < batch.files.size(); i++) {
                File file = batch.files.get(i);
                ExpandResult result = results.get(b).get(i);
                boolean failed = result.outcome() == ExpandResult.Outcome.FAILED;
                for (Diagnostic diagnostic : result.diagnostics()) {
                    if (failed) {
                        failures.add(diagnostic.format(file.getPath()));
                    } else if (diagnostic.severity() == Diagnostic.Severity.INFO) {
                        getLogger().info("{}", diagnostic.format(file.getPath()));
                    } else {
                        getLogger().warn("{}", diagnostic.format(file.getPath()));
                    }
                }
                if (failed) {
                    if (result.diagnostics().isEmpty()) {
                        failures.add(file.getPath() + ": expansion abandoned");
                    }
                } else if (result.outcome() != ExpandResult.Outcome.UNCHANGED) {
                    if (getCheckOnly().get()) {
                        wouldChange.add(file.getPath());
                    } else if (!result.isUnchanged()) {
                        write(file.toPath(), result.text(), batch.charset);
                        expanded++;
                        getLogger().lifecycle("expanded {}", file.getPath());
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
            .keep(getKeep().get())
            .expandStaticImports(getExpandStaticImports().get())
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

    private static ExpandRequest request(File file, Charset charset) {
        try {
            return ExpandRequest.of(Files.readString(file.toPath(), charset)).withName(file.getPath());
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
