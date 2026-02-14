import org.gradle.internal.impldep.com.amazonaws.PredefinedClientConfigurations.defaultConfig

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

group = "io.github.yoonseo6399"
version = "1.0-SNAPSHOT"

dependencies {
//    implementation("com.juul.kable:kable-core:0.42.0")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
//    implementation(kotlin("reflect"))
}

kotlin {

    macosX64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("com.juul.kable:kable-core:0.42.0")
            api(kotlin("reflect"))
        }
        androidLibrary {
            namespace = "io.github.yoonseo6399.iotswitch"
            compileSdk = 36 // 또는 35
            minSdk = 24
        }

        androidMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
    }
    jvmToolchain(21)
}
