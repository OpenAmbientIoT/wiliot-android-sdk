import java.util.Base64

plugins {
    `java-platform`
    `maven-publish`
    id("com.vanniktech.maven.publish") version libs.versions.vanniktech
    id("signing")
}

// Apply shared version constants.
apply(from = "../sdk-versions.gradle")

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

val isMavenCentral = project.findProperty("releaseToMavenCentral") == "true"

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

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

}

//publishing {
//    publications {
//        create<MavenPublication>("mavenBom") {
//            from(components["javaPlatform"])
//            groupId = project.group.toString()
//            artifactId = "wiliot-bom"
//            version = project.version.toString()
//
//            pom {
//                name.set("Wiliot SDK BOM")
//                description.set("Bill of Materials for Wiliot Android SDK modules")
//                url.set("https://github.com/OpenAmbientIoT/wiliot-android-sdk")
//                licenses {
//                    license {
//                        name.set("The Apache License, Version 2.0")
//                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
//                    }
//                }
//                developers {
//                    developer {
//                        id.set("wiliot")
//                        name.set("Wiliot")
//                        email.set("support@wiliot.com")
//                    }
//                }
//                scm {
//                    connection.set("scm:git:git://github.com/OpenAmbientIoT/wiliot-android-sdk.git")
//                    developerConnection.set("scm:git:ssh://github.com:OpenAmbientIoT/wiliot-android-sdk.git")
//                    url.set("https://github.com/OpenAmbientIoT/wiliot-android-sdk")
//                }
//            }
//        }
//    }
//
//    repositories {
//        if (isMavenCentral) {
//            maven {
//                name = "MavenCentral"
//                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
//                credentials {
//                    username = System.getenv("OSSRH_USERNAME")
//                    password = System.getenv("OSSRH_PASSWORD")
//                }
//            }
//        } else {
//            mavenLocal()
//            maven {
//                name = "CodeArtifact"
//                url = uri("https://wiliot-cloud-096303741971.d.codeartifact.us-east-2.amazonaws.com/maven/maven/")
//                credentials {
//                    username = "aws"
//                    password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
//                }
//            }
//        }
//    }
//}

signing {
    if (isMavenCentral) {
        val encodedKey = findProperty("SIGNING_KEY") as String?
        val signingPassword = findProperty("SIGNING_PASSWORD") as String?

        if (encodedKey != null && signingPassword != null) {
            val decodedKey = String(Base64.getDecoder().decode(encodedKey))
            useInMemoryPgpKeys(decodedKey, signingPassword)
            sign(publishing.publications["mavenBom"])
        } else {
            logger.error("Signing key or password not provided.")
        }
    }
}