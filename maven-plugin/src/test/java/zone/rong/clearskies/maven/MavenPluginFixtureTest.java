package zone.rong.clearskies.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the packaged Maven plugin against a real Maven project.
 *
 * <p>{@link MavenPluginDescriptorTest} keeps the hand-written descriptor honest. This test is the
 * slower check that Maven actually loads that descriptor, puts the plugin on its classpath, and
 * that {@code clearskies:apply} / {@code clearskies:check} change a Java source the way a user would
 * see.
 */
class MavenPluginFixtureTest {

    private static final String MAVEN_VERSION = System.getProperty("clearskies.maven.version", "3.9.16");
    private static final String MAVEN_ARCHIVE = "apache-maven-" + MAVEN_VERSION + "-bin.tar.gz";
    private static final String MAVEN_URL = "https://archive.apache.org/dist/maven/maven-3/" + MAVEN_VERSION
            + "/binaries/" + MAVEN_ARCHIVE;
    // Published by Apache alongside each binary. A version tested in CI needs its digest listed here.
    private static final Map<String, String> MAVEN_SHA512 = Map.of(
            "3.9.0",
            "1ea149f4e48bc7b34d554aef86f948eca7df4e7874e30caf449f3708e4f8487c71a5e5c072a05f17c60406176ebeeaf56b5f895090c7346f8238e2da06cf6ecd",
            "3.9.16",
            "831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6");

    @TempDir
    Path temp;

    @Test
    void applyGoalRewritesAndCheckThenPasses() throws Exception {
        String version = requiredProperty("clearskies.version");
        Path pluginJar = Path.of(requiredProperty("clearskies.maven.plugin.jar"));
        Path coreJar = Path.of(requiredProperty("clearskies.core.jar"));
        assertTrue(Files.isRegularFile(pluginJar), () -> "missing plugin jar: " + pluginJar);
        assertTrue(Files.isRegularFile(coreJar), () -> "missing core jar: " + coreJar);

        Path fixture = temp.resolve("fixture");
        copyFixture(fixture);
        replaceVersion(fixture.resolve("pom.xml"), version);
        Path sample = fixture.resolve("src/main/java/sample/Sample.java");
        String starred = Files.readString(sample, StandardCharsets.UTF_8);
        assertTrue(starred.contains("import java.util.*;"), starred);

        Path localRepo = temp.resolve("repo");
        installArtifact(localRepo, "zone.rong.clearskies", "clearskies", version, coreJar, corePom(version));
        installArtifact(
                localRepo,
                "zone.rong.clearskies",
                "clearskies-maven-plugin",
                version,
                pluginJar,
                pluginPom(version));

        Path mavenHome = mavenHome();
        Run mavenVersion = runProcess(
                List.of(mavenHome.resolve("bin/mvn").toString(), "--version"),
                fixture,
                Duration.ofMinutes(1));
        assertEquals(0, mavenVersion.exitCode, mavenVersion.out + mavenVersion.err);
        assertTrue(
                mavenVersion.out.contains("Apache Maven " + MAVEN_VERSION),
                () -> "expected Maven " + MAVEN_VERSION + ":\n" + mavenVersion.out + mavenVersion.err);
        String plugin = "zone.rong.clearskies:clearskies-maven-plugin:" + version;
        Run checkDirty = maven(mavenHome, localRepo, fixture, version, plugin + ":check");
        assertNotEquals(0, checkDirty.exitCode, checkDirty.out + checkDirty.err);
        assertTrue(
                (checkDirty.out + checkDirty.err).contains("Sample.java")
                        || (checkDirty.out + checkDirty.err).toLowerCase().contains("star"),
                checkDirty.out + checkDirty.err);

        Run apply = maven(mavenHome, localRepo, fixture, version, plugin + ":apply");
        assertEquals(0, apply.exitCode, apply.out + apply.err);
        String expanded = Files.readString(sample, StandardCharsets.UTF_8);
        assertTrue(expanded.contains("import java.util.List;"), expanded);
        assertTrue(expanded.contains("class Sample { List x; }"), expanded);

        Run checkClean = maven(mavenHome, localRepo, fixture, version, plugin + ":check");
        assertEquals(0, checkClean.exitCode, checkClean.out + checkClean.err);
        assertEquals(expanded, Files.readString(sample, StandardCharsets.UTF_8));
    }

