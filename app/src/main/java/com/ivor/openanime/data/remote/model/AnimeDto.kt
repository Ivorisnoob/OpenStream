package com.ivor.openanime.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val tvName: String? = null,
    @SerialName("title") val movieTitle: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
    @SerialName("media_type") val mediaType: String? = "tv",
    @SerialName("original_language") val originalLanguage: String? = null
) {
    val name: String
        get() = movieTitle ?: tvName ?: ""

    val date: String
        get() = releaseDate ?: firstAirDate ?: ""

    val isMovie: Boolean
        get() = mediaType == "movie"
}

@Serializable
data class AnimeDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val tvName: String? = null,
    @SerialName("title") val movieTitle: String? = null,
    @SerialName("overview") val overview: String,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("seasons") val seasons: List<SeasonDto>? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("genres") val genres: List<GenreDto>? = null,
    @SerialName("production_companies") val productionCompanies: List<ProductionCompanyDto>? = null,
    @SerialName("homepage") val homepage: String? = null
) {
    val name: String
        get() = movieTitle ?: tvName ?: ""

    val date: String
        get() = releaseDate ?: firstAirDate ?: ""
}

@Serializable
data class GenreDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String
)

@Serializable
data class ProductionCompanyDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("origin_country") val originCountry: String? = null
)

fun AnimeDetailsDto.toAnimeDto(mediaType: String): AnimeDto {
    return AnimeDto(
        id = id,
        tvName = tvName,
        movieTitle = movieTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        firstAirDate = firstAirDate,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        mediaType = mediaType
    )
}

@Serializable
data class SeasonDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("overview") val overview: String,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_count") val episodeCount: Int,
    @SerialName("air_date") val airDate: String?
)
