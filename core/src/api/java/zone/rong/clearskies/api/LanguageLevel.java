package zone.rong.clearskies.api;

/**
 * The Java release passed to javac as {@code --release}.
 *
 * <p>This is the project's language level, not the JDK ClearSkies itself runs on. A run on Java 25
 * can still attribute a file as Java 21 so later APIs are not visible.
 */
public enum LanguageLevel {

    JAVA_17(17),
    JAVA_18(18),
    JAVA_19(19),
    JAVA_20(20),
    JAVA_21(21),
    JAVA_22(22),
    JAVA_23(23),
    JAVA_24(24),
    JAVA_25(25);

    /** The newest release ClearSkies knows about. */
    public static final LanguageLevel LATEST = JAVA_25;

    private final int release;

    LanguageLevel(int release) {
        this.release = release;
    }

    /** The release number, e.g. {@code 25}. */
    public int release() {
        return release;
    }

    /** Whether this level is at least {@code other}. */
    public boolean isAtLeast(LanguageLevel other) {
        return release >= other.release;
    }

    /**
     * The newest known level that the running JDK can actually compile, so {@code --release} is
     * never newer than the compiler.
     */
    public static LanguageLevel ofRuntime() {
        int feature = Runtime.version().feature();
        LanguageLevel best = values()[0];
        for (LanguageLevel level : values()) {
            if (level.release <= feature) {
                best = level;
            }
        }
        return best;
    }

    /** Looks up a level by release number, e.g. {@code 21} or {@code "21"}. */
    public static LanguageLevel ofRelease(int release) {
        for (LanguageLevel level : values()) {
            if (level.release == release) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unsupported Java release: " + release);
    }

}
