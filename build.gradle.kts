import org.gradle.internal.impldep.com.amazonaws.PredefinedClientConfigurations.defaultConfig

plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0" apply false

    id("com.android.kotlin.multiplatform.library") version "9.0.0"
}

group = "io.github.yoonseo6399"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}


dependencies {

}

kotlin {
    android {
        namespace = "io.github.yoonseo6399.iotswitch"
        compileSdk { version = release(36) }
    }
    macosX64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("com.juul.kable:kable-core:0.42.0")
            implementation(kotlin("reflect"))

        }

        androidMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation("com.juul.kable:kable-default-permissions:0.42.0") // Optional
        }
    }
    jvmToolchain(21)
}
