import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.vanniktech.maven.publish") version libs.versions.vanniktech
    signing
}

// Apply shared config files.
apply(from = "../constants.gradle")
apply(from = "../sdk-versions.gradle")

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

fun getProperty(propertyName: String): String? {
    return System.getenv("ORG_GRADLE_PROJECT_$propertyName")
        ?: System.getenv(propertyName)
        ?: localProperties.getProperty(propertyName)
}

val encodedKey = getProperty("signingInMemoryKey")
val decodedKey = encodedKey?.let { String(Base64.getDecoder().decode(it)) }
project.extra.set("mavenCentralUsername", getProperty("mavenCentralUsername"))
project.extra.set("mavenCentralPassword", getProperty("mavenCentralPassword"))
project.extra.set("signingInMemoryKey", decodedKey)
project.extra.set("signingInMemoryKeyId", getProperty("signingInMemoryKeyId"))
project.extra.set("signingInMemoryKeyPassword", getProperty("signingInMemoryKeyPassword"))

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
}

dependencies {
    implementation(project(":wiliot-core"))
    implementation(project(":wiliot-calibration"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:${project.extra["mockkVersion"]}")

    implementation(libs.androidx.core.ktx)

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

mavenPublishing {
    coordinates(group.toString(), archivesBaseName, version.toString())

    pom {
        name.set("Wiliot Android SDK")
        description.set("Upstream SDK module for Wiliot Android integration")
        url.set("https://github.com/OpenAmbientIoT/wiliot-android-sdk")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("wiliot")
                name.set("Wiliot")
                email.set("support@wiliot.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/OpenAmbientIoT/wiliot-android-sdk.git")
            developerConnection.set("scm:git:ssh://github.com:OpenAmbientIoT/wiliot-android-sdk.git")
            url.set("https://github.com/OpenAmbientIoT/wiliot-android-sdk")
        }
    }

    publishToMavenCentral()

    signAllPublications()

}

signing {
    val signingPassword = findProperty("signingInMemoryKeyPassword") as String?

    if (encodedKey != null && signingPassword != null) {
        useGpgCmd()
        useInMemoryPgpKeys(decodedKey, signingPassword)
    } else {
        logger.error("Signing key or password not provided.")
    }
}