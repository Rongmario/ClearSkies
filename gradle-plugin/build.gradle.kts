import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    `java-gradle-plugin`
    id("com.cleanroommc.conventions")
}

base {
    archivesName.set("clearskies-gradle")
}

gradlePlugin {
    website = "https://github.com/Rongmario/ClearSkies"
    vcsUrl = "https://github.com/Rongmario/ClearSkies.git"
    plugins {
        create("clearskies") {
            id = "zone.rong.clearskies"
            implementationClass = "zone.rong.clearskies.gradle.ClearSkiesPlugin"
            displayName = "ClearSkies"
            description = "Expands Java star imports with ClearSkies, using the same engine as the CLI and Maven plugin."
            tags = listOf("java", "imports", "star-imports", "wildcard")
        }
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation(gradleTestKit())
}

val coreJar = project(":core").tasks.named<Jar>("jar")
tasks.named<Jar>("jar") {
    from(coreJar.map { zipTree(it.archiveFile.get()) }) {
        exclude("META-INF/MANIFEST.MF")
    }
}

tasks.named<Jar>("sourcesJar") {
    from(project(":core").sourceSets.named("main").map { it.allJava })
    from(project(":core").sourceSets.named("apiSources").map { it.allJava })
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "clearskies-gradle"
        }
        pom {
            name.set("ClearSkies Gradle Plugin")
            description.set("Expands Java star imports with ClearSkies, using the same engine as the CLI and Maven plugin.")
        }
        pom.withXml {
            val dependenciesNodes = asNode().get("dependencies") as NodeList
            if (dependenciesNodes.isEmpty()) {
                return@withXml
            }
            val dependencies = dependenciesNodes[0] as Node
            val embedded = dependencies.children().filterIsInstance<Node>().filter { dep ->
                val artifactId = (dep.get("artifactId") as NodeList).text()
                artifactId == "core" || artifactId == "clearskies"
            }
            embedded.forEach { dependencies.remove(it) }
        }
    }
}

tasks.test {
    val testVersion = providers.gradleProperty("clearskies.gradle.test.version").orElse("")
    systemProperty("clearskies.gradle.version", testVersion.get())
    inputs.property("clearskies.gradle.version", testVersion)
}
