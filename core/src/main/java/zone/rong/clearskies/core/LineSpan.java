package zone.rong.clearskies.core;

/**
 * The physical source line occupied by one import declaration: indent, trailing comment, and the
 * terminator that originally followed it.
 */
final class LineSpan {

    final int start;
    final int end;
    final String indent;
    final String newline;
    final String trailingComment;

    private LineSpan(int start, int end, String indent, String newline, String trailingComment) {
        this.start = start;
        this.end = end;
        this.indent = indent;
        this.newline = newline;
        this.trailingComment = trailingComment;
    }

    /**
     * Extends a declaration span {@code [declStart, declEnd)} — typically from {@code import} through
     * {@code ;} — to the whole line it sits on.
     */
    static LineSpan of(String source, int declStart, int declEnd) {
        if (declStart < 0) {
            declStart = 0;
        }
        if (declEnd < declStart) {
            declEnd = declStart;
        }
        if (declEnd > source.length()) {
            declEnd = source.length();
        }

        int lineStart = declStart;
        while (lineStart > 0) {
            char previous = source.charAt(lineStart - 1);
            if (previous == '\n' || previous == '\r') {
                break;
            }
            lineStart--;
        }

        boolean indentIsWhitespace = true;
        for (int i = lineStart; i < declStart; i++) {
            char c = source.charAt(i);
            if (c != ' ' && c != '\t') {
                indentIsWhitespace = false;
                break;
            }
        }
        int start = indentIsWhitespace ? lineStart : declStart;
        String indent = indentIsWhitespace ? source.substring(lineStart, declStart) : "";

        int i = declEnd;
        String trailingComment = "";
        int spaces = i;
        while (i < source.length() && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) {
            i++;
        }
        if (i + 1 < source.length() && source.charAt(i) == '/' && source.charAt(i + 1) == '/') {
            while (i < source.length() && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
                i++;
            }
            trailingComment = source.substring(declEnd, i);
        } else if (i + 1 < source.length() && source.charAt(i) == '/' && source.charAt(i + 1) == '*') {
            int close = source.indexOf("*/", i + 2);
            if (close >= 0) {
                i = close + 2;
                trailingComment = source.substring(declEnd, i);
            } else {
                i = spaces;
            }
        } else {
            i = declEnd;
        }

        String newline = "";
        if (i < source.length() && source.charAt(i) == '\r') {
            if (i + 1 < source.length() && source.charAt(i + 1) == '\n') {
                newline = "\r\n";
                i += 2;
            } else {
                newline = "\r";
                i += 1;
            }
        } else if (i < source.length() && source.charAt(i) == '\n') {
            newline = "\n";
            i += 1;
        }
        return new LineSpan(start, i, indent, newline, trailingComment);
    }

    /**
     * Explicit imports that occupy this line's slot. Empty {@code fqns} means delete the line.
     * Trailing comment, if any, rides on the first inserted line.
     */
    String replacement(Iterable<String> fqns) {
        StringBuilder text = new StringBuilder();
        boolean first = true;
        int count = 0;
        for (String ignored : fqns) {
            count++;
        }
        if (count == 0) {
            return "";
        }
        String breakAfter = newline.isEmpty() ? "\n" : newline;
        int written = 0;
        for (String fqn : fqns) {
            text.append(indent).append("import ").append(fqn).append(';');
            if (first) {
                text.append(trailingComment);
                first = false;
            }
            written++;
            boolean last = written == count;
            if (!last) {
                text.append(breakAfter);
            } else if (!newline.isEmpty()) {
                text.append(newline);
            }
        }
        return text.toString();
    }

}
