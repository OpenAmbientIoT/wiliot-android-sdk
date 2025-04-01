package com.wiliot.wiliotnetworkmeta.gql

import com.wiliot.wiliotcore.FlowVersion
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.Asset
import com.wiliot.wiliotcore.model.IResolveInfo
import com.wiliot.wiliotcore.model.gqlQueryIdentifier
import com.wiliot.wiliotnetworkmeta.gql.model.AssetsPage
import com.wiliot.wiliotnetworkmeta.gql.model.AssetsResponse

data class GQLAssetForTag(val tag: IResolveInfo, val useDefaultFlowVersion: Boolean = false) {

    companion object {
        const val QUERY_BOUNDS =
            """query {%s}"""

        private fun buildItemLine(pData: IResolveInfo, flowVersion: FlowVersion, ignoreFlowVersion: Boolean) = with(pData) {
            val searchPredicate = when (flowVersion) {
                FlowVersion.V1 -> labels?.get(0) ?: name
                FlowVersion.V2 -> name
                else -> if (ignoreFlowVersion) name else throw RuntimeException("Flow version is not supported ($flowVersion)")
            }
            "q$gqlQueryIdentifier: assets(tags: { contains: \"$searchPredicate\"}) { page { id tags { tagId } name category { id name sku_upc } } }"
        }

        private fun parseAsset(dataRoot: Map<String, AssetsPage>, tag: IResolveInfo): List<Asset> =
            with(tag) {
                mutableListOf<Asset>().apply {
                    dataRoot["q${gqlQueryIdentifier}"]
                        ?.page
                        ?.getOrNull(0)?.let {
                            this.add(it)
                        }
                }
            }
    }

    fun generateQuery(): String = String.format(
        QUERY_BOUNDS,
        buildItemLine(pData = tag, flowVersion = Wiliot.configuration.flowVersion, ignoreFlowVersion = useDefaultFlowVersion)
    )

    fun parseResponse(response: AssetsResponse): List<Asset>? = with(response) {
        data?.run {
            parseAsset(this, tag)
        }
    }
}
