import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * `:wear-protocol` — the wire format between the phone app's Wear bridge and the `:wear` app:
 * message paths, the state payload and the talk watchdog. Pure Kotlin/JVM on purpose, so the
 * watch app does not have to pull in `:shared` (Ktor, DataStore, Compose resources) and the
 * logic that keeps a lost "button up" from stranding the phone's microphone can be unit-tested
 * without a device.
 */
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
