package com.wiliot.wiliotcore.env

import com.wiliot.wiliotcore.BuildConfig

/**
 * Represents a Wiliot environment with its associated API and MQTT URLs.
 *
 * This interface defines the properties required to identify and interact with a specific Wiliot environment.
 * It is used to configure API endpoints and MQTT connections for different environments.
 */
interface EnvironmentWiliot {
    /**
     * Human readable environment name; used as a key, so must be unique
     */
    val envName: String

    /**
     * API Base URL, e.g.: https://api.us-east-2.prod.wiliot.cloud
     */
    val apiBaseUrl: String

    /**
     * MQTT broker url with port, e.g.: ssl://mqtt.us-east-2.prod.wiliot.cloud:8883
     */
    val mqttUrl: String
}

/**
 * Builder class for further creating instances of [EnvironmentWiliot].
 *
 * This class allows you to specify the environment name, API base URL, and MQTT URL
 * to create a custom Wiliot environment.
 */
class EnvironmentBuilder private constructor(
    val envName: String,
    val apiBaseUrl: String,
    val mqttUrl: String
) {

  init {
      // Check mqtt protocol
      if (mqttUrl.startsWith("ssl://", ignoreCase = true).not())
          throw IllegalArgumentException("mqtt url supports only ssl:// protocol; while provided $mqttUrl")

      // Check if mqttUrl contains port
      if (mqttUrl.substringAfterLast(":").toIntOrNull() == null)
          throw IllegalArgumentException("mqtt url must contain a valid port; while provided $mqttUrl")

      // Check if apiBaseUrl is a valid URL
      try {
          java.net.URL(apiBaseUrl)
      } catch (e: Exception) {
          throw IllegalArgumentException("apiBaseUrl must be a valid URL; while provided $apiBaseUrl")
      }

      // Check apiBaseUrl doesn't end with "/"
      if (apiBaseUrl.endsWith("/"))
          throw IllegalArgumentException("apiBaseUrl must not end with '/'; while provided $apiBaseUrl")

      // Check envName contains only allowed characters
      if (!envName.matches("[a-zA-Z_]+".toRegex()))
          throw IllegalArgumentException("envName must contain only uppercase/lowercase letters and underscores; while provided '$envName'")

      // Check envName length
      if (envName.length > 15) throw IllegalArgumentException(
          "envName can not be longer than 15 characters; '$envName' has ${envName.length} characters"
      )
  }

    companion object {
        /**
         * Creates a new instance of [EnvironmentBuilder] with the specified environment name, API base URL, and MQTT URL.
         *
         * @param envName the unique, human-readable name of the environment
         * @param apiBaseUrl the base URL for the API (e.g., https://api.us-east-2.prod.wiliot.cloud)
         * @param mqttUrl the MQTT broker URL with port (e.g., ssl://mqtt.us-east-2.prod.wiliot.cloud:8883)
         * @return a new [EnvironmentBuilder] instance with the provided parameters
         */
        fun with(
            envName: String,
            apiBaseUrl: String,
            mqttUrl: String
        ) = EnvironmentBuilder(envName, apiBaseUrl, mqttUrl)
    }
}

object Environments {

    private val mSet: MutableSet<EnvironmentWiliot> = mutableSetOf()

    /**
     * Predefined Wiliot production environment hosted on Wiliot AWS.
     */
    val WILIOT_PROD_AWS: EnvironmentWiliot

    /**
     * Returns an immutable set of all available Wiliot environments.
     *
     * This set includes both predefined environments and any custom environments added using [addCustomEnvironment].
     *
     * @return a set of [EnvironmentWiliot] instances representing all available environments
     */
    val set: Set<EnvironmentWiliot>
        get() = mSet.toSet()

    private fun EnvironmentBuilder.buildEnvironment(): EnvironmentWiliot {
        return object : EnvironmentWiliot {
            override val envName: String = this@buildEnvironment.envName
            override val apiBaseUrl: String = this@buildEnvironment.apiBaseUrl
            override val mqttUrl: String = this@buildEnvironment.mqttUrl
        }
    }

    /**
     * Adds a custom environment to the set of available environments.
     * Throws an [IllegalArgumentException] if an environment with the same name already exists.
     *
     * Use [EnvironmentBuilder.with] to create the [EnvironmentBuilder] instance.
     *
     * @param environmentBuilder the builder for the custom environment to add
     * @throws IllegalArgumentException if an environment with the same name already exists
     */
    fun addCustomEnvironment(environmentBuilder: EnvironmentBuilder) {
        if (environmentBuilder.envName in mSet.map { it.envName }) throw IllegalArgumentException(
            "Environment with name ${environmentBuilder.envName} already exist"
        )
        mSet.add(environmentBuilder.buildEnvironment())
    }

    /**
     * Retrieves a Wiliot environment by its name.
     *
     * @param envName the unique name of the environment to retrieve
     * @return the [EnvironmentWiliot] instance corresponding to the provided name
     * @throws NoSuchElementException if no environment with the specified name exists
     */
    fun getForName(envName: String): EnvironmentWiliot {
        return mSet.first { it.envName == envName }
    }

    init {
        WILIOT_PROD_AWS = EnvironmentBuilder.with(
            envName = "WILIOT_PROD_AWS",
            apiBaseUrl = BuildConfig.PROD_AWS_API_BASE,
            mqttUrl = BuildConfig.PROD_AWS_MQTT_URL
        ).buildEnvironment()

        mSet.add(WILIOT_PROD_AWS)
    }

}