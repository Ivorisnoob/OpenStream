package com.ivor.openstream.domain.repository

import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.domain.model.MediaIdentity

interface SubtitleRepository {
    /** External subtitles for a movie or episode, best matches first. Empty when none are found. */
    suspend fun search(identity: MediaIdentity): List<SubtitleDto>

    /** More releases in one language ([language] is ISO 639-1), including ones [search] leaves out. */
    suspend fun searchLanguage(identity: MediaIdentity, language: String): List<SubtitleDto> =
        search(identity).filter { it.language == language }
}
