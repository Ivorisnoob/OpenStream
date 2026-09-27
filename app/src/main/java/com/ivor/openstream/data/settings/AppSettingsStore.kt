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
    val downloadMaxHeight: Int = 1080
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
                .takeIf { it in AppSettings.DOWNLOAD_HEIGHTS } ?: defaults.downloadMaxHeight
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        const val KEY_THEME = "app_theme_mode"
        const val KEY_DYNAMIC_COLOR = "app_dynamic_color"
        const val KEY_DNS = "app_dns_provider"
        const val KEY_WIFI_ONLY = "app_wifi_only_downloads"
        const val KEY_SEEK_STEP = "app_seek_step_seconds"
        const val KEY_DEFAULT_SPEED = "app_default_speed"
        const val KEY_AUTO_PLAY_NEXT = "app_auto_play_next"
        const val KEY_DOWNLOAD_HEIGHT = "app_download_max_height"
    }
}
