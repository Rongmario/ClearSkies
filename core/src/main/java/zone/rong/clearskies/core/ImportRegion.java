package zone.rong.clearskies.core;

/**
 * Cheap scan of the import region. Used to skip javac when a file cannot contain a star import, and
 * to skip {@code module-info.java} entirely.
 *
 * <p>Comments and strings in the import region are skipped, so a star in a comment is not a hit. The
 * scan stops at the first type or module declaration.
 */
final class ImportRegion {

    private ImportRegion() { }

    /** Whether this file should be returned unchanged without running javac. */
    static boolean skip(String source, String name) {
        if (isModuleInfo(name)) {
            return true;
        }
        return !hasStarImport(UnicodeEscapes.translate(source));
    }

    static boolean isModuleInfo(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String file = slash >= 0 ? name.substring(slash + 1) : name;
        return file.equals("module-info.java");
    }

    static boolean hasStarImport(String source) {
        Scanner scanner = new Scanner(source);
        scanner.skipWhitespaceAndComments();
        while (scanner.peek() == '@') {
            scanner.skipAnnotation();
            scanner.skipWhitespaceAndComments();
        }
        if (scanner.matchKeyword("package")) {
            scanner.skipToSemicolon();
            scanner.skipWhitespaceAndComments();
        }
        while (!scanner.eof()) {
            scanner.skipWhitespaceAndComments();
            if (scanner.eof()) {
                return false;
            }
            if (scanner.peek() == '@') {
                scanner.skipAnnotation();
                continue;
            }
            if (scanner.matchKeyword("import")) {
                scanner.skipWhitespaceAndComments();
                if (scanner.matchKeyword("module")) {
                    scanner.skipToSemicolon();
                    continue;
                }
                if (scanner.matchKeyword("static")) {
                    scanner.skipWhitespaceAndComments();
                }
                if (scanner.scanOnDemand()) {
                    return true;
                }
                continue;
            }
            return false;
        }
        return false;
    }

    private static final class Scanner {

        private final String source;
        private int index;

        Scanner(String source) {
            this.source = source;
            if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
                this.index = 1;
            }
        }

        boolean eof() {
            return index >= source.length();
        }

        char peek() {
            return eof() ? 0 : source.charAt(index);
        }

        void skipWhitespaceAndComments() {
            while (!eof()) {
                char c = peek();
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
                    index++;
                    continue;
                }
                if (c == '/' && index + 1 < source.length()) {
                    char next = source.charAt(index + 1);
                    if (next == '/') {
                        index += 2;
                        while (!eof() && peek() != '\n' && peek() != '\r') {
                            index++;
                        }
                        continue;
                    }
                    if (next == '*') {
                        index += 2;
                        while (index + 1 < source.length()
                                && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                            index++;
                        }
                        if (index + 1 < source.length()) {
                            index += 2;
                        } else {
                            index = source.length();
                        }
                        continue;
                    }
                }
                return;
            }
        }

        boolean matchKeyword(String keyword) {
            if (!startsWithIdentifier(keyword)) {
                return false;
            }
            int after = index + keyword.length();
            if (after < source.length() && Character.isJavaIdentifierPart(source.charAt(after))) {
                return false;
            }
            index = after;
            return true;
        }

        private boolean startsWithIdentifier(String keyword) {
            if (index + keyword.length() > source.length()) {
                return false;
            }
            return source.regionMatches(index, keyword, 0, keyword.length());
        }

        boolean scanOnDemand() {
            skipWhitespaceAndComments();
            if (!skipIdentifier()) {
                skipToSemicolon();
                return false;
            }
            while (true) {
                skipWhitespaceAndComments();
                if (peek() != '.') {
                    skipToSemicolon();
                    return false;
                }
                index++;
                skipWhitespaceAndComments();
                if (peek() == '*') {
                    index++;
                    return true;
                }
                if (!skipIdentifier()) {
                    skipToSemicolon();
                    return false;
                }
            }
        }

        boolean skipIdentifier() {
            if (eof() || !Character.isJavaIdentifierStart(peek())) {
                return false;
            }
            index++;
            while (!eof() && Character.isJavaIdentifierPart(peek())) {
                index++;
            }
            return true;
        }

        void skipToSemicolon() {
            while (!eof() && peek() != ';') {
                char c = peek();
                if (c == '"' || c == '\'') {
                    skipQuoted(c);
                    continue;
                }
                if (c == '/' && index + 1 < source.length()) {
                    int saved = index;
                    skipWhitespaceAndComments();
                    if (index == saved) {
                        index++;
                    }
                    continue;
                }
                index++;
            }
            if (!eof() && peek() == ';') {
                index++;
            }
        }

        void skipAnnotation() {
            if (peek() != '@') {
                return;
            }
            index++;
            skipWhitespaceAndComments();
            if (!skipIdentifier()) {
                return;
            }
            while (true) {
                skipWhitespaceAndComments();
                if (peek() != '.') {
                    break;
                }
                index++;
                skipWhitespaceAndComments();
                if (!skipIdentifier()) {
                    break;
                }
            }
            skipWhitespaceAndComments();
            if (peek() == '(') {
                skipBalanced('(', ')');
            }
        }

        private void skipBalanced(char open, char close) {
            if (peek() != open) {
                return;
            }
            int depth = 0;
            while (!eof()) {
                char c = peek();
                if (c == '"' || c == '\'') {
                    skipQuoted(c);
                    continue;
                }
                if (c == open) {
                    depth++;
                    index++;
                    continue;
                }
                if (c == close) {
                    depth--;
                    index++;
                    if (depth == 0) {
                        return;
                    }
                    continue;
                }
                index++;
            }
        }

        private void skipQuoted(char quote) {
            index++;
            while (!eof()) {
                char c = source.charAt(index++);
                if (c == '\\' && !eof()) {
                    index++;
                    continue;
                }
                if (c == quote) {
                    return;
                }
            }
        }

    }

}
