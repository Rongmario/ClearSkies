/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.api;

import java.util.Locale;
import java.util.Objects;

/** A problem ClearSkies hit while expanding one source file. */
public record Diagnostic(Diagnostic.Severity severity, String message, int line, int column) {

    /** How badly a diagnostic affects the result. */
    public enum Severity {

        /** Expansion was abandoned; the source is returned untouched. */
        ERROR,
        /** Expansion completed, but a star was left or a name could not be assigned. */
        WARNING,
        /** Informational only. */
        INFO

    }

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    public static Diagnostic error(String message) {
        return new Diagnostic(Severity.ERROR, message, 0, 0);
    }

    public static Diagnostic error(String message, int line, int column) {
        return new Diagnostic(Severity.ERROR, message, line, column);
    }

    public static Diagnostic warning(String message) {
        return new Diagnostic(Severity.WARNING, message, 0, 0);
    }

    public static Diagnostic warning(String message, int line, int column) {
        return new Diagnostic(Severity.WARNING, message, line, column);
    }

    public static Diagnostic info(String message) {
        return new Diagnostic(Severity.INFO, message, 0, 0);
    }

    /** Renders as {@code file:line:column: severity: message} when a name is known. */
    public String format(String name) {
        StringBuilder text = new StringBuilder();
        text.append(name == null || name.isEmpty() ? "<source>" : name);
        if (line > 0) {
            text.append(':').append(line);
            if (column > 0) {
                text.append(':').append(column);
            }
        }
        text.append(": ").append(severity.name().toLowerCase(Locale.ROOT)).append(": ").append(message);
        return text.toString();
    }

}
