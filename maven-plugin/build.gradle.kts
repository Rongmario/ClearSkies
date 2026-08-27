import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
}

apply(from = rootProject.file("gradle/cleanroom-publishing.gradle.kts"))

java {
    withSourcesJar()
}

base {
    archivesName.set("clearskies-maven-plugin")
}

description = "Expands Java star imports with ClearSkies, using the same engine as the CLI and Gradle plugin."

dependencies {
    implementation(project(":core"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)
    testCompileOnly(libs.maven.plugin.annotations)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val projectVersion = version.toString()
tasks.processResources {
    val tokens = mapOf("version" to projectVersion)
    inputs.property("version", projectVersion)
    filesMatching("META-INF/maven/plugin.xml") {
        filter(mapOf("tokens" to tokens), ReplaceTokens::class.java)
    }
}

tasks.test {
    useJUnitPlatform()
    val mavenVersion = providers.gradleProperty("clearskies.maven.test.version").orElse("3.9.16")
    val pluginJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val coreJar = project(":core").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    dependsOn(tasks.named("jar"), project(":core").tasks.named("jar"))
    inputs.files(pluginJar, coreJar)
    systemProperty(
        "clearskies.descriptor",
        layout.buildDirectory.file("resources/main/META-INF/maven/plugin.xml").get().asFile.absolutePath,
    )
    systemProperty("clearskies.version", projectVersion)
    systemProperty("clearskies.maven.version", mavenVersion.get())
    systemProperty("clearskies.maven.plugin.jar", pluginJar.get().asFile.absolutePath)
    systemProperty("clearskies.core.jar", coreJar.get().asFile.absolutePath)
    systemProperty(
        "clearskies.maven.cache",
        layout.buildDirectory.dir("maven-dist").get().asFile.absolutePath,
    )
    inputs.property("clearskies.maven.version", mavenVersion)
}
