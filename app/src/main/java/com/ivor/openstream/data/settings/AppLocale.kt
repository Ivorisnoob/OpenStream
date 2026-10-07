package com.ivor.openstream.data.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App language selection. Null (the default) follows the system locale; otherwise the app runs
 * in the chosen language regardless of the system setting.
 *
 * Implementation notes:
 * - API 33+: the platform [android.app.LocaleManager] persists per-app locales, so the system
 *   applies them to every context automatically. We still mirror the choice in our own prefs so
 *   the Settings UI can show it and so pre-33 devices share the same code path.
 * - API 26–32: the saved tag is applied by wrapping contexts in [wrap] (called from the
 *   Application and MainActivity [android.content.ContextWrapper.attachBaseContext]). Because the
 *   application object is created once per process, [setLanguage] also pushes the new
 *   configuration into its resources and then restarts the task so every screen, ViewModel and
 *   service context picks it up.
 */
object AppLocale {

    const val PREFS_NAME = "open_stream_prefs"
    const val KEY_APP_LANGUAGE = "app_language"

    /** BCP-47 tags offered in Settings, in display order. */
    val supportedTags: List<String> = listOf(
        "es", "fr", "de", "it", "pt", "ru", "uk", "zh-CN", "ja", "ko", "hi", "ar"
    )

    /** Autonyms: language names shown in Settings, intentionally never translated. */
    fun autonym(tag: String): String = when (tag) {
        "es" -> "Español"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "it" -> "Italiano"
        "pt" -> "Português"
        "ru" -> "Русский"
        "uk" -> "Українська"
        "zh-CN" -> "中文（简体）"
        "ja" -> "日本語"
        "ko" -> "한국어"
        "hi" -> "हिन्दी"
        "ar" -> "العربية"
        "en" -> "English"
        else -> tag
    }

    fun savedTag(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, null)

    fun localeFor(tag: String): Locale = Locale.forLanguageTag(tag)

    /** Returns a context whose resources resolve in [tag] (null = untouched system default). */
    fun wrap(context: Context, tag: String?): Context {
        if (tag.isNullOrBlank()) return context
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /** Applies [tag] everywhere and restarts the task so all UI is recreated under it. */
    fun setLanguage(activity: Activity, tag: String?) {
        val app = activity.applicationContext
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_APP_LANGUAGE, tag).apply()

        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val manager = app.getSystemService(android.app.LocaleManager::class.java)
                manager?.applicationLocales =
                    if (tag.isNullOrBlank()) LocaleList.getEmptyLocaleList()
                    else LocaleList(localeFor(tag))
            }
        }
        // Pre-33 (and as a belt-and-braces refresh on 33+): push the configuration into the
        // long-lived application resources so @ApplicationContext getString() calls agree with
        // the wrapped activity contexts. Deprecated on 25+ but still honored on all supported APIs.
        if (!tag.isNullOrBlank()) {
            val locale = localeFor(tag)
            Locale.setDefault(locale)
            @Suppress("DEPRECATION")
            val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            app.resources.updateConfiguration(config, app.resources.displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            app.resources.updateConfiguration(
                Configuration(app.resources.configuration).apply {
                    setLocale(Locale.getDefault())
                },
                app.resources.displayMetrics
            )
        }

        // Fresh task: new ViewModels, new activity contexts, no stale strings.
        val intent = activity.packageManager
            .getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) {
            activity.startActivity(intent)
            activity.finish()
        } else {
            activity.recreate()
        }
    }
}
