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
    val wifiOnlyDownloads: Boolean = false
)

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
            .apply()
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), defaults.themeMode),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
            dnsProvider = enumOrDefault(prefs.getString(KEY_DNS, null), defaults.dnsProvider),
            wifiOnlyDownloads = prefs.getBoolean(KEY_WIFI_ONLY, defaults.wifiOnlyDownloads)
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        const val KEY_THEME = "app_theme_mode"
        const val KEY_DYNAMIC_COLOR = "app_dynamic_color"
        const val KEY_DNS = "app_dns_provider"
        const val KEY_WIFI_ONLY = "app_wifi_only_downloads"
    }
}
