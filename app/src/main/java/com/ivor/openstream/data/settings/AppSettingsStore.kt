package com.ivor.openstream.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/**
 * Where the app resolves host names. Some ISPs (several in India) poison DNS answers for TMDB, so
 * the default asks a DNS-over-HTTPS resolver instead of the network's DNS. AdGuard's unfiltered
 * server is used so ad or tracker blocking can't break source hosts.
 */
enum class DnsProvider(
    val label: String,
    val url: String?,
    /** Resolver IPs, so reaching the resolver itself never depends on the network's DNS. */
    val bootstrapHosts: List<String>
) {
    SYSTEM("System", null, emptyList()),
    ADGUARD("AdGuard", "https://unfiltered.adguard-dns.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4"))
}

/** A button in the picture-in-picture window, beside play/pause. */
enum class PipAction(val label: String) {
    REWIND("Back"),
    FORWARD("Forward"),
    NEXT_EPISODE("Next"),
    SKIP_INTRO("Skip intro")
}

/** How the player searches installed sources. */
enum class SourceSearchMode(val label: String) {
    /** Every source at once; the list is sorted by quality, the user's order breaking ties. */
    ALL_AT_ONCE("All at once"),

    /** One source at a time, top of the user's order first, stopping at the first with streams. */
    IN_ORDER("In my order")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val dnsProvider: DnsProvider = DnsProvider.ADGUARD,
    val wifiOnlyDownloads: Boolean = false,
    /** Seconds per double-tap or seek button press. */
    val seekStepSeconds: Int = 10,
    /** Speed every title starts at. */
    val defaultSpeed: Float = 1f,
    /** Count down into the next episode when one ends. */
    val autoPlayNext: Boolean = true,
    /** Tallest rendition a download picks from a master playlist. */
    val downloadMaxHeight: Int = 1080,
    /** Picture-in-picture buttons left and right of play/pause (Android shows three at most). */
    val pipLeftAction: PipAction = PipAction.REWIND,
    val pipRightAction: PipAction = PipAction.FORWARD,
    /** ISO 639-1 codes whose subtitles are saved with every download; empty saves none. */
    val subtitleDownloadLanguages: List<String> = listOf("en"),
    /** Besides the video's own subtitles, also save OpenSubtitles and SubSource ones. */
    val subtitleDownloadFromSites: Boolean = true,
    val sourceSearchMode: SourceSearchMode = SourceSearchMode.ALL_AT_ONCE
) {
    companion object {
        val SEEK_STEPS = listOf(5, 10, 15, 30)
        val DEFAULT_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val DOWNLOAD_HEIGHTS = listOf(480, 720, 1080)
    }
}

/** App-wide preferences, persisted in the shared preferences file. */
@Singleton
class AppSettingsStore @Inject constructor(
    private val prefs: SharedPreferences
) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    private val _activeProfileId = MutableStateFlow(prefs.getLong(KEY_ACTIVE_PROFILE, DEFAULT_PROFILE_ID))

    /**
     * The profile whose library, progress and lists the app shows. Not part of [AppSettings]
     * because backups and restores shouldn't switch who's watching.
     */
    val activeProfileId: StateFlow<Long> = _activeProfileId.asStateFlow()

    fun setActiveProfile(id: Long) {
        _activeProfileId.value = id
        prefs.edit().putLong(KEY_ACTIVE_PROFILE, id).apply()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_THEME, updated.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, updated.dynamicColor)
            .putString(KEY_DNS, updated.dnsProvider.name)
            .putBoolean(KEY_WIFI_ONLY, updated.wifiOnlyDownloads)
            .putInt(KEY_SEEK_STEP, updated.seekStepSeconds)
            .putFloat(KEY_DEFAULT_SPEED, updated.defaultSpeed)
            .putBoolean(KEY_AUTO_PLAY_NEXT, updated.autoPlayNext)
            .putInt(KEY_DOWNLOAD_HEIGHT, updated.downloadMaxHeight)
            .putString(KEY_PIP_LEFT, updated.pipLeftAction.name)
            .putString(KEY_PIP_RIGHT, updated.pipRightAction.name)
            .putString(KEY_SUBTITLE_LANGUAGES, updated.subtitleDownloadLanguages.joinToString(","))
            .putBoolean(KEY_SUBTITLE_FROM_SITES, updated.subtitleDownloadFromSites)
            .putString(KEY_SOURCE_SEARCH_MODE, updated.sourceSearchMode.name)
            .apply()
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), defaults.themeMode),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
            dnsProvider = enumOrDefault(prefs.getString(KEY_DNS, null), defaults.dnsProvider),
            wifiOnlyDownloads = prefs.getBoolean(KEY_WIFI_ONLY, defaults.wifiOnlyDownloads),
            seekStepSeconds = prefs.getInt(KEY_SEEK_STEP, defaults.seekStepSeconds)
                .takeIf { it in AppSettings.SEEK_STEPS } ?: defaults.seekStepSeconds,
            defaultSpeed = prefs.getFloat(KEY_DEFAULT_SPEED, defaults.defaultSpeed)
                .takeIf { it in AppSettings.DEFAULT_SPEEDS } ?: defaults.defaultSpeed,
            autoPlayNext = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, defaults.autoPlayNext),
            downloadMaxHeight = prefs.getInt(KEY_DOWNLOAD_HEIGHT, defaults.downloadMaxHeight)
                .takeIf { it in AppSettings.DOWNLOAD_HEIGHTS } ?: defaults.downloadMaxHeight,
            pipLeftAction = enumOrDefault(prefs.getString(KEY_PIP_LEFT, null), defaults.pipLeftAction),
            pipRightAction = enumOrDefault(prefs.getString(KEY_PIP_RIGHT, null), defaults.pipRightAction),
            subtitleDownloadLanguages = prefs.getString(KEY_SUBTITLE_LANGUAGES, null)
                ?.let { parseLanguages(it) }
                ?: defaults.subtitleDownloadLanguages,
            subtitleDownloadFromSites = prefs.getBoolean(KEY_SUBTITLE_FROM_SITES, defaults.subtitleDownloadFromSites),
            sourceSearchMode = enumOrDefault(prefs.getString(KEY_SOURCE_SEARCH_MODE, null), defaults.sourceSearchMode)
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        /** "en,es" as stored; an empty string means none. */
        fun parseLanguages(stored: String): List<String> =
            stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        const val KEY_SUBTITLE_LANGUAGES = "app_subtitle_download_languages"
        const val KEY_SUBTITLE_FROM_SITES = "app_subtitle_download_from_sites"
        const val KEY_SOURCE_SEARCH_MODE = "app_source_search_mode"
        const val KEY_THEME = "app_theme_mode"
        const val KEY_DYNAMIC_COLOR = "app_dynamic_color"
        const val KEY_DNS = "app_dns_provider"
        const val KEY_WIFI_ONLY = "app_wifi_only_downloads"
        const val KEY_SEEK_STEP = "app_seek_step_seconds"
        const val KEY_DEFAULT_SPEED = "app_default_speed"
        const val KEY_AUTO_PLAY_NEXT = "app_auto_play_next"
        const val KEY_DOWNLOAD_HEIGHT = "app_download_max_height"
        const val KEY_ACTIVE_PROFILE = "app_active_profile_id"
        const val KEY_PIP_LEFT = "app_pip_left_action"
        const val KEY_PIP_RIGHT = "app_pip_right_action"
        /** Matches ProfileEntity.DEFAULT_ID, the profile the Room migration seeds. */
        const val DEFAULT_PROFILE_ID = 1L
    }
}
