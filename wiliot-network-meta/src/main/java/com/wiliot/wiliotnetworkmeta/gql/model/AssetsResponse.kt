package com.wiliot.wiliotnetworkmeta.gql.model

import com.wiliot.wiliotcore.model.Asset

data class AssetsResponse(
    val data: Map<String, AssetsPage>?,
    val error: Any?
)

data class AssetsPage(
    val page: List<Asset>
)