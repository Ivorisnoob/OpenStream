package com.ivor.openanime.data.remote

import com.ivor.openanime.data.remote.model.SubtitleDto
import retrofit2.http.GET
import retrofit2.http.Query

interface SubtitleApi {
    @GET("https://sub.wyzie.ru/search")
    suspend fun searchSubtitles(
        @Query("id") tmdbId: Int,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null
    ): kotlinx.serialization.json.JsonElement
}
