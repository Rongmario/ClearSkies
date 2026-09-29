/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A line-based unified diff, used by {@code --diff}.
 *
 * <p>Small on purpose: the CLI needs to show what would change, not to be a diff library. Equal
 * lines are matched in order with O(n) memory so a large source does not allocate an
 * {@code O(lines²)} matrix.
 */
public final class UnifiedDiff {

    private static final int CONTEXT = 3;
    private static final String NO_NEWLINE = "\\ No newline at end of file";

    private UnifiedDiff() { }

    /** A unified diff of two texts, or an empty string when they are identical. */
    public static String between(String name, String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        Split left = Split.of(before);
        Split right = Split.of(after);
        List<String> body = diffLines(left.lines, right.lines);
        if (left.newlineAtEnd != right.newlineAtEnd && !body.isEmpty()) {
            int last = body.size() - 1;
            if (!right.newlineAtEnd && body.get(last).startsWith("+")) {
                body.add(NO_NEWLINE);
            } else if (!left.newlineAtEnd && body.get(last).startsWith("-")) {
                body.add(NO_NEWLINE);
            } else if (!left.newlineAtEnd || !right.newlineAtEnd) {
                body.add(NO_NEWLINE);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("--- ").append(name).append('\n');
        out.append("+++ ").append(name).append(" (expanded)\n");
        appendHunks(out, body);
        return out.toString();
    }

    static List<String> diffLines(List<String> left, List<String> right) {
        Map<String, ArrayDeque<Integer>> remaining = new HashMap<>();
        for (int j = 0; j < right.size(); j++) {
            remaining.computeIfAbsent(right.get(j), key -> new ArrayDeque<>()).add(j);
        }
        boolean[] takeLeft = new boolean[left.size()];
        boolean[] takeRight = new boolean[right.size()];
        int cursor = 0;
        for (int i = 0; i < left.size(); i++) {
            ArrayDeque<Integer> hits = remaining.get(left.get(i));
            if (hits == null) {
                continue;
            }
            while (!hits.isEmpty() && hits.peekFirst() < cursor) {
                hits.pollFirst();
            }
            Integer match = hits.pollFirst();
            if (match != null) {
                takeLeft[i] = true;
                takeRight[match] = true;
                cursor = match + 1;
            }
        }
        List<String> body = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;
        while (i < left.size() || j < right.size()) {
            while (j < right.size() && !takeRight[j]) {
                body.add("+" + right.get(j++));
            }
            while (i < left.size() && !takeLeft[i]) {
                body.add("-" + left.get(i++));
            }
            if (i < left.size() && j < right.size() && takeLeft[i] && takeRight[j]) {
                body.add(" " + left.get(i));
                i++;
                j++;
            }
        }
        return body;
    }

    private static void appendHunks(StringBuilder out, List<String> body) {
        int index = 0;
        int leftLine = 1;
        int rightLine = 1;
        while (index < body.size()) {
            if (body.get(index).startsWith(" ")) {
                leftLine++;
                rightLine++;
                index++;
                continue;
            }
            int start = Math.max(0, index - CONTEXT);
            int end = index;
            int trailing = 0;
            while (end < body.size() && trailing <= CONTEXT) {
                String line = body.get(end);
                if (line.startsWith("\\")) {
                    end++;
                    continue;
                }
                trailing = line.startsWith(" ") ? trailing + 1 : 0;
                end++;
            }
            int hunkLeftStart = leftLine - (index - start);
            int hunkRightStart = rightLine - (index - start);
            int leftCount = 0;
            int rightCount = 0;
            for (int k = start; k < end; k++) {
                char marker = body.get(k).charAt(0);
                if (marker == '\\') {
                    continue;
                }
                if (marker != '+') {
                    leftCount++;
                }
                if (marker != '-') {
                    rightCount++;
                }
            }
            out.append("@@ -")
                .append(Math.max(hunkLeftStart, 1))
                .append(',')
                .append(leftCount)
                .append(" +")
                .append(Math.max(hunkRightStart, 1))
                .append(',')
                .append(rightCount)
                .append(" @@\n");
            for (int k = start; k < end; k++) {
                out.append(body.get(k)).append('\n');
            }
            for (int k = index; k < end; k++) {
                char marker = body.get(k).charAt(0);
                if (marker == '\\') {
                    continue;
                }
                if (marker != '+') {
                    leftLine++;
                }
                if (marker != '-') {
                    rightLine++;
                }
            }
            index = end;
        }
    }

    private record Split(List<String> lines, boolean newlineAtEnd) {

        static Split of(String text) {
            boolean newlineAtEnd = text.endsWith("\n") || text.endsWith("\r");
            List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
            if (newlineAtEnd && !lines.isEmpty() && lines.getLast().isEmpty()) {
                lines.removeLast();
            }
            return new Split(List.copyOf(lines), newlineAtEnd);
        }

    }

}
