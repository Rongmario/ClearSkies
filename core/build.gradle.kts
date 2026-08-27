import org.gradle.api.artifacts.type.ArtifactTypeDefinition

plugins {
    `java-library`
}

apply(from = rootProject.file("gradle/cleanroom-publishing.gradle.kts"))

java {
    withSourcesJar()
}

base {
    archivesName.set("clearskies")
}

val apiSources: SourceSet = sourceSets.create("apiSources")

sourceSets {
    main {
        compileClasspath += apiSources.output
        runtimeClasspath += apiSources.output
    }
    test {
        compileClasspath += apiSources.output
        runtimeClasspath += apiSources.output
    }
}

apiSources.java.setSrcDirs(listOf("src/api/java"))

tasks.jar {
    from(apiSources.output)
}

tasks.named<Jar>("sourcesJar") {
    from(apiSources.allJava)
}

tasks.javadoc {
    source(apiSources.allJava)
}

val apiClasses = tasks.named<JavaCompile>("compileApiSourcesJava").flatMap { it.destinationDirectory }

listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.variants.named("classes") {
            artifact(apiClasses) {
                type = ArtifactTypeDefinition.JVM_CLASS_DIRECTORY
            }
        }
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
