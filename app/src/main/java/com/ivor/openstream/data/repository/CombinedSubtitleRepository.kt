package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.repository.SubtitleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every keyless subtitle source, searched in parallel: OpenSubtitles first, then SubSource.
 * Each result keeps its source name, which the player shows under the language.
 */
@Singleton
class CombinedSubtitleRepository @Inject constructor(
    private val openSubtitles: OpenSubtitlesRepository,
    private val subSource: SubSourceRepository
) : SubtitleRepository {
    override suspend fun search(identity: MediaIdentity): List<SubtitleDto> = coroutineScope {
        val fromOpenSubtitles = async { runCatching { openSubtitles.search(identity) }.getOrDefault(emptyList()) }
        val fromSubSource = async { subSource.search(identity) }
        (fromOpenSubtitles.await() + fromSubSource.await())
            .distinctBy { it.url }
            // The same release uploaded to both sites: keep the first copy.
            .distinctBy { listOf(it.language, it.release?.lowercase()?.trim(), it.isHearingImpaired).takeIf { _ -> it.release != null } ?: it.url }
            .sortedBy { if (it.language == "en") 0 else 1 }
    }

    override suspend fun searchLanguage(identity: MediaIdentity, language: String): List<SubtitleDto> = coroutineScope {
        val fromOpenSubtitles = async {
            runCatching { openSubtitles.searchLanguage(identity, language) }.getOrDefault(emptyList())
        }
        val fromSubSource = async { subSource.searchLanguage(identity, language) }
        (fromOpenSubtitles.await() + fromSubSource.await()).distinctBy { it.url }
    }
}