    @Test
    void processSourcesApplyDoesNotRewriteTestSources() throws Exception {
        String version = requiredProperty("clearskies.version");
        Path pluginJar = Path.of(requiredProperty("clearskies.maven.plugin.jar"));
        Path coreJar = Path.of(requiredProperty("clearskies.core.jar"));
        Path fixture = temp.resolve("process-sources");
        copyFixture(fixture);
        replaceVersion(fixture.resolve("pom.xml"), version);
        String pom = Files.readString(fixture.resolve("pom.xml"), StandardCharsets.UTF_8);
        Files.writeString(
                fixture.resolve("pom.xml"),
                pom.replace(
                        "</plugin>",
                        """
                          <executions>
                            <execution>
                              <id>clearskies-apply</id>
                              <phase>process-sources</phase>
                              <goals><goal>apply</goal></goals>
                            </execution>
                          </executions>
                        </plugin>
                        """),
                StandardCharsets.UTF_8);
        Path testSample = fixture.resolve("src/test/java/sample/TestSample.java");
        Files.createDirectories(testSample.getParent());
        String testStarred =
                """
                package sample;

                import java.util.*;

                class TestSample { List x; }
                """;
        Files.writeString(testSample, testStarred, StandardCharsets.UTF_8);

        Path localRepo = temp.resolve("repo-process");
        installArtifact(localRepo, "zone.rong.clearskies", "clearskies", version, coreJar, corePom(version));
        installArtifact(
                localRepo,
                "zone.rong.clearskies",
                "clearskies-maven-plugin",
                version,
                pluginJar,
                pluginPom(version));
        Path mavenHome = mavenHome();
        Run process = maven(mavenHome, localRepo, fixture, version, "process-sources");
        assertEquals(0, process.exitCode, process.out + process.err);
        String main = Files.readString(fixture.resolve("src/main/java/sample/Sample.java"), StandardCharsets.UTF_8);
        assertTrue(main.contains("import java.util.List;"), main);
        assertEquals(testStarred, Files.readString(testSample, StandardCharsets.UTF_8));
    }

    private static Run maven(Path mavenHome, Path localRepo, Path fixture, String version, String goal)
            throws Exception {
        Path mvn = mavenHome.resolve("bin/mvn");
        assertTrue(Files.isRegularFile(mvn), () -> "missing mvn: " + mvn);
        List<String> command = new ArrayList<>();
        command.add(mvn.toString());
        command.add("-B");
        command.add("-e");
        command.add("-Dclearskies.version=" + version);
        command.add("-Dmaven.repo.local=" + localRepo);
        command.add(goal);
        return runProcess(command, fixture, Duration.ofMinutes(5));
    }

    private static Path mavenHome() throws Exception {
        Path cache = Path.of(System.getProperty("clearskies.maven.cache", "build/maven-dist"));
        Path unpacked = cache.resolve("apache-maven-" + MAVEN_VERSION);
        Files.createDirectories(cache);
        Path archive = cache.resolve(MAVEN_ARCHIVE);
        if (!Files.isRegularFile(archive) || Files.size(archive) == 0) {
            download(URI.create(MAVEN_URL), archive);
        }
        String expected = MAVEN_SHA512.get(MAVEN_VERSION);
        if (expected == null) {
            throw new IOException("no published SHA-512 recorded for Maven " + MAVEN_VERSION);
        }
        verifySha512(archive, expected);
        if (!Files.isRegularFile(unpacked.resolve("bin/mvn"))) {
            Run unpack =
                    runProcess(
                            List.of("tar", "-xzf", archive.toString(), "-C", cache.toString()),
                            cache,
                            Duration.ofMinutes(2));
            if (unpack.exitCode != 0) {
                Files.deleteIfExists(archive);
                throw new IOException("failed to unpack Maven from " + archive + "\n" + unpack.out + unpack.err);
            }
        }
        assertTrue(Files.isRegularFile(unpacked.resolve("bin/mvn")), () -> "unpacked Maven missing: " + unpacked);
        unpacked.resolve("bin/mvn").toFile().setExecutable(true);
        return unpacked;
    }

