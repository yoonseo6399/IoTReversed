plugins {
    kotlin("multiplatform") version "2.3.0"
    id("com.android.kotlin.multiplatform.library") version "9.0.0"
}

group = "io.github.yoonseo6399"
version = "1.0-SNAPSHOT"
repositories {
    google()       // 안드로이드 플러그인이 사는 곳
    mavenCentral()
    gradlePluginPortal()
}
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
            minSdk = 28
        }

        androidMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
    }
    jvmToolchain(21)
}
