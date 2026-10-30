plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nickesh.tvlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nickesh.tvlauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("long", "WELCOME_REPLAY_MS", "600000L")
    }

    buildTypes {
        debug {
            // `-Pprobe=true` builds a second installable copy (used to prove focus on uninstall).
            if (project.hasProperty("probe")) applicationIdSuffix = ".probe"
            (project.findProperty("welcomeReplayMs") as String?)?.let { buildConfigField("long", "WELCOME_REPLAY_MS", "${it}L") }
        }
    }

    // Probe label "AAA Probe" sorts first so the copy lands at the start of the home row.
    if (project.hasProperty("probe")) sourceSets.getByName("debug").res.srcDir("src/probe/res")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3) // Text only
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
