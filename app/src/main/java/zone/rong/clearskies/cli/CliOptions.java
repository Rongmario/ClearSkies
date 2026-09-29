/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import zone.rong.clearskies.api.LanguageLevel;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The parsed command line.
 *
 * <p>Argument parsing is hand-written rather than delegated to a library: the CLI is meant to start
 * fast and to be one self-contained jar.
 */
public record CliOptions(
    Mode mode,
    List<Path> paths,
    List<Path> classpath,
    List<Path> sourcePath,
    List<String> includes,
    List<String> excludes,
    LanguageLevel languageLevel,
    Charset encoding,
    boolean readStdin,
    String stdinName,
    int parallelism,
    boolean verbose
) {

    /** What the CLI was asked to do. */
    public enum Mode {

        /** Rewrite files in place. */
        WRITE,

        /** Report files that would change, and exit non-zero if any would. */
        CHECK,

        /** Print a unified diff of what would change. */
        DIFF,

        /** Print usage and exit. */
        HELP,

        /** Print the version and exit. */
        VERSION

    }

    /** A malformed command line. */
    public static final class CliException extends RuntimeException {

        public CliException(String message) {
            super(message);
        }

    }

    public static CliOptions parse(String[] arguments) {
        Mode mode = Mode.CHECK;
        boolean modeGiven = false;
        List<Path> paths = new ArrayList<>();
        List<Path> classpath = new ArrayList<>();
        List<Path> sourcePath = new ArrayList<>();
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        LanguageLevel languageLevel = LanguageLevel.ofRuntime();
        Charset encoding = StandardCharsets.UTF_8;
        boolean readStdin = false;
        String stdinName = "<stdin>";
        int parallelism = Runtime.getRuntime().availableProcessors();
        boolean verbose = false;

        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            switch (argument) {
                case "--write", "-w" -> {
                    mode = Mode.WRITE;
                    modeGiven = true;
                }
                case "--check", "-c" -> {
                    mode = Mode.CHECK;
                    modeGiven = true;
                }
                case "--diff", "-d" -> {
                    mode = Mode.DIFF;
                    modeGiven = true;
                }
                case "--help", "-h" -> {
                    return helpOptions(Mode.HELP);
                }
                case "--version" -> {
                    return helpOptions(Mode.VERSION);
                }
                case "--classpath", "-cp" -> classpath.addAll(splitPaths(value(arguments, ++i, "--classpath")));
                case "--source-path" -> sourcePath.addAll(splitPaths(value(arguments, ++i, "--source-path")));
                case "--include" -> includes.add(value(arguments, ++i, "--include"));
                case "--exclude" -> excludes.add(value(arguments, ++i, "--exclude"));
                case "--release", "--language-level" -> languageLevel = LanguageLevel.ofRelease(intValue(arguments, ++i, "--release"));
                case "--encoding" -> encoding = Charset.forName(value(arguments, ++i, "--encoding"));
                case "--stdin" -> readStdin = true;
                case "--stdin-name" -> {
                    stdinName = value(arguments, ++i, "--stdin-name");
                    readStdin = true;
                }
                case "-j", "--jobs" -> parallelism = Math.max(1, intValue(arguments, ++i, "--jobs"));
                case "--verbose", "-v" -> verbose = true;
                default -> {
                    if (argument.startsWith("-") && argument.length() > 1) {
                        throw new CliException("Unknown option '" + argument + "'. Try --help.");
                    }
                    paths.add(Path.of(argument));
                }
            }
        }

        if (paths.isEmpty() && !readStdin) {
            throw new CliException("Nothing to expand. Pass one or more paths, or --stdin. Try --help.");
        }
        if (readStdin && !modeGiven) {
            mode = Mode.WRITE;
        }
        return new CliOptions(
            mode,
            List.copyOf(paths),
            List.copyOf(classpath),
            List.copyOf(sourcePath),
            List.copyOf(includes),
            List.copyOf(excludes),
            languageLevel,
            encoding,
            readStdin,
            stdinName,
            parallelism,
            verbose
        );
    }

    private static CliOptions helpOptions(Mode mode) {
        return new CliOptions(
            mode,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            LanguageLevel.ofRuntime(),
            StandardCharsets.UTF_8,
            false,
            "<stdin>",
            1,
            false
        );
    }

    private static List<Path> splitPaths(String raw) {
        List<Path> paths = new ArrayList<>();
        for (String part : raw.split(java.io.File.pathSeparator, -1)) {
            if (!part.isBlank()) {
                paths.add(Path.of(part));
            }
        }
        return paths;
    }

    private static String value(String[] arguments, int index, String option) {
        if (index >= arguments.length) {
            throw new CliException(option + " expects a value");
        }
        return arguments[index];
    }

    private static int intValue(String[] arguments, int index, String option) {
        String raw = value(arguments, index, option);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new CliException(option + " expects a number, got '" + raw + "'");
        }
    }

    /** The usage text printed by {@code --help}. */
    public static String usage() {
        return """
                clearskies - expand Java star imports into the types the file actually uses

                USAGE
                  clearskies [options] <path>...
                  clearskies --stdin [--stdin-name Foo.java] [options]

                MODES
                  -c, --check           report files that would change, exit 1 if any would (default)
                  -w, --write           rewrite files in place
                  -d, --diff            print a unified diff of what would change
                  -h, --help            print this help
                      --version         print the version

                RESOLUTION
                  -cp, --classpath PATH compile classpath, javac-style (system path separator)
                      --source-path PATH extra source roots for same-tree types
                      --release N       javac --release, e.g. 21 (default: running JDK)
                      --encoding NAME   charset used to read and write files (default: UTF-8)

                FILES
                      --include GLOB    only expand paths matching this glob, repeatable
                      --exclude GLOB    skip paths matching this glob, repeatable
                      --stdin           read source from standard input, write to standard output
                      --stdin-name NAME name used for diagnostics when reading standard input

                OTHER
                  -j, --jobs N          files to expand in parallel (default: available processors)
                  -v, --verbose         report every file and every diagnostic

                EXIT CODES
                  0  success: nothing to do, or the requested rewrite succeeded
                  1  files would change (--check and --diff only)
                  2  an error occurred

                Directory arguments are walked for *.java and are also used as source roots.
                import static ….* is left untouched.
                """;
    }

}