    private static void verifySha512(Path file, String expected) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
        try (java.io.InputStream in = Files.newInputStream(file);
                java.security.DigestInputStream digested = new java.security.DigestInputStream(in, digest)) {
            digested.transferTo(java.io.OutputStream.nullOutputStream());
        }
        String actual = java.util.HexFormat.of().formatHex(digest.digest());
        if (!expected.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(file);
            throw new IOException(
                    "Maven archive checksum mismatch for " + file + "\nexpected " + expected + "\ngot " + actual);
        }
    }

    private static void download(URI uri, Path destination) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(destination);
            throw new IOException("download failed: " + uri + " -> HTTP " + response.statusCode());
        }
    }

    private static void installArtifact(Path repo, String group, String artifact, String version, Path jar, String pom)
            throws IOException {
        Path directory = repo;
        for (String part : group.split("\\.")) {
            directory = directory.resolve(part);
        }
        directory = directory.resolve(artifact).resolve(version);
        Files.createDirectories(directory);
        Files.copy(jar, directory.resolve(artifact + "-" + version + ".jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(directory.resolve(artifact + "-" + version + ".pom"), pom, StandardCharsets.UTF_8);
        Files.writeString(
                directory.getParent().resolve("maven-metadata-local.xml"),
                metadata(group, artifact, version),
                StandardCharsets.UTF_8);
    }

    private static String corePom(String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>zone.rong.clearskies</groupId>
                  <artifactId>clearskies</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(
                version);
    }

    private static String pluginPom(String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>zone.rong.clearskies</groupId>
                  <artifactId>clearskies-maven-plugin</artifactId>
                  <version>%s</version>
                  <packaging>maven-plugin</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>zone.rong.clearskies</groupId>
                      <artifactId>clearskies</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(
                version,
                version);
    }

    private static String metadata(String group, String artifact, String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <versioning>
                    <release>%s</release>
                    <versions><version>%s</version></versions>
                  </versioning>
                </metadata>
                """.formatted(
                group,
                artifact,
                version,
                version);
    }

    private static void copyFixture(Path destination) throws IOException {
        Path source = Path.of("src/test/resources/maven-fixture");
        assertTrue(Files.isDirectory(source), () -> "missing fixture: " + source.toAbsolutePath());
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path target = destination.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                }
            }
        }
    }

    private static void replaceVersion(Path pom, String version) throws IOException {
        String text = Files.readString(pom, StandardCharsets.UTF_8);
        Files.writeString(pom, text.replace("CLEARSKIES_VERSION", version), StandardCharsets.UTF_8);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("missing system property " + name);
        }
        return value;
    }

    private static Run runProcess(List<String> command, Path directory, Duration timeout) throws Exception {
        Path outFile = Files.createTempFile("clearskies-maven-", ".out");
        Path errFile = Files.createTempFile("clearskies-maven-", ".err");
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(directory.toFile());
            builder.redirectOutput(outFile.toFile());
            builder.redirectError(errFile.toFile());
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException(
                        "process timed out: " + command + "\n" + Files.readString(outFile) + Files.readString(errFile));
            }
            return new Run(
                    process.exitValue(),
                    Files.readString(outFile, StandardCharsets.UTF_8),
                    Files.readString(errFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outFile);
            Files.deleteIfExists(errFile);
        }
    }

    private record Run(int exitCode, String out, String err) { }

}
