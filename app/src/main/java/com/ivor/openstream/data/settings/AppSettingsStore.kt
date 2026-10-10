package com.ivor.openstream.data.settings

import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.ivor.openstream.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String, @StringRes val labelRes: Int) {
    SYSTEM("System", R.string.st_theme_system),
    LIGHT("Light", R.string.st_theme_light),
    DARK("Dark", R.string.st_theme_dark)
}

/**
 * Where the app resolves host names. Some ISPs (several in India) poison DNS answers for TMDB, so
 * the default asks a DNS-over-HTTPS resolver instead of the network's DNS. AdGuard's unfiltered
 * server is used so ad or tracker blocking can't break source hosts.
 */
enum class DnsProvider(
    val label: String,
    @StringRes val labelRes: Int,
    val url: String?,
    /** Resolver IPs, so reaching the resolver itself never depends on the network's DNS. */
    val bootstrapHosts: List<String>
) {
    SYSTEM("System", R.string.st_dns_system, null, emptyList()),
    ADGUARD("AdGuard", R.string.dns_name_adguard, "https://unfiltered.adguard-dns.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
    CLOUDFLARE("Cloudflare", R.string.dns_name_cloudflare, "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google", R.string.dns_name_google, "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4"))
}

/** A button in the picture-in-picture window, beside play/pause. */
enum class PipAction(val label: String, @StringRes val labelRes: Int) {
    REWIND("Back", R.string.pip_back),
    FORWARD("Forward", R.string.pip_forward),
    NEXT_EPISODE("Next", R.string.pip_next),
    SKIP_INTRO("Skip intro", R.string.player_skip_intro)
}

/** How the player searches installed sources. */
enum class SourceSearchMode(val label: String, @StringRes val labelRes: Int) {
    /** Every source at once; the list is sorted by quality, the user's order breaking ties. */
    ALL_AT_ONCE("All at once", R.string.sheet_search_all),

