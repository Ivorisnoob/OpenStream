package com.ivor.openstream.presentation.player.components

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.player.NextEpisodeTarget
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything under the video in portrait: what is playing, what to do next, and the rest of the
 * season. Playback stays the hero; this panel only answers "where am I and what's next".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerInfoPanel(
    mediaType: String,
    season: Int,
    episode: Int,
    details: AnimeDetailsDto?,
    currentEpisode: EpisodeDto?,
    seasonEpisodes: List<EpisodeDto>,
    isLoadingEpisodes: Boolean,
    episodeProgress: Map<Pair<Int, Int>, WatchProgress>,
    nextEpisode: NextEpisodeTarget?,
    download: DownloadEntity?,
    canDownload: Boolean,
    isSaved: Boolean,
    onPlayEpisode: (season: Int, episode: Int) -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onToggleSaved: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenTitle: (id: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMovie = mediaType == "movie"
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                if (!isMovie && details != null) {
                    Row(
                        modifier = Modifier
                            .clip(ExpressiveShapes.small)
                            .clickable(onClickLabel = "Open ${details.name}", onClick = onOpenDetails)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = details.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = if (isMovie) details?.name.orEmpty() else currentEpisode?.name ?: "Episode $episode",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .semantics { heading() }
                )
                MetaRow(
                    parts = buildList {
                        if (!isMovie) add("S$season · E$episode")
                        val runtime = if (isMovie) details?.runtime else currentEpisode?.runtime ?: details?.typicalRuntime
                        runtime?.takeIf { it > 0 }?.let { add(formatMinutes(it)) }
                        (if (isMovie) details?.date else currentEpisode?.airDate)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { add(formatDay(it)) }
                    },
                    rating = (if (isMovie) details?.voteAverage else currentEpisode?.voteAverage)?.takeIf { it > 0 }
                )
            }
        }

        item(key = "actions") {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                if (nextEpisode != null) {
                    Button(
                        onClick = { onPlayEpisode(nextEpisode.season, nextEpisode.episode) },
                        shape = ExpressiveShapes.large,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Next: " + (if (nextEpisode.season != season) "S${nextEpisode.season} " else "") +
                                "E${nextEpisode.episode}" + (nextEpisode.title?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val (downloadIcon, downloadLabel, busy) = when (download?.status) {
                        DownloadStatus.COMPLETED -> Triple(Icons.Default.DownloadDone, "Downloaded", false)
                        DownloadStatus.RUNNING -> Triple(Icons.Default.Download, "${download.progress}%", true)
                        DownloadStatus.RESOLVING, DownloadStatus.QUEUED -> Triple(Icons.Default.Download, "Queued", true)
                        else -> Triple(Icons.Default.Download, "Download", false)
                    }
                    PanelTile(
                        icon = downloadIcon,
                        label = downloadLabel,
                        busy = busy,
                        highlighted = download?.status == DownloadStatus.COMPLETED,
                        enabled = canDownload || download != null,
                        onClick = if (download?.status == DownloadStatus.COMPLETED) onRemoveDownload else onDownload,
                        clickLabel = if (download?.status == DownloadStatus.COMPLETED) "Delete download" else "Download",
                        modifier = Modifier.weight(1f)
                    )
                    PanelTile(
                        icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        label = if (isSaved) "Saved" else "My list",
                        highlighted = isSaved,
                        onClick = onToggleSaved,
                        modifier = Modifier.weight(1f)
                    )
                    PanelTile(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = {
                            val name = details?.name ?: return@PanelTile
                            val link = "https://www.themoviedb.org/$mediaType/${details.id}"
                            val text = if (isMovie) "$name — $link" else "$name S$season E$episode — $link"
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                    "Share"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PanelTile(
                        icon = Icons.Default.Info,
                        label = "Details",
                        onClick = onOpenDetails,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val overview = if (isMovie) details?.overview else currentEpisode?.overview?.ifBlank { details?.overview }
        if (!overview.isNullOrBlank()) {
            item(key = "overview") { ExpandableText(overview) }
        }

        if (!isMovie) {
            item(key = "episodes-title") {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 28.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Season $season",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                    )
                    TextButton(onClick = onOpenDetails) { Text("All seasons") }
                }
            }
            if (isLoadingEpisodes && seasonEpisodes.isEmpty()) {
                item(key = "episodes-loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
            itemsIndexed(seasonEpisodes, key = { _, ep -> "ep:${ep.id}" }) { index, ep ->
                val isCurrent = ep.episodeNumber == episode
                val progress = episodeProgress[ep.seasonNumber to ep.episodeNumber]
                SegmentedListItem(
                    selected = isCurrent,
                    onClick = { if (!isCurrent) onPlayEpisode(ep.seasonNumber, ep.episodeNumber) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = seasonEpisodes.size),
                    colors = ListItemDefaults.segmentedColors(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                    leadingContent = { EpisodeThumb(ep, progress) },
                    supportingContent = {
                        Text(
                            text = if (isCurrent) "Now playing" else listOfNotNull(
                                ep.runtime?.takeIf { it > 0 }?.let(::formatMinutes),
                                if (progress?.completed == true) "Watched" else null
                            ).joinToString(" · ").ifEmpty { "Episode ${ep.episodeNumber}" },
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingContent = if (isCurrent) {
                        { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    }
                ) {
                    Text(
                        text = "${ep.episodeNumber}. ${ep.name}",
                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            val recommendations = details?.recommendations?.results.orEmpty().filter { it.posterPath != null }
            if (recommendations.isNotEmpty()) {
                item(key = "more-like-this") {
                    Text(
                        text = "More like this",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .padding(start = 20.dp, top = 28.dp, bottom = 12.dp)
                            .semantics { heading() }
                    )
                    RecommendationRow(recommendations, onOpenTitle)
                }
            }
        }
    }
}

@Composable
private fun MetaRow(parts: List<String>, rating: Double?) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        rating?.let {
            Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
            Text(
                text = String.format(Locale.US, " %.1f", it) + if (parts.isNotEmpty()) "  ·  " else "",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PanelTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    clickLabel: String? = null
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ExpressiveShapes.medium,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .heightIn(min = 68.dp)
            // Names the tap action (e.g. "Delete download") without replacing what the tile says.
            .semantics { if (clickLabel != null) onClick(label = clickLabel) { onClick(); true } }
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .alpha(if (enabled) 1f else 0.45f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (busy) LoadingIndicator(modifier = Modifier.size(24.dp)) else Icon(icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpandableText(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .animateContentSize()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
        if (text.length > 160) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Show less" else "More")
            }
        }
    }
}

@Composable
internal fun EpisodeThumb(episode: EpisodeDto, progress: WatchProgress?) {
    Box(
        modifier = Modifier
            .width(112.dp)
            .aspectRatio(16f / 9f)
            .clip(ExpressiveShapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(
            model = episode.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        when {
            progress?.completed == true -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Watched", tint = MaterialTheme.colorScheme.primary)
            }
            progress != null && progress.fraction > 0f -> LinearProgressIndicator(
                progress = { progress.fraction },
                gapSize = 0.dp,
                drawStopIndicator = {},
                trackColor = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}

@Composable
private fun RecommendationRow(items: List<AnimeDto>, onOpen: (Int, String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { anime ->
            Column(
                modifier = Modifier
                    .width(116.dp)
                    .clickable(onClickLabel = "Open ${anime.name}") {
                        onOpen(anime.id, if (anime.mediaType == "tv") "tv" else "movie")
                    }
            ) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w342${anime.posterPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f)
                        .clip(ExpressiveShapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Text(
                    text = anime.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Int) = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

private fun formatDay(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())) }
        .getOrDefault(isoDate)
