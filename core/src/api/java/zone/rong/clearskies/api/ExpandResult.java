/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of expanding one request.
 *
 * <p>A failed result still carries usable text: ClearSkies returns the original source rather than a
 * half-rewritten file, so a caller writing {@link #text()} back to disk can never corrupt it.
 */
public final class ExpandResult {

    /** What happened while expanding one file. */
    public enum Outcome {

        /** The source had nothing to expand. */
        UNCHANGED,
        /** Stars were rewritten to the types the file uses. */
        EXPANDED,
        /**
         * Attribution finished, but a star was left, a name could not be assigned, or an unusual
         * import was skipped. {@link #text()} may be a partial rewrite or the original source.
         */
        INCOMPLETE,
        /** Expansion was abandoned. {@link #text()} is the original source. */
        FAILED

    }

    private final String text;
    private final boolean unchanged;
    private final Outcome outcome;
    private final List<Diagnostic> diagnostics;

    private ExpandResult(String text, boolean unchanged, Outcome outcome, List<Diagnostic> diagnostics) {
        this.text = Objects.requireNonNull(text, "text");
        this.unchanged = unchanged;
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.diagnostics = List.copyOf(diagnostics);
    }

    /** The source had nothing to expand; nothing needs writing. */
    public static ExpandResult unchanged(String source) {
        return new ExpandResult(source, true, Outcome.UNCHANGED, List.of());
    }

    /** The source was rewritten to {@code expanded}. */
    public static ExpandResult expanded(String source, String expanded) {
        if (source.equals(expanded)) {
            return unchanged(expanded);
        }
        return new ExpandResult(expanded, false, Outcome.EXPANDED, List.of());
    }

    /**
     * Attribution finished without a clean expansion. {@code text} is a partial rewrite or the
     * original source.
     */
    public static ExpandResult incomplete(String source, String text, List<Diagnostic> diagnostics) {
        return new ExpandResult(text, source.equals(text), Outcome.INCOMPLETE, diagnostics);
    }

    /** Expansion was abandoned; the original source is returned with the reason attached. */
    public static ExpandResult failed(String source, List<Diagnostic> diagnostics) {
        return new ExpandResult(source, true, Outcome.FAILED, diagnostics);
    }

    /** The same result with extra diagnostics attached. */
    public ExpandResult withDiagnostics(List<Diagnostic> extra) {
        if (extra.isEmpty()) {
            return this;
        }
        List<Diagnostic> merged = new ArrayList<>(diagnostics);
        merged.addAll(extra);
        return new ExpandResult(text, unchanged, outcome, merged);
    }

    /** The text to write back: either the expanded source or, on failure, the original. */
    public String text() {
        return text;
    }

    /** Whether the text is identical to the input. */
    public boolean isUnchanged() {
        return unchanged;
    }

    /** How expansion finished. This is the source of truth for adapters. */
    public Outcome outcome() {
        return outcome;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * Whether expansion was abandoned and the original source is what {@link #text()} returns.
     *
     * <p>Ambiguous names and kept unused stars finish as {@link Outcome#INCOMPLETE}; they do not
     * set this flag.
     */
    public boolean hasErrors() {
        return outcome == Outcome.FAILED;
    }

}
