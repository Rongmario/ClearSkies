package zone.rong.clearskies.gradle;

import zone.rong.clearskies.api.LanguageLevel;
import javax.inject.Inject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * One source set's sources plus the compile classpath javac attributes them against.
 */
public abstract class SourceSetWork {

    private final String name;

    @Inject
    public SourceSetWork(String name) {
        this.name = name;
    }

    @Input
    public String getName() {
        return name;
    }

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSource();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    @Input
    public abstract Property<LanguageLevel> getLanguageLevel();

    @Input
    public abstract Property<String> getEncoding();

}
