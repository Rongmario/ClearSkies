plugins {
    application
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
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    val launcher = layout.buildDirectory.file("install/clearskies/bin/clearskies")
    dependsOn(tasks.named("installDist"))
    inputs.file(launcher)
    systemProperty("clearskies.launcher", launcher.get().asFile.absolutePath)
}
