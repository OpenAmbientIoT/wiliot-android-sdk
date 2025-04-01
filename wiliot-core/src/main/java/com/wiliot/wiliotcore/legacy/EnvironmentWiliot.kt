package com.wiliot.wiliotcore.legacy

enum class EnvironmentWiliot(val value: String) {

    PROD_AWS("PROD_AWS"),
    PROD_GCP("PROD_GCP"),

    TEST_AWS("TEST_AWS"),
    TEST_GCP("TEST_GCP"),

    DEV_AWS("DEV_AWS"),
    DEV_GCP("DEV_GCP");

    companion object {
        val mqttSuffix = mapOf(
            Pair(PROD_AWS.value, ""),
            Pair(PROD_GCP.value, ""),
            Pair(TEST_AWS.value, ""),
            Pair(TEST_GCP.value, ""),
            Pair(DEV_AWS.value, ""),
            Pair(DEV_GCP.value, "")
        )
    }

}

enum class CloudGroup(val groupName: String, val environments: List<EnvironmentWiliot>) {

    AWS(
        "AWS",
        listOf(
            EnvironmentWiliot.PROD_AWS,
            EnvironmentWiliot.TEST_AWS,
            EnvironmentWiliot.DEV_AWS,
        )
    ),

    GCP(
        "GCP",
        listOf(
            EnvironmentWiliot.PROD_GCP,
            EnvironmentWiliot.TEST_GCP,
            EnvironmentWiliot.DEV_GCP,
        )
    );

    companion object {
        fun findGroup(env: EnvironmentWiliot): CloudGroup {
            return values().first { env in it.environments }
        }
    }

}

enum class EnvironmentGroup(val groupName: String, val environments: List<EnvironmentWiliot>) {

    PROD(
        "PROD_GROUP",
        listOf(
            EnvironmentWiliot.PROD_AWS,
            EnvironmentWiliot.PROD_GCP
        )
    ),

    TEST(
        "TEST_GROUP",
        listOf(
            EnvironmentWiliot.TEST_AWS,
            EnvironmentWiliot.TEST_GCP
        )
    ),

    DEV(
        "DEV_GROUP",
        listOf(
            EnvironmentWiliot.DEV_AWS,
            EnvironmentWiliot.DEV_GCP
        )
    );

    companion object {
        fun findGroup(env: EnvironmentWiliot): EnvironmentGroup {
            return values().first { it.environments.contains(env) }
        }
    }

}