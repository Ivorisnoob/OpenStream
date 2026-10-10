package com.ivor.openstream.presentation.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ivor.openstream.presentation.player.session.PlaybackSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the app's one [PlaybackSession] as a foreground service so playback survives leaving the
 * app. The service builds the media notification on its own from that session, and the session is
 * what the lock screen, headset buttons, Android Auto and Wear all talk to.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playbackSession: PlaybackSession

    override fun onCreate() {
        super.onCreate()
        // A plain start intent doesn't make Media3 ask for the session (only media-button intents
        // and binding controllers do), so hand it over here: this is what posts the notification
        // and moves the service to the foreground while playing.
        addSession(playbackSession.mediaSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        playbackSession.mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // The queue lives in memory, so there is nothing to bring back after the process dies;
        // a sticky restart would only build an idle player in the background.
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from recents with nothing queued: don't leave a dead notification behind.
        val player = playbackSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The session outlives this service. Media3 doesn't detach it on destroy, and the stale
        // notification manager would keep reacting to the player on behalf of a dead service.
        sessions.forEach { removeSession(it) }
        playbackSession.notificationServiceStopped()
        super.onDestroy()
    }
}
