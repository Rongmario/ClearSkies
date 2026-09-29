/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.maven;

import zone.rong.clearskies.api.Diagnostic;
import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.LanguageLevel;
import zone.rong.clearskies.api.StarExpander;
import zone.rong.clearskies.core.AtomicFiles;
import zone.rong.clearskies.core.ClearSkies;
import zone.rong.clearskies.core.PathGlobs;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared configuration and file walking for the ClearSkies goals.
 *
 * <p>Both goals run the same engine over the same files and differ only in what they do with a file
 * that would change, which is the point: {@code clearskies:check} in CI can never disagree with
 * {@code clearskies:apply} on a developer's machine.
 */
abstract class AbstractClearSkiesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    protected MojoExecution mojoExecution;

    /** Globs limiting which files are expanded. Empty means every Java source. */
    @Parameter
    protected List<String> includes = new ArrayList<>();

    /** Globs excluding files from expansion. */
    @Parameter
    protected List<String> excludes = new ArrayList<>();

    /** Whether to expand test sources as well as main sources. */
    @Parameter(property = "clearskies.includeTestSources", defaultValue = "true")
    protected boolean includeTestSources;

    /**
     * Java release passed to javac as {@code --release}. When unset, uses {@code maven.compiler.release},
     * then the compiler plugin {@code source}, then the running JDK.
     */
    @Parameter(property = "clearskies.languageLevel")
    protected Integer languageLevel;

    /** Encoding of the source files. */
    @Parameter(property = "clearskies.encoding", defaultValue = "${project.build.sourceEncoding}")
    protected String encoding;

    /** Skips the goal entirely. */
    @Parameter(property = "clearskies.skip", defaultValue = "false")
    protected boolean skip;

    /** Whether a file that would change is rewritten, or merely reported. */
    protected abstract boolean checkOnly();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("ClearSkies is skipped");
            return;
        }
        Charset charset = MavenCompilerSettings.encoding(encoding, project);
        LanguageLevel level = MavenCompilerSettings.languageLevel(languageLevel, project);
        List<String> wouldChange = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int[] expanded = { 0 };
        int seen = 0;

        if (processMain()) {
            seen += process(mainFiles(), mainClasspath(), charset, level, wouldChange, failures, expanded);
        }
        if (processTests()) {
            seen += process(testFiles(), testClasspath(), charset, level, wouldChange, failures, expanded);
        }

        if (seen == 0) {
            getLog().info("ClearSkies found no Java sources");
        }
        if (!failures.isEmpty()) {
            throw new MojoExecutionException("ClearSkies could not expand " + failures.size() + " file(s):\n" + String.join("\n", failures));
        }
        if (!wouldChange.isEmpty()) {
            throw new MojoFailureException(
                "ClearSkies found " + wouldChange.size() + " file(s) with expandable star imports or incomplete attribution. Run clearskies:apply.\n" + String.join("\n", wouldChange)
            );
        }
        getLog().info("ClearSkies checked " + seen + " file(s), expanded " + expanded[0]);
    }

    static boolean processMainSources(String phase) {
        return !"process-test-sources".equals(phase);
    }

    static boolean processTestSources(boolean includeTestSources, String phase) {
        return includeTestSources && !"process-sources".equals(phase);
    }

    private boolean processMain() {
        return processMainSources(phase());
    }

    private boolean processTests() {
        return processTestSources(includeTestSources, phase());
    }

    private String phase() {
        return mojoExecution == null ? null : mojoExecution.getLifecyclePhase();
    }

    private int process(
        List<Path> files,
        ExpandClasspath classpath,
        Charset charset,
        LanguageLevel level,
        List<String> wouldChange,
        List<String> failures,
        int[] expanded
    ) throws MojoExecutionException {
        if (files.isEmpty()) {
            return 0;
        }
        StarExpander expander = ClearSkies.newExpander().classpath(classpath).languageLevel(level).encoding(charset).build();
        for (Path file : files) {
            String source;
            try {
                source = Files.readString(file, charset);
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot read " + file, e);
            }
            ExpandResult result = expander.expand(ExpandRequest.of(source).withName(file.toString()));
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (result.outcome() == ExpandResult.Outcome.FAILED) {
                    failures.add(diagnostic.format(file.toString()));
                } else {
                    getLog().debug(diagnostic.format(file.toString()));
                }
            }
            switch (result.outcome()) {
                case FAILED -> {
                    if (result.diagnostics().isEmpty()) {
                        failures.add(file + ": expansion abandoned");
                    }
                }
                case INCOMPLETE -> {
                    if (checkOnly()) {
                        wouldChange.add(file.toString());
                    } else if (!result.isUnchanged()) {
                        write(file, result.text(), charset);
                        expanded[0]++;
                        getLog().info("Expanded " + file);
                    }
                }
                case EXPANDED -> {
                    if (checkOnly()) {
                        wouldChange.add(file.toString());
                    } else {
                        write(file, result.text(), charset);
                        expanded[0]++;
                        getLog().info("Expanded " + file);
                    }
                }
                default -> {
                }
            }
        }
        return files.size();
    }

    private void write(Path file, String text, Charset charset) throws MojoExecutionException {
        try {
            AtomicFiles.writeString(file, text, charset);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write " + file, e);
        }
    }

    private ExpandClasspath mainClasspath() throws MojoExecutionException {
        return classpath(compileClasspathElements(), existingRoots(project.getCompileSourceRoots()));
    }

    private ExpandClasspath testClasspath() throws MojoExecutionException {
        List<String> roots = new ArrayList<>(existingRoots(project.getTestCompileSourceRoots()));
        roots.addAll(existingRoots(project.getCompileSourceRoots()));
        return classpath(testClasspathElements(), roots);
    }

    private ExpandClasspath classpath(List<String> entries, List<String> roots) {
        List<Path> jars = new ArrayList<>();
        for (String entry : entries) {
            Path path = Path.of(entry);
            if (Files.exists(path)) {
                jars.add(path);
            }
        }
        List<Path> sourceRoots = new ArrayList<>();
        for (String root : roots) {
            sourceRoots.add(Path.of(root));
        }
        return ExpandClasspath.of(jars).withSourceRoots(sourceRoots);
    }

    private List<String> compileClasspathElements() throws MojoExecutionException {
        try {
            return project.getCompileClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Cannot resolve compile classpath", e);
        }
    }

    private List<String> testClasspathElements() throws MojoExecutionException {
        try {
            return project.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Cannot resolve test classpath", e);
        }
    }

    private List<Path> mainFiles() throws MojoExecutionException {
        return sourceFiles(project.getCompileSourceRoots());
    }

    private List<Path> testFiles() throws MojoExecutionException {
        return sourceFiles(project.getTestCompileSourceRoots());
    }

    protected List<Path> sourceFiles(List<String> roots) throws MojoExecutionException {
        Path base = project.getBasedir() == null ? Path.of("").toAbsolutePath() : project.getBasedir().toPath();
        List<Path> files = new ArrayList<>();
        for (String root : roots) {
            Path directory = Path.of(root);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> PathGlobs.allowed(path, base, includes, excludes))
                    .sorted()
                    .forEach(files::add);
            } catch (IOException | UncheckedIOException e) {
                throw new MojoExecutionException("Cannot walk " + directory, e);
            }
        }
        return files;
    }

    private static List<String> existingRoots(List<String> roots) {
        List<String> existing = new ArrayList<>();
        for (String root : roots) {
            if (root != null && Files.exists(Path.of(root))) {
                existing.add(root);
            }
        }
        return existing;
    }

}
