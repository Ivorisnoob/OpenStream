package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseDatesDto(val results: List<CountryReleaseDates> = emptyList())

@Serializable
data class CountryReleaseDates(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val releaseDates: List<ReleaseDateDto> = emptyList()
)

@Serializable
data class ReleaseDateDto(val certification: String = "")

@Serializable
data class ContentRatingsDto(val results: List<ContentRatingDto> = emptyList())

@Serializable
data class ContentRatingDto(
    @SerialName("iso_3166_1") val country: String = "",
    val rating: String = ""
)
