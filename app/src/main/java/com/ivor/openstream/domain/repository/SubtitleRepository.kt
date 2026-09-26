package com.ivor.openstream.domain.repository

import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.domain.model.MediaIdentity

interface SubtitleRepository {
    /** External subtitles for a movie or episode, best matches first. Empty when none are found. */
    suspend fun search(identity: MediaIdentity): List<SubtitleDto>
}
