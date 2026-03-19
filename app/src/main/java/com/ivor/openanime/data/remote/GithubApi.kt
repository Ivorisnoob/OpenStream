package com.ivor.openanime.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("assets") val assets: List<GithubAssetDto> = emptyList()
)

@Serializable
data class GithubAssetDto(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("size") val size: Long
)

interface GithubApi {
    @GET("repos/ivorisnoob/openstream/releases/latest")
    suspend fun getLatestRelease(): GithubReleaseDto
}
