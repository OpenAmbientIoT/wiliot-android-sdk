plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("maven-publish")
}

apply(from = "../constants.gradle")
apply(from = "../sdk-versions.gradle")

group = "com.wiliot"
val archivesBaseName = "wiliot-upstream"
version = extra["upstreamVersionName"] as String

android {
    compileSdk = extra["compile_sdk"] as Int
    namespace = "com.wiliot.wiliotupstream"

    defaultConfig {
        minSdk = extra["min_sdk"] as Int
        testOptions.targetSdk = extra["target_sdk"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    android.buildFeatures.buildConfig = true

    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(project(":wiliot-core"))
    implementation(project(":wiliot-calibration"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:${project.extra["mockkVersion"]}")

    androidTestImplementation("org.robolectric:robolectric:3.5")
    androidTestImplementation("org.mockito:mockito-core:4.8.0")

    androidTestImplementation("org.powermock:powermock-api-mockito:1.6.4") {
        exclude(module = "hamcrest-core")
        exclude(module = "objenesis")
    }
    androidTestImplementation("org.powermock:powermock-module-junit4:1.6.4") {
        exclude(module = "hamcrest-core")
        exclude(module = "objenesis")
    }

    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = archivesBaseName
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }
        }
    }

    repositories {
        mavenLocal()
        maven {
            url = uri("https://wiliot-cloud-096303741971.d.codeartifact.us-east-2.amazonaws.com/maven/maven/")
            credentials {
                username = "aws"
                password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
            }
        }
    }
}
