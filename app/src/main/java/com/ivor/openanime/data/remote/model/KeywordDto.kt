package com.ivor.openanime.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeywordDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String
)
