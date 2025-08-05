import java.util.Base64
import java.util.Properties

plugins {
    `java-platform`
    id("com.vanniktech.maven.publish") version libs.versions.vanniktech
    signing
}

// Apply shared version constants.
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
version = extra["bomVersionName"] as String

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("com.wiliot:wiliot-core:${project.extra["coreVersionName"]}")
        api("com.wiliot:wiliot-calibration:${project.extra["calibrationVersionName"]}")
        api("com.wiliot:wiliot-downstream:${project.extra["downstreamVersionName"]}")
        api("com.wiliot:wiliot-network-edge:${project.extra["networkEdgeVersionName"]}")
        api("com.wiliot:wiliot-network-meta:${project.extra["networkMetaVersionName"]}")
        api("com.wiliot:wiliot-queue:${project.extra["queueVersionName"]}")
        api("com.wiliot:wiliot-resolve-edge:${project.extra["resolveEdgeVersionName"]}")
        api("com.wiliot:wiliot-resolve-data:${project.extra["resolveDataVersionName"]}")
        api("com.wiliot:wiliot-upstream:${project.extra["upstreamVersionName"]}")
        api("com.wiliot:wiliot-virtual-bridge:${project.extra["virtualBridgeVersionName"]}")
        api("com.wiliot:wiliot-edge:${project.extra["edgeVersionName"]}")
        api("com.wiliot:wiliot-advertising:${project.extra["advertisingVersionName"]}")
        api("com.wiliot:wiliot-dfu:${project.extra["dfuVersionName"]}")
    }
}

mavenPublishing {
    coordinates(group.toString(), "wiliot-bom", version.toString())

    pom {
        name.set("Wiliot Android SDK BOM")
        description.set("Bill of Materials for Wiliot Android SDK modules")
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