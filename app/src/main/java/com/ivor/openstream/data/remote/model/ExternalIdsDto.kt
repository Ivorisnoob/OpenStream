package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalIdsDto(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null
)
