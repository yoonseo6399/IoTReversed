plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    implementation("com.juul.kable:kable-core:0.42.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.yoonseo6399.raspi.RaspberryPiMainKt")
}
