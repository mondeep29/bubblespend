plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bubblespend.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bubblespend.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coroutines.core)
    ksp(libs.room.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(project(":core:domain"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.coroutines.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
