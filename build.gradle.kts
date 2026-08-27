import org.gradle.api.GradleException
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
    `jvm-toolchains`
    alias(libs.plugins.cleanroom.versioning)
}

allprojects {
    group = "zone.rong.clearskies"
}

subprojects {
    version = rootProject.version
    plugins.withType<JavaPlugin>().configureEach {
        tasks.withType<JavaCompile>().configureEach {
            if (!name.contains("Test", ignoreCase = true)) {
                options.release.set(21)
            }
        }
        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

val clearskiesCli = configurations.create("clearskiesCli") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    clearskiesCli(project(":app"))
}

val starExcludes = listOf(
    "--exclude", "**/build/**",
    "--exclude", "**/src/test/resources/**",
)

tasks.register<JavaExec>("clearSkies") {
    group = "verification"
    description = "Expands star imports in this repository's Java sources with ClearSkies."
    classpath = clearskiesCli
    mainClass.set("zone.rong.clearskies.cli.Main")
    workingDir = layout.projectDirectory.asFile
    args(listOf("--write", ".") + starExcludes)
}

tasks.register<JavaExec>("clearSkiesCheck") {
    group = "verification"
    description = "Fails if this repository's Java sources still contain expandable star imports."
    classpath = clearskiesCli
    mainClass.set("zone.rong.clearskies.cli.Main")
    workingDir = layout.projectDirectory.asFile
    args(listOf("--check", ".") + starExcludes)
}

tasks.register("verifyArtifacts") {
    group = "verification"
    description = "Fails if published CLI and plugin distribution artifacts are missing or empty."
    dependsOn(
        ":app:distZip",
        ":app:distTar",
        ":app:installDist",
        ":core:jar",
        ":gradle-plugin:jar",
        ":maven-plugin:jar",
    )
    val rootDir = layout.projectDirectory
    doLast {
        data class Check(val directory: String, val match: (java.io.File) -> Boolean)

        val checks = listOf(
            Check("app/build/distributions") {
                it.name.startsWith("clearskies-") && it.name.endsWith(".zip")
            },
            Check("app/build/distributions") {
                it.name.startsWith("clearskies-") && it.name.endsWith(".tar")
            },
            Check("app/build/install/clearskies/bin") { it.name == "clearskies" },
            Check("core/build/libs") {
                it.name.startsWith("clearskies-")
                    && it.name.endsWith(".jar")
                    && !it.name.contains("-sources")
                    && !it.name.contains("-javadoc")
            },
            Check("gradle-plugin/build/libs") {
                it.name.startsWith("clearskies-gradle-")
                    && it.name.endsWith(".jar")
                    && !it.name.contains("-sources")
                    && !it.name.contains("-javadoc")
            },
            Check("maven-plugin/build/libs") {
                it.name.startsWith("clearskies-maven-plugin-")
                    && it.name.endsWith(".jar")
                    && !it.name.contains("-sources")
                    && !it.name.contains("-javadoc")
            },
        )
        val missing = checks.filter { check ->
            val folder = rootDir.dir(check.directory).asFile
            folder.listFiles().orEmpty().none { it.isFile && it.length() > 0L && check.match(it) }
        }.map { it.directory }
        if (missing.isNotEmpty()) {
            throw GradleException("empty or missing distribution artifacts in: $missing")
        }
    }
}

tasks.named("check") {
    dependsOn("clearSkiesCheck", "verifyArtifacts")
}
