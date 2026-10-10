package com.ivor.openstream.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.ReminderEntity
import com.ivor.openstream.data.repository.ReminderRepository
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily new-episode check: every followed series (Watch Later + anything with
 * progress) is compared against TMDB's `next_episode_to_air`. Each newly aired
 * episode notifies once; tapping opens the title. Runs from [EpisodeAlarm].
 */
@Singleton
class EpisodeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val animeRepository: AnimeRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val appSettingsStore: AppSettingsStore,
    private val reminderRepository: ReminderRepository,
    private val prefs: SharedPreferences
) {
    companion object {
        const val CHANNEL_ID = "new_episodes"
        private const val KEY_NOTIFIED_PREFIX = "episode_notified_tv_"
        private const val MAX_SHOWS_PER_RUN = 40
        private const val MAX_NOTIFICATIONS_PER_RUN = 8
    }

    data class NewEpisode(
        val tmdbId: Int,
        val showTitle: String,
        val season: Int,
        val episode: Int
    )

    /** Checks followed shows; returns how many notifications were posted. */
    suspend fun checkOnce(): Int {
        if (appSettingsStore.current.episodeNotifications.not()) return 0
        if (!canPost()) return 0
        val followed = followedSeries()
        val today = runCatching { LocalDate.now().toString() }.getOrDefault("9999-12-31")
        var posted = 0
        for ((tmdbId, title) in followed) {
            // The receiver runs this under a time limit; stop here, not inside a runCatching.
            currentCoroutineContext().ensureActive()
            if (posted >= MAX_NOTIFICATIONS_PER_RUN) break
            val episode = runCatching {
                animeRepository.getMediaDetails(tmdbId, "tv").getOrNull()?.nextEpisodeToAir
            }.getOrNull() ?: continue
            val airDate = episode.airDate ?: continue
            // Lexicographic compare works on ISO-8601 dates.
            if (airDate > today) continue
            val marker = "S${episode.seasonNumber}E${episode.episodeNumber}"
            if (prefs.getString(KEY_NOTIFIED_PREFIX + tmdbId, null) == marker) continue
            postNotification(tmdbId, title, episode.seasonNumber, episode.episodeNumber, posted)
            prefs.edit().putString(KEY_NOTIFIED_PREFIX + tmdbId, marker).apply()
            posted++
        }
        posted += runCatching { checkReminders() }.getOrDefault(0)
        return posted
    }

    /**
     * Fires due "Remind me" requests: a movie whose release date has passed, a show whose
     * first air date has passed, or a specific season/episode that has aired. Each fires
     * once and is then removed. Returns how many notifications were posted.
     */
    suspend fun checkReminders(): Int {
        val reminders = runCatching { reminderRepository.all() }.getOrDefault(emptyList())
        if (reminders.isEmpty()) return 0
        var fired = 0
        for (reminder in reminders) {
            currentCoroutineContext().ensureActive()
            try {
                val details = animeRepository.getMediaDetails(reminder.tmdbId, reminder.mediaType)
                    .getOrNull() ?: continue
                val season = reminder.season
                val episode = reminder.episode
                val isOut = when {
                    reminder.mediaType == "movie" -> isReleased(details.date)
                    season != null && episode != null ->
                        seasonEpisodeAired(reminder.tmdbId, season, episode)
                    else -> isReleased(details.firstAirDate)
                }
                if (!isOut) continue
                postReminderNotification(reminder, season, episode)
                runCatching {
                    reminderRepository.remove(reminder.profileId, reminder.mediaType, reminder.tmdbId)
                }
                fired++
            } catch (_: Exception) {
                continue
            }
        }
        return fired
    }

    private suspend fun seasonEpisodeAired(tmdbId: Int, season: Int, episode: Int): Boolean {
        val episodes = runCatching {
            animeRepository.getSeasonDetails(tmdbId, season).getOrNull()?.episodes
        }.getOrNull() ?: return false
        val airDate = episodes.firstOrNull { it.episodeNumber == episode }?.airDate
            ?: return false
        return isReleased(airDate)
    }

    private fun isReleased(date: String?): Boolean {
        val parsed = date?.takeIf { it.isNotBlank() } ?: return false
        return runCatching { !LocalDate.parse(parsed.take(10)).isAfter(LocalDate.now()) }
            .getOrDefault(false)
    }

    private fun postReminderNotification(reminder: ReminderEntity, season: Int?, episode: Int?) {
        ensureChannel()
        val section = if (reminder.mediaType == "movie") "movie" else "tv"
        val deepLink = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://www.themoviedb.org/$section/${reminder.tmdbId}")
        ).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val tap = PendingIntent.getActivity(
            context,
            30_000 + reminder.tmdbId,
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (season != null && episode != null) {
            context.getString(R.string.notif_episode_text, reminder.title, season, episode)
        } else {
            reminder.title
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_openstream)
            .setContentTitle(context.getString(R.string.notif_reminder_title))
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(30_000 + reminder.tmdbId, notification)
    }

    /** Series the user follows: saved titles plus anything with watch progress. */
    private suspend fun followedSeries(): List<Pair<Int, String>> {
        val saved = runCatching { watchLaterRepository.getWatchLaterList().first() }
            .getOrDefault(emptyList())
            .filter { it.mediaType != "movie" }
            .map { it.id to it.title }
        val progressed = runCatching { watchProgressRepository.allProgress().first() }
            .getOrDefault(emptyList())
            .filter { !it.isMovie && !it.isUpNext }
            .map { it.tmdbId to it.title }
        return (saved + progressed).distinctBy { it.first }.take(MAX_SHOWS_PER_RUN)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun postNotification(tmdbId: Int, title: String, season: Int, episode: Int, index: Int) {
        ensureChannel()
        // Deep link the app already understands: opens the title's details.
        val deepLink = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://www.themoviedb.org/tv/$tmdbId")
        ).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val tap = PendingIntent.getActivity(
            context,
            10_000 + tmdbId,
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_openstream)
            .setContentTitle(context.getString(R.string.notif_episode_title))
            .setContentText(context.getString(R.string.notif_episode_text, title, season, episode))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(20_000 + tmdbId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_episodes),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_episodes_desc)
            }
        )
    }
}
