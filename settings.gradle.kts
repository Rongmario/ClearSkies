pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.cleanroommc.conventions.settings") version "1.1.8"
}

rootProject.name = "clearskies"

include("core")
include("app")
include("gradle-plugin")
include("maven-plugin")
