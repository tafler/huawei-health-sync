import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Баг #3: читаем секреты из local.properties (не коммитится в git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "ru.tafinceva.health"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.tafinceva.health"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Прокидываем секреты в BuildConfig
        buildConfigField("String", "HUAWEI_APP_ID",          "\"${localProps["HUAWEI_APP_ID"] ?: ""}\"")
        buildConfigField("String", "HUAWEI_APP_SECRET",       "\"${localProps["HUAWEI_APP_SECRET"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID",        "\"${localProps["GOOGLE_CLIENT_ID"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET",    "\"${localProps["GOOGLE_CLIENT_SECRET"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_DRIVE_FOLDER_ID",  "\"${localProps["GOOGLE_DRIVE_FOLDER_ID"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // WorkManager
    implementation(libs.workmanager)

    // Security
    implementation(libs.security.crypto)

    // Coroutines
    implementation(libs.coroutines.android)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
}
