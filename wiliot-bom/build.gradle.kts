plugins {
    `java-platform`
    `maven-publish`
}

// Apply shared version constants
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

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
            groupId = project.group.toString()
            artifactId = "wiliot-bom"
            version = project.version.toString()
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
