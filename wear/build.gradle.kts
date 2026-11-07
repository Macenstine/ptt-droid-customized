import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * `:wear` — the Wear OS side of the watch remote. A thin client: it shows the phone session's
 * state and sends the hold-to-talk button to the phone, which owns the connection, microphone and
 * speaker (see `:app`'s `wear/WearBridge`). It deliberately does not depend on `:shared`.
 *
 * The `applicationId` must equal the phone app's, and both APKs must be signed with the same
 * key: the Wearable Data Layer only pairs apps by package name and signing certificate. Debug
 * builds on one machine share the debug key, so that case works without any setup.
 */
apply(from = "$rootDir/gradle/relay-defaults.gradle.kts")

val appVersionName: String by extra
val appVersionCode: Int by extra

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.jetbrainsKotlinCompose)
}

android {
    namespace = "com.github.devapro.pttdroid.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.devapro.pttdroid"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    val keystorePath: String? = System.getenv("PTT_KEYSTORE_PATH")
    if (!keystorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PTT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PTT_KEY_ALIAS")
                keyPassword = System.getenv("PTT_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Must match :app's debug suffix, or the Data Layer will not pair the two.
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":wear-protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.ui.tooling)
}
