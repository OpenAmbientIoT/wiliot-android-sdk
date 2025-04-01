package com.wiliot.wiliotnetworkmeta.gql.model

data class ApiRequest(
    val query: String,
    val variables: String? = null
)