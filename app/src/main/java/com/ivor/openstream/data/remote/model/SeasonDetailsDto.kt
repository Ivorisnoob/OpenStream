package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeasonDetailsDto(
    @SerialName("_id") val _id: String,
    @SerialName("air_date") val airDate: String?,
    @SerialName("episodes") val episodes: List<EpisodeDto>,
    @SerialName("name") val name: String,
    @SerialName("overview") val overview: String,
    @SerialName("id") val id: Int,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("season_number") val seasonNumber: Int
)

@Serializable
data class EpisodeDto(
    @SerialName("air_date") val airDate: String?,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("overview") val overview: String,
    @SerialName("production_code") val productionCode: String,
    @SerialName("runtime") val runtime: Int?,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("show_id") val showId: Int,
    @SerialName("still_path") val stillPath: String?,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("vote_count") val voteCount: Int
)