    /** One source at a time, top of the user's order first, stopping at the first with streams. */
    IN_ORDER("In my order", R.string.sheet_search_in_order)
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
    /** Offer the skip intro / recap / credits button over the video. */
    val showSkipButton: Boolean = true,
    /** Tallest rendition a download picks from a master playlist. */
    val downloadMaxHeight: Int = 1080,
    /** Picture-in-picture buttons left and right of play/pause (Android shows three at most). */
    val pipLeftAction: PipAction = PipAction.REWIND,
    val pipRightAction: PipAction = PipAction.FORWARD,
    /** ISO 639-1 codes whose subtitles are saved with every download; empty saves none. */
    val subtitleDownloadLanguages: List<String> = listOf("en"),
    /** Besides the video's own subtitles, also save OpenSubtitles and SubSource ones. */
    val subtitleDownloadFromSites: Boolean = true,
    val sourceSearchMode: SourceSearchMode = SourceSearchMode.ALL_AT_ONCE,
    /** BCP-47 app language tag, or null to follow the system locale. */
    val appLanguage: String? = null,
    /** Daily check of followed shows for newly aired episodes. */
    val episodeNotifications: Boolean = true,
    /** Auto-download the next unwatched episodes and delete watched ones. */
    val smartDownloads: Boolean = false,
    /** How many episodes ahead Smart Downloads keeps (1..3). */
    val smartKeepAhead: Int = 2,
    /** Playback keeps going when the app leaves the foreground, with a media notification. */
    val keepPlayingInBackground: Boolean = true,
    /** Ask before a stream runs on a metered network (once per app run). */
    val warnBeforeMeteredStream: Boolean = true,
    /** Tallest rendition allowed while streaming on a metered network; 0 means no limit. */
    val meteredMaxHeight: Int = 720,
    /** Real-time video frame interpolation (up to 60 or 120 fps). Off by default to save battery. */
    val smoothMotionEnabled: Boolean = false,
    /** Output FPS ceiling for Smooth Motion (60 or 120). */
    val smoothMotionMaxFps: Int = 120
) {
    companion object {
        val SEEK_STEPS = listOf(5, 10, 15, 30)
        val DEFAULT_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val DOWNLOAD_HEIGHTS = listOf(480, 720, 1080)
        val SMART_AHEAD_OPTIONS = listOf(1, 2, 3)
        /** Quality caps offered for metered networks: 720p, 480p and no limit. */
        val METERED_CAPS = listOf(720, 480, 0)
        val SMOOTH_MOTION_FPS_OPTIONS = listOf(60, 120)
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
            .putBoolean(KEY_SHOW_SKIP_BUTTON, updated.showSkipButton)
            .putInt(KEY_DOWNLOAD_HEIGHT, updated.downloadMaxHeight)
            .putString(KEY_PIP_LEFT, updated.pipLeftAction.name)
            .putString(KEY_PIP_RIGHT, updated.pipRightAction.name)
            .putString(KEY_SUBTITLE_LANGUAGES, updated.subtitleDownloadLanguages.joinToString(","))
            .putBoolean(KEY_SUBTITLE_FROM_SITES, updated.subtitleDownloadFromSites)
            .putString(KEY_SOURCE_SEARCH_MODE, updated.sourceSearchMode.name)
            .putString(KEY_APP_LANGUAGE, updated.appLanguage)
            .putBoolean(KEY_EPISODE_NOTIFICATIONS, updated.episodeNotifications)
            .putBoolean(KEY_SMART_DOWNLOADS, updated.smartDownloads)
            .putInt(KEY_SMART_AHEAD, updated.smartKeepAhead)
            .putBoolean(KEY_BACKGROUND_PLAYBACK, updated.keepPlayingInBackground)
            .putBoolean(KEY_WARN_METERED, updated.warnBeforeMeteredStream)
            .putInt(KEY_METERED_HEIGHT, updated.meteredMaxHeight)
            .putBoolean(KEY_SMOOTH_MOTION_ENABLED, updated.smoothMotionEnabled)
            .putInt(KEY_SMOOTH_MOTION_MAX_FPS, updated.smoothMotionMaxFps)
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
            showSkipButton = prefs.getBoolean(KEY_SHOW_SKIP_BUTTON, defaults.showSkipButton),
            downloadMaxHeight = prefs.getInt(KEY_DOWNLOAD_HEIGHT, defaults.downloadMaxHeight)
                .takeIf { it in AppSettings.DOWNLOAD_HEIGHTS } ?: defaults.downloadMaxHeight,
            pipLeftAction = enumOrDefault(prefs.getString(KEY_PIP_LEFT, null), defaults.pipLeftAction),
            pipRightAction = enumOrDefault(prefs.getString(KEY_PIP_RIGHT, null), defaults.pipRightAction),
            subtitleDownloadLanguages = prefs.getString(KEY_SUBTITLE_LANGUAGES, null)
                ?.let { parseLanguages(it) }
                ?: defaults.subtitleDownloadLanguages,
            subtitleDownloadFromSites = prefs.getBoolean(KEY_SUBTITLE_FROM_SITES, defaults.subtitleDownloadFromSites),
            sourceSearchMode = enumOrDefault(prefs.getString(KEY_SOURCE_SEARCH_MODE, null), defaults.sourceSearchMode),
            appLanguage = prefs.getString(KEY_APP_LANGUAGE, null),
            episodeNotifications = prefs.getBoolean(KEY_EPISODE_NOTIFICATIONS, defaults.episodeNotifications),
            smartDownloads = prefs.getBoolean(KEY_SMART_DOWNLOADS, defaults.smartDownloads),
            smartKeepAhead = prefs.getInt(KEY_SMART_AHEAD, defaults.smartKeepAhead)
                .takeIf { it in AppSettings.SMART_AHEAD_OPTIONS } ?: defaults.smartKeepAhead,
            keepPlayingInBackground = prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, defaults.keepPlayingInBackground),
            warnBeforeMeteredStream = prefs.getBoolean(KEY_WARN_METERED, defaults.warnBeforeMeteredStream),
            meteredMaxHeight = prefs.getInt(KEY_METERED_HEIGHT, defaults.meteredMaxHeight)
                .takeIf { it in AppSettings.METERED_CAPS } ?: defaults.meteredMaxHeight,
            smoothMotionEnabled = prefs.getBoolean(KEY_SMOOTH_MOTION_ENABLED, defaults.smoothMotionEnabled),
            smoothMotionMaxFps = prefs.getInt(KEY_SMOOTH_MOTION_MAX_FPS, defaults.smoothMotionMaxFps)
                .takeIf { it in AppSettings.SMOOTH_MOTION_FPS_OPTIONS } ?: defaults.smoothMotionMaxFps
        )
    }

    fun setAppLanguage(tag: String?) = update { it.copy(appLanguage = tag) }

    fun setEpisodeNotifications(enabled: Boolean) = update { it.copy(episodeNotifications = enabled) }

    fun setSmartDownloads(enabled: Boolean) = update { it.copy(smartDownloads = enabled) }

    fun setSmartKeepAhead(count: Int) =
        update { it.copy(smartKeepAhead = count.takeIf { it in AppSettings.SMART_AHEAD_OPTIONS } ?: AppSettings().smartKeepAhead) }

    fun setKeepPlayingInBackground(enabled: Boolean) = update { it.copy(keepPlayingInBackground = enabled) }

    fun setWarnBeforeMeteredStream(enabled: Boolean) = update { it.copy(warnBeforeMeteredStream = enabled) }

    /** 0 clears the cap, so metered networks stream at the source's best quality. */
    fun setMeteredMaxHeight(height: Int) =
        update { it.copy(meteredMaxHeight = height.takeIf { it in AppSettings.METERED_CAPS } ?: 0) }

    fun setSmoothMotionEnabled(enabled: Boolean) = update { it.copy(smoothMotionEnabled = enabled) }

    fun setSmoothMotionMaxFps(fps: Int) =
        update { it.copy(smoothMotionMaxFps = fps.takeIf { it in AppSettings.SMOOTH_MOTION_FPS_OPTIONS } ?: 120) }

    // region Profile PIN lockout (attempt counting survives restarts)

    fun pinAttempts(profileId: Long): Int = prefs.getInt(pinAttemptsKey(profileId), 0)

    fun setPinAttempts(profileId: Long, attempts: Int) {
        prefs.edit().putInt(pinAttemptsKey(profileId), attempts).apply()
    }

    fun pinLockoutUntil(profileId: Long): Long = prefs.getLong(pinLockoutKey(profileId), 0L)

    fun setPinLockout(profileId: Long, untilMs: Long) {
        prefs.edit().putLong(pinLockoutKey(profileId), untilMs).apply()
    }

    fun clearPinLockout(profileId: Long) {
        prefs.edit().remove(pinAttemptsKey(profileId)).remove(pinLockoutKey(profileId)).apply()
    }

    private fun pinAttemptsKey(profileId: Long) = "pin_attempts_$profileId"

    private fun pinLockoutKey(profileId: Long) = "pin_lockout_$profileId"

    // endregion

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        /** "en,es" as stored; an empty string means none. */
        fun parseLanguages(stored: String): List<String> =
            stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        const val KEY_SUBTITLE_LANGUAGES = "app_subtitle_download_languages"
        const val KEY_SUBTITLE_FROM_SITES = "app_subtitle_download_from_sites"
        const val KEY_SOURCE_SEARCH_MODE = "app_source_search_mode"
        const val KEY_APP_LANGUAGE = AppLocale.KEY_APP_LANGUAGE
        const val KEY_EPISODE_NOTIFICATIONS = "app_episode_notifications"
        const val KEY_SMART_DOWNLOADS = "app_smart_downloads"
        const val KEY_SMART_AHEAD = "app_smart_keep_ahead"
        const val KEY_BACKGROUND_PLAYBACK = "app_background_playback"
        const val KEY_WARN_METERED = "app_warn_metered_stream"
        const val KEY_METERED_HEIGHT = "app_metered_max_height"
        const val KEY_SMOOTH_MOTION_ENABLED = "app_smooth_motion_enabled"
        const val KEY_SMOOTH_MOTION_MAX_FPS = "app_smooth_motion_max_fps"
        const val KEY_THEME = "app_theme_mode"
        const val KEY_DYNAMIC_COLOR = "app_dynamic_color"
        const val KEY_DNS = "app_dns_provider"
        const val KEY_WIFI_ONLY = "app_wifi_only_downloads"
        const val KEY_SEEK_STEP = "app_seek_step_seconds"
        const val KEY_DEFAULT_SPEED = "app_default_speed"
        const val KEY_AUTO_PLAY_NEXT = "app_auto_play_next"
        const val KEY_SHOW_SKIP_BUTTON = "app_show_skip_button"
        const val KEY_DOWNLOAD_HEIGHT = "app_download_max_height"
        const val KEY_ACTIVE_PROFILE = "app_active_profile_id"
        const val KEY_PIP_LEFT = "app_pip_left_action"
        const val KEY_PIP_RIGHT = "app_pip_right_action"
        /** Matches ProfileEntity.DEFAULT_ID, the profile the Room migration seeds. */
        const val DEFAULT_PROFILE_ID = 1L
    }
}
