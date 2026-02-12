package com.ivor.openanime.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubtitleDto(
    @SerialName("id") val id: String,
    @SerialName("url") val url: String,
    @SerialName("display") val display: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("isHearingImpaired") val isHearingImpaired: Boolean = false,
    @SerialName("source") val source: String? = null,
    @SerialName("release") val release: String? = null,
    @SerialName("origin") val origin: String? = null
)
