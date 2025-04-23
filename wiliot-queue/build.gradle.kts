plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("maven-publish")
    id("signing")
}

apply(from = "../constants.gradle")
apply(from = "../sdk-versions.gradle")

group = "com.wiliot"
val archivesBaseName = "wiliot-queue"
version = extra["queueVersionName"] as String

android {
    namespace = "com.wiliot.wiliotqueue"
    compileSdk = extra["compile_sdk"] as Int

    defaultConfig {
        minSdk = extra["min_sdk"] as Int
        testOptions.targetSdk = extra["target_sdk"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "PROD_AWS_MQTT_URL", "\"${project.extra["prodAwsMqttUrl"]}\"")
        buildConfigField("String", "PROD_GCP_MQTT_URL", "\"${project.extra["prodGcpMqttUrl"]}\"")
        buildConfigField("String", "TEST_AWS_MQTT_URL", "\"${project.extra["testAwsMqttUrl"]}\"")
        buildConfigField("String", "TEST_GCP_MQTT_URL", "\"${project.extra["testGcpMqttUrl"]}\"")
        buildConfigField("String", "DEV_AWS_MQTT_URL", "\"${project.extra["devAwsMqttUrl"]}\"")
        buildConfigField("String", "DEV_GCP_MQTT_URL", "\"${project.extra["devGcpMqttUrl"]}\"")
        buildConfigField("String", "PROD_AWS_API_BASE", "\"${project.extra["prodAwsBaseUrl"]}\"")
        buildConfigField("String", "PROD_GCP_API_BASE", "\"${project.extra["prodGcpBaseUrl"]}\"")
        buildConfigField("String", "TEST_AWS_API_BASE", "\"${project.extra["testAwsBaseUrl"]}\"")
        buildConfigField("String", "TEST_GCP_API_BASE", "\"${project.extra["testGcpBaseUrl"]}\"")
        buildConfigField("String", "DEV_AWS_API_BASE", "\"${project.extra["devAwsBaseUrl"]}\"")
        buildConfigField("String", "DEV_GCP_API_BASE", "\"${project.extra["devGcpBaseUrl"]}\"")
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

val isMavenCentral = project.findProperty("releaseToMavenCentral") == "true"

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = archivesBaseName
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Wiliot Android SDK")
                description.set("Queue SDK module for Wiliot Android integration")
                url.set("https://github.com/OpenAmbientIoT/wiliot-android-sdk")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
        }
    }

    repositories {
        if (isMavenCentral) {
            maven {
                name = "MavenCentral"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = System.getenv("OSSRH_USERNAME")
                    password = System.getenv("OSSRH_PASSWORD")
                }
            }
        } else {
            mavenLocal()
            maven {
                name = "CodeArtifact"
                url = uri("https://wiliot-cloud-096303741971.d.codeartifact.us-east-2.amazonaws.com/maven/maven/")
                credentials {
                    username = "aws"
                    password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
                }
            }
        }
    }
}

signing {
    if (isMavenCentral) {
        useInMemoryPgpKeys(
            System.getenv("SIGNING_KEY_ID"),
            System.getenv("SIGNING_KEY"),
            System.getenv("SIGNING_PASSWORD")
        )
        sign(publishing.publications["release"])
    }
}