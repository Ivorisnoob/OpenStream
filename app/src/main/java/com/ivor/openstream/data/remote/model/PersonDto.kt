package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PersonDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("biography") val biography: String? = null,
    @SerialName("birthday") val birthday: String? = null,
    @SerialName("deathday") val deathday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    @SerialName("combined_credits") val combinedCredits: PersonCreditsDto? = null
)

/** Movies and series a person worked on; entries carry `media_type`, so they parse as [AnimeDto]. */
@Serializable
data class PersonCreditsDto(
    @SerialName("cast") val cast: List<AnimeDto> = emptyList(),
    @SerialName("crew") val crew: List<AnimeDto> = emptyList()
)
