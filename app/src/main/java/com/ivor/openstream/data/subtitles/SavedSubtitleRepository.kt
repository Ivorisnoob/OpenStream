package com.ivor.openstream.data.subtitles

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.domain.model.DownloadTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A subtitle file kept on the device for one movie or episode. */
@Serializable
data class SavedSubtitle(
    val id: String,
    val fileName: String,
    /** ISO 639-1 when known. */
    val language: String? = null,
    val label: String,
    val release: String? = null,
    /** Where it came from: a subtitle site's name, or [SavedSubtitleRepository.IMPORTED]. */
    val origin: String? = null,
    /** The [SubtitleDto.id] it was saved from, so the same file is not listed twice. */
    val originId: String? = null,
    val isHearingImpaired: Boolean = false,
    val savedAt: Long = System.currentTimeMillis()
)

/**
 * Subtitles saved for offline viewing, one folder per movie or episode under `filesDir/subtitles`
 * holding the files (already unzipped and decoded to UTF-8) and an `index.json`.
 *
 * Folders are keyed by title and episode rather than by download, so the player finds them
 * whether the episode plays from a download or a stream.
 */
@Singleton
class SavedSubtitleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: SubtitleFetcher,
    private val json: Json
) {
    private val changes = MutableStateFlow(0L)
    private val writeLock = Mutex()
    private val indexSerializer = ListSerializer(SavedSubtitle.serializer())

    fun observe(mediaType: String, tmdbId: Int, season: Int, episode: Int): Flow<List<SavedSubtitle>> {
        val dir = directory(mediaType, tmdbId, season, episode)
        return changes.map { readIndex(dir) }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    /** How many subtitles each folder holds, by [keyFor]. */
    fun observeCounts(): Flow<Map<String, Int>> = changes
        .map {
            rootDirectory(context).listFiles().orEmpty()
                .filter { it.isDirectory }
                .associate { it.name to readIndex(it).size }
                .filterValues { it > 0 }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /** The player's entry for [saved]: a local file read back through [SubtitleFetcher]. */
    fun toSubtitleDto(mediaType: String, tmdbId: Int, season: Int, episode: Int, saved: SavedSubtitle) = SubtitleDto(
        id = "saved_${saved.id}",
        url = Uri.fromFile(File(directory(mediaType, tmdbId, season, episode), saved.fileName)).toString(),
        display = saved.label,
        language = saved.language,
        isHearingImpaired = saved.isHearingImpaired,
        source = SOURCE_NAME,
        release = saved.release ?: saved.origin
    )

    /** Downloads [subtitle] and keeps it. Saving the same one again returns the copy already kept. */
    suspend fun save(
        mediaType: String,
        tmdbId: Int,
        season: Int,
        episode: Int,
        subtitle: SubtitleDto,
        headers: Map<String, String> = emptyMap()
    ): SavedSubtitle = withContext(Dispatchers.IO) {
        val dir = directory(mediaType, tmdbId, season, episode)
        readIndex(dir).firstOrNull { it.originId == subtitle.id }?.let { return@withContext it }
        val text = fetcher.fetchText(subtitle.url, headers)
        if (parseSubtitles(text).isEmpty()) throw IOException("That file has no readable subtitles")
        add(
            dir,
            text,
            SavedSubtitle(
                id = newId(),
                fileName = "",
                language = subtitle.language,
                label = subtitle.display ?: subtitle.language?.let { languageName(it) } ?: "Subtitles",
                release = subtitle.release,
                origin = subtitle.source,
                originId = subtitle.id,
                isHearingImpaired = subtitle.isHearingImpaired
            )
        )
    }

    /** Copies a subtitle file the user picked (SRT, VTT, ASS, or a zip/gz holding one). */
    suspend fun import(mediaType: String, tmdbId: Int, season: Int, episode: Int, uri: Uri): SavedSubtitle =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
                if (buffer.size > MAX_IMPORT_BYTES) throw IOException("That file is too large for a subtitle")
                buffer
            } ?: throw IOException("Could not open that file")
            val text = fetcher.textOf(bytes)
            if (parseSubtitles(text).isEmpty()) throw IOException("No subtitles found in that file")

            val baseName = name.substringBeforeLast('.').ifBlank { "Subtitles" }
            val language = guessLanguage(baseName)
            add(
                directory(mediaType, tmdbId, season, episode),
                text,
                SavedSubtitle(
                    id = newId(),
                    fileName = "",
                    language = language,
                    label = language?.let { languageName(it) } ?: baseName,
                    release = baseName.takeIf { language != null },
                    origin = IMPORTED,
                    isHearingImpaired = tokens(baseName).any { it in HEARING_IMPAIRED_TAGS && !(it == "hi" && language == "hi") }
                )
            )
        }

    suspend fun delete(mediaType: String, tmdbId: Int, season: Int, episode: Int, id: String) = withContext(Dispatchers.IO) {
        val dir = directory(mediaType, tmdbId, season, episode)
        writeLock.withLock {
            val index = readIndex(dir)
            val removed = index.firstOrNull { it.id == id } ?: return@withLock
            File(dir, removed.fileName).delete()
            writeIndex(dir, index - removed)
        }
        changes.update { it + 1 }
    }

    /** Removes every subtitle saved for this movie or episode. */
    suspend fun deleteAll(mediaType: String, tmdbId: Int, season: Int, episode: Int) = withContext(Dispatchers.IO) {
        writeLock.withLock { directory(mediaType, tmdbId, season, episode).deleteRecursively() }
        changes.update { it + 1 }
    }

    private suspend fun add(dir: File, text: String, entry: SavedSubtitle): SavedSubtitle = withContext(Dispatchers.IO) {
        val saved = entry.copy(fileName = "${entry.id}.${extensionFor(text)}")
        writeLock.withLock {
            dir.mkdirs()
            File(dir, saved.fileName).writeText(text)
            writeIndex(dir, readIndex(dir) + saved)
        }
        changes.update { it + 1 }
        saved
    }

    private fun readIndex(dir: File): List<SavedSubtitle> {
        val file = File(dir, INDEX_FILE)
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(indexSerializer, file.readText()) }
            .getOrDefault(emptyList())
            // A file deleted from outside the app should not stay listed.
            .filter { File(dir, it.fileName).exists() }
    }

    private fun writeIndex(dir: File, entries: List<SavedSubtitle>) {
        if (entries.isEmpty()) {
            dir.deleteRecursively()
            return
        }
        val temp = File(dir, "$INDEX_FILE.tmp")
        temp.writeText(json.encodeToString(indexSerializer, entries))
        if (!temp.renameTo(File(dir, INDEX_FILE))) throw IOException("Could not update saved subtitles")
    }

    private fun directory(mediaType: String, tmdbId: Int, season: Int, episode: Int) =
        File(rootDirectory(context), keyFor(mediaType, tmdbId, season, episode))

    private fun newId() = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun extensionFor(text: String): String {
        val head = text.trimStart().take(200)
        return when {
            head.startsWith("WEBVTT") -> "vtt"
            "[Script Info]" in head || "[Events]" in text -> "ass"
            else -> "srt"
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        /** Source name the player shows under a saved subtitle. */
        const val SOURCE_NAME = "Saved"
        const val IMPORTED = "Imported"
        private const val INDEX_FILE = "index.json"
        private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        private val HEARING_IMPAIRED_TAGS = setOf("sdh", "hi", "cc")

        fun rootDirectory(context: Context) = File(context.filesDir, "subtitles")

        /** Same as the download id, so movies use season and episode 1 as downloads do. */
        fun keyFor(mediaType: String, tmdbId: Int, season: Int, episode: Int): String =
            if (mediaType == "movie") {
                DownloadTarget.downloadIdFor(mediaType, tmdbId, 1, 1)
            } else {
                DownloadTarget.downloadIdFor(mediaType, tmdbId, season, episode)
            }

        fun languageName(code: String): String {
            // OpenSubtitles' own codes, not ISO 639-1.
            when (code) {
                "pb" -> return "Portuguese (Brazil)"
                "zt" -> return "Chinese (Traditional)"
                "ze" -> return "Chinese and English"
            }
            val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
            return if (name.isBlank() || name.equals(code, ignoreCase = true)) code.uppercase(Locale.ROOT) else name
        }

        private fun tokens(name: String) = name.lowercase(Locale.ROOT).split(Regex("[^\\p{L}0-9]+")).filter { it.isNotEmpty() }

        /**
         * The language a file name ends with, like "Show.S01E02.1080p.es.srt" or
         * "Movie (Spanish).srt". "hi" usually marks hearing-impaired subtitles rather than Hindi,
         * so it only counts when nothing else matches.
         */
        internal fun guessLanguage(fileName: String): String? {
            val iso2 = Locale.getISOLanguages().toSet()
            val byIso3 = Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language }
            val byName = Locale.getISOLanguages().associateBy {
                Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
            }
            val tokens = tokens(fileName)
            val matches = tokens.mapIndexedNotNull { index, token ->
                // Short codes only near the end, where they mark the language rather than a title word ("It").
                val isTail = index >= tokens.size - 3
                when {
                    token.length == 2 && isTail && token in iso2 -> token
                    token.length == 3 && isTail && token in BIBLIOGRAPHIC_TO_ISO2 -> BIBLIOGRAPHIC_TO_ISO2[token]
                    token.length == 3 && isTail && token in byIso3 -> byIso3[token]
                    token.length > 3 -> byName[token]
                    else -> null
                }
            }
            return matches.lastOrNull { it != "hi" } ?: matches.lastOrNull()
        }

        private val BIBLIOGRAPHIC_TO_ISO2 = mapOf(
            "ger" to "de", "fre" to "fr", "chi" to "zh", "cze" to "cs", "dut" to "nl", "gre" to "el",
            "per" to "fa", "rum" to "ro", "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my",
            "geo" to "ka", "ice" to "is", "mac" to "mk", "may" to "ms", "slo" to "sk", "wel" to "cy"
        )
    }
}
