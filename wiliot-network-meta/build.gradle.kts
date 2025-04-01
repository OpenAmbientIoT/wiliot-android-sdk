plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("maven-publish")
}

apply(from = "../constants.gradle")
apply(from = "../sdk-versions.gradle")

group = "com.wiliot"
val archivesBaseName = "wiliot-network-meta"
version = extra["networkMetaVersionName"] as String

android {
    compileSdk = extra["compile_sdk"] as Int
    namespace = "com.wiliot.wiliotnetworkmeta"

    defaultConfig {
        minSdk = extra["min_sdk"] as Int
        testOptions.targetSdk = extra["target_sdk"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Using legacy style buildConfigField due to applied groovy-style property file
        buildConfigField("String", "PROD_AWS_API_BASE", "\"${extra["prodAwsBaseUrl"]}\"")
        buildConfigField("String", "PROD_GCP_API_BASE", "\"${extra["prodGcpBaseUrl"]}\"")
        buildConfigField("String", "TEST_AWS_API_BASE", "\"${extra["testAwsBaseUrl"]}\"")
        buildConfigField("String", "TEST_GCP_API_BASE", "\"${extra["testGcpBaseUrl"]}\"")
        buildConfigField("String", "DEV_AWS_API_BASE", "\"${extra["devAwsBaseUrl"]}\"")
        buildConfigField("String", "DEV_GCP_API_BASE", "\"${extra["devGcpBaseUrl"]}\"")
        buildConfigField("Boolean", "SDK_HTTP_LOGS_ENABLED", "${extra["sdkHttpLogsEnabledInDebugMode"]}")
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

    kotlinOptions {
        jvmTarget = "1.8"
    }

    android.buildFeatures.buildConfig = true

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(project(":wiliot-core"))

    testImplementation("junit:junit:4.13.2")
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