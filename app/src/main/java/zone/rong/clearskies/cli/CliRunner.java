/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import zone.rong.clearskies.api.Diagnostic;
import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.StarExpander;
import zone.rong.clearskies.core.AtomicFiles;
import zone.rong.clearskies.core.ClearSkies;
import zone.rong.clearskies.core.PathGlobs;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs one CLI invocation. Kept separate from {@link Main} so it can be tested without exiting. */
final class CliRunner {

    static final int SUCCESS = 0;
    static final int WOULD_CHANGE = 1;
    static final int ERROR = 2;

    private final CliOptions options;
    private final PrintStream out;
    private final PrintStream err;
    private final InputStream in;

    CliRunner(CliOptions options, PrintStream out, PrintStream err, InputStream in) {
        this.options = options;
        this.out = out;
        this.err = err;
        this.in = in;
    }

    int run() {
        return switch (options.mode()) {
            case HELP -> {
                out.print(CliOptions.usage());
                yield SUCCESS;
            }
            case VERSION -> {
                out.println("clearskies " + version());
                yield SUCCESS;
            }
            case WRITE, CHECK, DIFF -> options.readStdin() ? runStandardInput() : runFiles();
        };
    }

    private int runStandardInput() {
        String source;
        try {
            source = options.encoding()
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(in.readAllBytes()))
                .toString();
        } catch (CharacterCodingException e) {
            err.println("clearskies: standard input is not valid " + options.encoding().name() + "; pass --encoding");
            return ERROR;
        } catch (IOException e) {
            err.println("clearskies: cannot read standard input: " + e.getMessage());
            return ERROR;
        }
        ExpandResult result = expander(List.of()).expand(ExpandRequest.of(source).withName(options.stdinName()));
        reportDiagnostics(options.stdinName(), result);
        return finishOne(options.stdinName(), source, result, false);
    }

    private int runFiles() {
        List<Path> files;
        try {
            files = discover();
        } catch (IOException e) {
            err.println("clearskies: " + e.getMessage());
            return ERROR;
        }
        if (files.isEmpty()) {
            err.println("clearskies: no Java sources matched");
            // Patterns that match nothing are almost always a typo, and passing would hide it in CI.
            return options.includes().isEmpty() && options.excludes().isEmpty() ? SUCCESS : ERROR;
        }

        StarExpander expander = expander(sourceRootsFor(files));
        AtomicInteger changed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger incomplete = new AtomicInteger();
        List<Future<?>> pending = new ArrayList<>(files.size());
        try (ExecutorService pool = Executors.newFixedThreadPool(options.parallelism())) {
            for (Path file : files) {
                pending.add(pool.submit(() -> processFile(file, expander, changed, failed, incomplete)));
            }
            for (Future<?> future : pending) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ERROR;
                } catch (ExecutionException e) {
                    err.println("clearskies: " + e.getCause().getMessage());
                    failed.incrementAndGet();
                }
            }
        }

        if (options.verbose()) {
            err.println("clearskies: " + files.size() + " files, " + changed.get() + " changed, " + failed.get() + " failed");
        }
        if (failed.get() > 0) {
            return ERROR;
        }
        if (options.mode() != CliOptions.Mode.WRITE && (changed.get() > 0 || incomplete.get() > 0)) {
            return WOULD_CHANGE;
        }
        return SUCCESS;
    }

    private void processFile(Path file, StarExpander expander, AtomicInteger changed, AtomicInteger failed, AtomicInteger incomplete) {
        String source;
        try {
            source = Files.readString(file, options.encoding());
        } catch (CharacterCodingException e) {
            err.println("clearskies: " + file + " is not valid " + options.encoding().name() + "; pass --encoding");
            failed.incrementAndGet();
            return;
        } catch (IOException e) {
            err.println("clearskies: cannot read " + file + ": " + e.getMessage());
            failed.incrementAndGet();
            return;
        }

        ExpandResult result = expander.expand(ExpandRequest.of(source).withName(file.toString()));
        reportDiagnostics(file.toString(), result);
        int code = finishOne(file.toString(), source, result, true);
        if (code == ERROR) {
            failed.incrementAndGet();
        } else if (result.outcome() == ExpandResult.Outcome.INCOMPLETE) {
            incomplete.incrementAndGet();
            if (!result.isUnchanged()) {
                changed.incrementAndGet();
            }
        } else if (code == WOULD_CHANGE || (options.mode() == CliOptions.Mode.WRITE && !result.isUnchanged())) {
            changed.incrementAndGet();
        }
    }

    private int finishOne(String name, String source, ExpandResult result, boolean writeToDisk) {
        return switch (result.outcome()) {
            case FAILED -> ERROR;
            case UNCHANGED -> {
                if (!writeToDisk && options.mode() == CliOptions.Mode.WRITE) {
                    out.print(source);
                }
                if (options.verbose()) {
                    out.println("unchanged " + name);
                }
                yield SUCCESS;
            }
            case INCOMPLETE, EXPANDED -> {
                if (!applyOutput(name, source, result, writeToDisk)) {
                    yield ERROR;
                }
                yield options.mode() == CliOptions.Mode.WRITE ? SUCCESS : WOULD_CHANGE;
            }
        };
    }

    private boolean applyOutput(String name, String source, ExpandResult result, boolean writeToDisk) {
        switch (options.mode()) {
            case WRITE -> {
                if (result.isUnchanged()) {
                    if (!writeToDisk) {
                        out.print(source);
                    }
                    return true;
                }
                if (writeToDisk) {
                    try {
                        AtomicFiles.writeString(Path.of(name), result.text(), options.encoding());
                        out.println("expanded " + name);
                    } catch (IOException e) {
                        err.println("clearskies: cannot write " + name + ": " + e.getMessage());
                        return false;
                    }
                } else {
                    out.print(result.text());
                }
            }
            case DIFF -> out.print(UnifiedDiff.between(name, source, result.text()));
            default -> {
                if (!result.isUnchanged() || result.outcome() == ExpandResult.Outcome.INCOMPLETE) {
                    out.println(name);
                }
            }
        }
        return true;
    }

    private void reportDiagnostics(String name, ExpandResult result) {
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == Diagnostic.Severity.INFO && !options.verbose()) {
                continue;
            }
            err.println(diagnostic.format(name));
        }
    }

    private StarExpander expander(List<Path> extraSourceRoots) {
        List<Path> roots = new ArrayList<>(options.sourcePath());
        roots.addAll(extraSourceRoots);
        return ClearSkies.newExpander()
            .classpath(ExpandClasspath.of(options.classpath()).withSourceRoots(roots))
            .languageLevel(options.languageLevel())
            .encoding(options.encoding())
            .keep(options.keep())
            .expandStaticImports(options.expandStaticImports())
            .build();
    }

    private List<Path> sourceRootsFor(List<Path> files) {
        Set<Path> roots = new LinkedHashSet<>();
        for (Path path : options.sourcePath()) {
            if (Files.exists(path)) {
                roots.add(path);
            }
        }
        for (Path path : options.paths()) {
            if (Files.isDirectory(path)) {
                roots.add(path);
            }
        }
        return List.copyOf(roots);
    }

    private List<Path> discover() throws IOException {
        Path root = Path.of("").toAbsolutePath().normalize();
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        for (Path path : options.paths()) {
            if (!Files.exists(path)) {
                throw new IOException("no such file or directory: " + path);
            }
            if (Files.isRegularFile(path)) {
                Path normalized = path.toAbsolutePath().normalize();
                if (PathGlobs.allowed(normalized, List.of(root), options.includes(), options.excludes())) {
                    files.add(relativeToWorkingDirectory(normalized, root));
                }
                continue;
            }
            List<Path> roots = List.of(root, path);
            try (var walk = Files.walk(path)) {
                walk.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .sorted()
                    .map(candidate -> candidate.toAbsolutePath().normalize())
                    .filter(candidate -> PathGlobs.allowed(candidate, roots, options.includes(), options.excludes()))
                    .map(candidate -> relativeToWorkingDirectory(candidate, root))
                    .forEach(files::add);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
        return List.copyOf(files);
    }

    private static Path relativeToWorkingDirectory(Path file, Path root) {
        return file.startsWith(root) ? root.relativize(file) : file;
    }

    private static String version() {
        String version = CliRunner.class.getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;
    }

}
