/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.maven;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the hand-written plugin descriptor honest.
 *
 * <p>The descriptor is maintained by hand because generating it needs either Maven itself or a Gradle
 * plugin that no longer runs on Gradle 9. That is only safe if something checks it, which is what
 * this test does: every goal, phase and parameter in the mojo sources must appear in the descriptor,
 * with the same defaults and property names.
 *
 * <p>The annotations are read from the sources rather than by reflection because Maven's plugin
 * annotations have class retention and are deliberately absent at runtime.
 */
class MavenPluginDescriptorTest {

    private static final Path SOURCES = Path.of("src/main/java/zone/rong/clearskies/maven");

    private static final Pattern MOJO = Pattern.compile("@Mojo\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern PARAMETER = Pattern.compile("@Parameter(?:\\(([^)]*)\\))?\\s+protected\\s+[\\w.<>, ]+?\\s+(\\w+)\\s*[;=]", Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)\\s*=\\s*(\"[^\"]*\"|[\\w.]+)");

    private record MojoSource(String goal, String phase, String resolution, String threadSafe, String implementation) { }

    private record ParameterSource(String name, String property, String defaultValue) { }

    private static Document descriptor() throws Exception {
        Path path = Path.of(System.getProperty("clearskies.descriptor"));
        assertThat(Files.isRegularFile(path)).as(() -> "descriptor not found: " + path).isTrue();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static List<Element> elements(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && element.getTagName().equals(name)) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String text(Element parent, String name) {
        List<Element> matches = elements(parent, name);
        return matches.isEmpty() ? null : matches.getFirst().getTextContent().trim();
    }

    private static Element mojoElement(Document document, String goal) {
        Element mojos = elements(document.getDocumentElement(), "mojos").getFirst();
        for (Element mojo : elements(mojos, "mojo")) {
            if (goal.equals(text(mojo, "goal"))) {
                return mojo;
            }
        }
        return null;
    }

    private static Map<String, String> attributes(String annotationBody) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(annotationBody);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value.startsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(matcher.group(1), value);
        }
        return values;
    }

    /** {@code LifecyclePhase.PROCESS_SOURCES} to {@code process-sources}. */
    private static String constantToId(String constant) {
        String name = constant.substring(constant.lastIndexOf('.') + 1);
        return name.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static List<MojoSource> mojoSources() throws IOException {
        List<MojoSource> mojos = new ArrayList<>();
        for (String fileName : List.of("ApplyMojo.java", "CheckMojo.java")) {
            String source = Files.readString(SOURCES.resolve(fileName), StandardCharsets.UTF_8);
            Matcher matcher = MOJO.matcher(source);
            assertThat(matcher.find()).as(() -> fileName + " has no @Mojo annotation").isTrue();
            Map<String, String> values = attributes(matcher.group(1));
            mojos.add(
                new MojoSource(
                    values.get("name"),
                    constantToId(values.get("defaultPhase")),
                    constantToId(values.getOrDefault("requiresDependencyResolution", "ResolutionScope.NONE")),
                    values.getOrDefault("threadSafe", "false"),
                    "zone.rong.clearskies.maven." + fileName.replace(".java", "")
                )
            );
        }
        return mojos;
    }

    private static List<ParameterSource> parameterSources() throws IOException {
        String source = Files.readString(SOURCES.resolve("AbstractClearSkiesMojo.java"), StandardCharsets.UTF_8);
        List<ParameterSource> parameters = new ArrayList<>();
        Matcher matcher = PARAMETER.matcher(source);
        while (matcher.find()) {
            Map<String, String> values = attributes(matcher.group(1) == null ? "" : matcher.group(1));
            parameters.add(new ParameterSource(matcher.group(2), values.getOrDefault("property", ""), values.getOrDefault("defaultValue", "")));
        }
        assertThat(parameters.size() > 4).as(() -> "found only " + parameters.size() + " parameters in the mojo source").isTrue();
        return parameters;
    }

    @Test
    void everyMojoIsDeclaredWithItsGoalPhaseAndImplementation() throws Exception {
        Document document = descriptor();
        for (MojoSource mojo : mojoSources()) {
            Element declared = mojoElement(document, mojo.goal());
            assertThat(declared).as(() -> "descriptor has no goal named " + mojo.goal()).isNotNull();
            assertThat(text(declared, "implementation")).isEqualTo(mojo.implementation());
            assertThat(text(declared, "phase")).isEqualTo(mojo.phase());
            assertThat(text(declared, "threadSafe")).isEqualTo(mojo.threadSafe());
            assertThat(text(declared, "requiresDependencyResolution")).isEqualTo(mojo.resolution());
        }
    }

    @Test
    void everyAnnotatedParameterIsDeclaredAndNothingExtraIs() throws Exception {
        Document document = descriptor();
        Set<String> annotated = new LinkedHashSet<>();
        parameterSources().forEach(parameter -> annotated.add(parameter.name()));

        for (MojoSource mojo : mojoSources()) {
            Element declared = mojoElement(document, mojo.goal());
            Set<String> names = new LinkedHashSet<>();
            for (Element parameter : elements(elements(declared, "parameters").getFirst(), "parameter")) {
                names.add(text(parameter, "name"));
            }
            assertThat(names).as(() -> "parameters drifted for goal " + mojo.goal()).isEqualTo(annotated);
        }
    }

    @Test
    void everyParameterIsWiredInTheConfigurationBlockWithItsDefault() throws Exception {
        Document document = descriptor();
        for (MojoSource mojo : mojoSources()) {
            Element configuration = elements(mojoElement(document, mojo.goal()), "configuration").getFirst();
            for (ParameterSource parameter : parameterSources()) {
                List<Element> wiring = elements(configuration, parameter.name());
                assertThat(wiring.size()).as(() -> parameter.name() + " is not wired exactly once").isEqualTo(1);
                Element element = wiring.getFirst();
                if (!parameter.defaultValue().isEmpty()) {
                    assertThat(element.getAttribute("default-value"))
                        .as(() -> parameter.name() + " has a different default in the descriptor")
                        .isEqualTo(parameter.defaultValue());
                }
                if (!parameter.property().isEmpty()) {
                    assertThat(element.getTextContent().trim())
                        .as(() -> parameter.name() + " has a different property in the descriptor")
                        .isEqualTo("${" + parameter.property() + "}");
                }
            }
        }
    }

    @Test
    void theProjectParameterIsInjectedAndReadOnly() throws Exception {
        Document document = descriptor();
        for (MojoSource mojo : mojoSources()) {
            Element configuration = elements(mojoElement(document, mojo.goal()), "configuration").getFirst();
            Element project = elements(configuration, "project").getFirst();
            assertThat(project.getAttribute("default-value")).isEqualTo("${project}");
        }
    }

    @Test
    void theGoalPrefixAndCoordinatesMatchTheDocumentedUsage() throws Exception {
        Document document = descriptor();
        assertThat(text(document.getDocumentElement(), "goalPrefix")).isEqualTo("clearskies");
        assertThat(text(document.getDocumentElement(), "artifactId")).isEqualTo("clearskies-maven-plugin");
        assertThat(text(document.getDocumentElement(), "groupId")).isEqualTo("zone.rong.clearskies");
    }

    @Test
    void theDeclaredVersionMatchesTheBuild() throws Exception {
        Document document = descriptor();
        assertThat(text(document.getDocumentElement(), "version"))
            .as("the processed plugin.xml version must match the build")
            .isEqualTo(System.getProperty("clearskies.version"));
    }

    @Test
    void bothGoalsExistAndBindToTheExpectedPhases() throws Exception {
        Document document = descriptor();
        assertThat(text(mojoElement(document, "apply"), "phase")).isEqualTo("process-sources");
        assertThat(text(mojoElement(document, "check"), "phase")).isEqualTo("verify");
    }

}
