package com.ivor.openstream.presentation.tv

import androidx.compose.runtime.Composable

/**
 * The TV player.
 *
 * A thin wrapper rather than a second player: playback, source resolution and the
 * settings panel are shared with the phone build, because duplicating them would put the
 * two players on different source logic. What TV adds is passed through `tvControls`,
 * which is what enables the D-pad control bar, the media notification and the leanback
 * start (see [com.ivor.openstream.presentation.player.PlayerScreen]).
 *
 * The three TV-specific playback problems are fixed at their source rather than here:
 *
 * - **Reachable quality and sources.** The settings panel now takes focus when it opens
 *   (`PlayerPanelHost(autoFocus = dpadControls)`), so D-pad OK on the quality chip actually
 *   moves into the panel instead of leaving focus on a control that is animating away.
 * - **No racing auto-hide.** The chrome stays up for 12 s on TV and any D-pad press
 *   restarts that, so a control cannot vanish between navigation and OK.
 * - **Back works.** On TV the fullscreen Back handler is disabled, so one press leaves
 *   playback rather than exiting a fullscreen state the TV was always in.
 */
@Composable
fun TvPlayerScreen(
    mediaType: String,
    tmdbId: Int,
    season: Int,
    episode: Int,
    onBackClick: () -> Unit,
    onEpisodeClick: (season: Int, episode: Int) -> Unit,
    onOpenDetails: (mediaType: String, id: Int) -> Unit = { _, _ -> }
) {
    com.ivor.openstream.presentation.player.PlayerScreen(
        mediaType = mediaType,
        tmdbId = tmdbId,
        season = season,
        episode = episode,
        onBackClick = onBackClick,
        onEpisodeClick = onEpisodeClick,
        onOpenDetails = onOpenDetails,
        tvControls = true
    )
}