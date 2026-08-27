package zone.rong.clearskies.core;

/**
 * JLS §3.3 Unicode escape translation.
 *
 * <p>javac applies this before scanning. The cheap import-region scan has to do the same or a
 * source that spells {@code import} as {@code \u0069mport} is skipped.
 */
final class UnicodeEscapes {

    private UnicodeEscapes() { }

    static String translate(String source) {
        int first = source.indexOf('\\');
        if (first < 0) {
            return source;
        }
        int length = source.length();
        StringBuilder out = new StringBuilder(length);
        out.append(source, 0, first);
        for (int i = first; i < length; ) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < length && source.charAt(i + 1) == 'u') {
                int j = i + 2;
                while (j < length && source.charAt(j) == 'u') {
                    j++;
                }
                if (j + 4 <= length) {
                    int value = hex4(source, j);
                    if (value >= 0) {
                        out.append((char) value);
                        i = j + 4;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static int hex4(String source, int index) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(source.charAt(index + i), 16);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

}
