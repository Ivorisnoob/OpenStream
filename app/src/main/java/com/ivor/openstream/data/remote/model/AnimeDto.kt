package com.ivor.openstream.data.remote.model

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
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("popularity") val popularity: Double? = null
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
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("seasons") val seasons: List<SeasonDto>? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("genres") val genres: List<GenreDto>? = null,
    @SerialName("production_companies") val productionCompanies: List<ProductionCompanyDto>? = null,
    @SerialName("homepage") val homepage: String? = null,
    @SerialName("videos") val videos: VideoResponseDto? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
    @SerialName("networks") val networks: List<ProductionCompanyDto>? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: AiringEpisodeDto? = null,
    /** Movies: `credits`. Series: `aggregate_credits`, which spans every season. */
    @SerialName("credits") val credits: CreditsDto? = null,
    @SerialName("aggregate_credits") val aggregateCredits: CreditsDto? = null,
    @SerialName("recommendations") val recommendations: TmdbResponse<AnimeDto>? = null
) {
    val name: String
        get() = movieTitle ?: tvName ?: ""

    val date: String
        get() = releaseDate ?: firstAirDate ?: ""

    val nativeTitle: String?
        get() = (originalName ?: originalTitle)?.takeIf { it.isNotBlank() && it != name }

    val cast: List<CastDto>
        get() = (aggregateCredits ?: credits)?.cast.orEmpty()

    /** Minutes per episode for series, or the film's length. */
    val typicalRuntime: Int?
        get() = runtime ?: episodeRunTime?.firstOrNull()
}

@Serializable
data class AiringEpisodeDto(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("name") val name: String? = null
)

@Serializable
data class CreditsDto(
    @SerialName("cast") val cast: List<CastDto> = emptyList()
)

@Serializable
data class CastDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("character") val character: String? = null,
    @SerialName("roles") val roles: List<CastRoleDto>? = null,
    @SerialName("profile_path") val profilePath: String? = null
) {
    val role: String?
        get() = (character ?: roles?.firstOrNull()?.character)?.takeIf { it.isNotBlank() }
}

@Serializable
data class CastRoleDto(
    @SerialName("character") val character: String? = null
)

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

@Serializable
data class VideoResponseDto(
    @SerialName("results") val results: List<VideoDto>
)

@Serializable
data class VideoDto(
    @SerialName("id") val id: String,
    @SerialName("key") val key: String,
    @SerialName("name") val name: String,
    @SerialName("site") val site: String,
    @SerialName("type") val type: String
)
