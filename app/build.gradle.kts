plugins {
    application
    id("com.cleanroommc.conventions")
}

application {
    applicationName = "clearskies"
    mainClass = "zone.rong.clearskies.cli.Main"
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "clearskies",
            "Implementation-Version" to version.toString(),
        )
    }
}

dependencies {
    implementation(project(":core"))
}

tasks.test {
    val launcher = layout.buildDirectory.file("install/clearskies/bin/clearskies")
    dependsOn(tasks.named("installDist"))
    inputs.file(launcher)
    systemProperty("clearskies.launcher", launcher.get().asFile.absolutePath)
}
