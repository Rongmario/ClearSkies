import org.gradle.api.GradleException

plugins {
    base
    `jvm-toolchains`
    id("com.cleanroommc.conventions.base")
}

allprojects {
    group = "zone.rong.clearskies"
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
    dependsOn("verifyArtifacts")
}
