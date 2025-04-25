plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
    alias(libs.plugins.dagger.hilt)
}

apply(from = "../secret.gradle")

android {
    namespace = "com.wiliot.wiliotandroidsdk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wiliot.wiliotandroidsdk"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "WILIOT_API_KEY", "\"${project.extra["wiliotApiKey"]}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    android.buildFeatures.buildConfig = true

}

dependencies {

    // Wiliot
    implementation(project(":wiliot-core"))
    implementation(project(":wiliot-upstream"))
    implementation(project(":wiliot-downstream"))
    implementation(project(":wiliot-advertising"))
    implementation(project(":wiliot-calibration"))
    implementation(project(":wiliot-dfu"))
    implementation(project(":wiliot-edge"))
    implementation(project(":wiliot-queue"))
    implementation(project(":wiliot-virtual-bridge"))
    implementation(project(":wiliot-network-edge"))
    implementation(project(":wiliot-network-meta"))
    implementation(project(":wiliot-resolve-edge"))
    implementation(project(":wiliot-resolve-data"))

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.play.services.location)
    kapt(libs.hilt.compiler)
    kaptAndroidTest(libs.hilt.compiler)
    androidTestAnnotationProcessor(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout.compose)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
