package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbResponse<T>(
    @SerialName("page") val page: Int,
    @SerialName("results") val results: List<T>,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Int
)
