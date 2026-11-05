package com.ivor.openstream.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the player remembers between titles: the caption style and the audio and subtitle language
 * last picked. Shared by the player and Settings, so a change in one shows in the other at once.
 * Kept under the keys the player always used, so nothing is lost on update.
 */
@Singleton
class PlayerPreferences @Inject constructor(
    private val prefs: SharedPreferences,
    private val json: Json
) {
    private val _captionStyle = MutableStateFlow(loadCaptionStyle())
    val captionStyle: StateFlow<CaptionStyleSettings> = _captionStyle.asStateFlow()

    /** Audio language last picked (for example `en` for dubs), or null to follow each stream. */
    private val _audioLanguage = MutableStateFlow(prefs.getString(KEY_AUDIO, null))
    val audioLanguage: StateFlow<String?> = _audioLanguage.asStateFlow()

    /**
     * Subtitle language last picked, [SUBTITLES_OFF] when subtitles were turned off, or null when
     * nothing was ever chosen.
     */
    private val _subtitleLanguage = MutableStateFlow(prefs.getString(KEY_SUBTITLE, null))
    val subtitleLanguage: StateFlow<String?> = _subtitleLanguage.asStateFlow()

    fun setCaptionStyle(style: CaptionStyleSettings) {
        _captionStyle.value = style
        prefs.edit().putString(KEY_CAPTION_STYLE, json.encodeToString(CaptionStyleSettings.serializer(), style)).apply()
    }

    fun setAudioLanguage(language: String?) {
        _audioLanguage.value = language
        prefs.edit().putString(KEY_AUDIO, language).apply()
    }

    /** [language] null forgets the choice, so the player decides again (and asks nothing). */
    fun setSubtitleLanguage(language: String?) {
        _subtitleLanguage.value = language
        prefs.edit().putString(KEY_SUBTITLE, language).apply()
    }

    private fun loadCaptionStyle(): CaptionStyleSettings = try {
        prefs.getString(KEY_CAPTION_STYLE, null)
            ?.let { json.decodeFromString(CaptionStyleSettings.serializer(), it) }
            ?: CaptionStyleSettings()
    } catch (_: Exception) {
        CaptionStyleSettings()
    }

    companion object {
        /** Stored when the user turned subtitles off; the player's SUBTITLES_OFF has the same value. */
        const val SUBTITLES_OFF = "off"

        private const val KEY_CAPTION_STYLE = "caption_style"
        private const val KEY_AUDIO = "preferred_audio_language"
        private const val KEY_SUBTITLE = "preferred_subtitle_language"
    }
}
