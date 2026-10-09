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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        playbackSession.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from recents with nothing queued: don't leave a dead notification behind.
        val player = playbackSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        playbackSession.notificationServiceStopped()
        super.onDestroy()
    }
}
