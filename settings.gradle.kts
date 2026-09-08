
pluginManagement {
    repositories {
        google()       // 안드로이드 플러그인이 사는 곳
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "IoTReversed"
include(":raspi-daemon")
