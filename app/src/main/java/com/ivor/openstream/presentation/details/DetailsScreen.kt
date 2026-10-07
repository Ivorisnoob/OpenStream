package com.ivor.openstream.presentation.details

import com.ivor.openstream.presentation.components.bottomContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivor.openstream.presentation.components.SkeletonBox
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import com.ivor.openstream.data.local.entity.TitleRatingEntity
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.navigationBarsPadding
import com.ivor.openstream.presentation.lists.AddToListSheet
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.CastDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.data.remote.model.SeasonDetailsDto
import com.ivor.openstream.data.remote.model.VideoDto
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailsScreen(
    mediaType: String,
    onBackClick: () -> Unit,
    onPlayClick: (season: Int, episode: Int) -> Unit,
    onOpenDownloads: () -> Unit = {},
    onOpenTitle: (id: Int, mediaType: String) -> Unit = { _, _ -> },
    onOpenPerson: (personId: Int) -> Unit = {},
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isWatchLater.collectAsState()
    val episodeDownloads by viewModel.episodeDownloads.collectAsState()
    val episodeProgress by viewModel.episodeProgress.collectAsState()
    val resumeTarget by viewModel.resumeTarget.collectAsState()
    val titleWatched by viewModel.titleWatched.collectAsState()
    val titleWatchedBusy by viewModel.titleWatchedBusy.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val memberOf by viewModel.memberOf.collectAsState()
    val remindable by viewModel.remindable.collectAsState()
    val reminderSet by viewModel.reminderSet.collectAsState()
    val rating by viewModel.rating.collectAsState()
    var showListSheet by remember { mutableStateOf(false) }
    var confirmUnwatched by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val pullToClose = rememberPullToClose(onClose = onBackClick)
    val context = LocalContext.current
    val share: () -> Unit = {
        (uiState as? DetailsUiState.Success)?.details?.let { details ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "${details.name} — https://www.themoviedb.org/$mediaType/${details.id}")
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_title, details.name)))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(pullToClose.connection)
            .graphicsLayer {
                // The page follows a pull past the top, shrinking a little, before it closes.
                translationY = pullToClose.distance
                val scale = 1f - (pullToClose.distance / 2400f).coerceIn(0f, 0.08f)
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape((pullToClose.distance / 6f).coerceAtMost(32f).dp)
                clip = pullToClose.distance > 0f
            }
    ) {
        when (val state = uiState) {
            DetailsUiState.Loading -> DetailsSkeleton()

            is DetailsUiState.Error -> DetailsError(state.message, onRetry = viewModel::loadDetails)

            is DetailsUiState.Success -> {
                val details = state.details
                val isMovie = mediaType == "movie"
                val activeDownloads = episodeDownloads.values.count { DownloadStatus.isActive(it.status) }
                val trailers = details.videos?.results.orEmpty()
                    .filter { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }

                val actions: @Composable (Modifier) -> Unit = { actionsModifier ->
                    PrimaryActions(
                        details = details,
                        isMovie = isMovie,
                        resume = resumeTarget,
                        isSaved = isSaved,
                        activeDownloads = activeDownloads,
                        movieDownload = episodeDownloads[1 to 1].takeIf { isMovie },
                        hasTrailer = trailers.isNotEmpty(),
                        onPlay = {
                            val resume = resumeTarget
                            if (resume != null) {
                                onPlayClick(resume.season, resume.episode)
                            } else {
                                onPlayClick(details.firstPlayableSeason(), 1)
                            }
                        },
                        isWatched = titleWatched,
                        watchedBusy = titleWatchedBusy,
                        onToggleWatched = {
                            if (titleWatched) confirmUnwatched = true else viewModel.setTitleWatched(true)
                        },
                        inListCount = memberOf.size,
                        onSave = { showListSheet = true },
                        onShare = share,
                        showRemind = remindable || reminderSet,
                        reminderSet = reminderSet,
                        onToggleReminder = viewModel::toggleReminder,
                        rating = rating,
                        onRate = viewModel::rate,
                        onDownload = {
                            if (activeDownloads > 0) {
                                onOpenDownloads()
                            } else if (isMovie) {
                                viewModel.downloadEpisodes(listOf(details.asMovieEpisode()))
                            } else {
                                state.selectedSeasonDetails?.episodes?.filter { it.isReleased() }
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let(viewModel::downloadEpisodes)
                            }
                        },
                        trailer = trailers.firstOrNull(),
                        modifier = actionsModifier
                    )
                }
                val nextAiringItem: LazyListScope.() -> Unit = {
                    details.nextEpisodeToAir?.let { next ->
                        if (!isMovie && next.airDate != null) {
                            item(key = "next-airing") {
                                NextAiringBanner(
                                    text = stringResource(
                                        R.string.next_airing_arrives,
                                        "S${next.seasonNumber} E${next.episodeNumber}",
                                        formatDate(next.airDate, "EEE, MMM d")
                                    )
                                )
                            }
                        }
                    }
                }

                // Phones: artwork on top, then everything in one column.
                val summaryItems: LazyListScope.() -> Unit = {
                    item(key = "hero") {
                        Hero(
                            details = details,
                            isMovie = isMovie,
                            // Parallax: the artwork drifts at half the scroll speed.
                            parallax = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset / 2f else 0f }
                        )
                    }

                    item(key = "actions") { actions(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) }

                    nextAiringItem()

                    if (details.overview.isNotBlank()) {
                        item(key = "overview") { Overview(details.overview) }
                    }
                }
                // Swiping sideways across the episode list moves to the next or previous season.
                val orderedSeasons = details.orderedSeasons().map { it.seasonNumber }
                val selectedSeason = state.selectedSeasonDetails?.seasonNumber
                val seasonSwipe = Modifier.seasonSwipe(
                    onPrevious = {
                        val index = orderedSeasons.indexOf(selectedSeason)
                        if (index > 0) viewModel.loadSeason(orderedSeasons[index - 1])
                    },
                    onNext = {
                        val index = orderedSeasons.indexOf(selectedSeason)
                        if (index in 0 until orderedSeasons.lastIndex) viewModel.loadSeason(orderedSeasons[index + 1])
                    }
                )
                val episodeCard: @Composable (EpisodeDto) -> Unit = { episode ->
                    EpisodeCard(
                        episode = episode,
                        progress = episodeProgress[episode.seasonNumber to episode.episodeNumber],
                        download = episodeDownloads[episode.seasonNumber to episode.episodeNumber],
                        onPlay = { onPlayClick(episode.seasonNumber, episode.episodeNumber) },
                        onDownload = { viewModel.downloadEpisodes(listOf(episode)) },
                        onSetWatched = { watched -> viewModel.setWatched(episode, watched) }
                    )
                }
                val episodeItems: LazyListScope.(columns: Int) -> Unit = { columns ->
                    if (!isMovie && !details.seasons.isNullOrEmpty()) {
                        item(key = "episodes-header") {
                            SectionTitle(stringResource(R.string.details_episodes))
                            SeasonPicker(
                                details = details,
                                selected = state.selectedSeasonDetails?.seasonNumber,
                                onSelect = viewModel::loadSeason
                            )
                        }
                        if (state.isLoadingEpisodes) {
                            item(key = "episodes-loading") {
                                Box(Modifier.fillMaxWidth().height(160.dp).then(seasonSwipe), contentAlignment = Alignment.Center) {
                                    LoadingIndicator()
                                }
                            }
                        } else {
                            val episodes = state.selectedSeasonDetails?.episodes.orEmpty()
                            val released = episodes.filter { it.isReleased() }
                            if (released.isNotEmpty()) {
                                item(key = "season-watched") {
                                    val allWatched = released.all {
                                        episodeProgress[it.seasonNumber to it.episodeNumber]?.completed == true
                                    }
                                    SeasonWatchedAction(
                                        allWatched = allWatched,
                                        onClick = { viewModel.setSeasonWatched(released, !allWatched) }
                                    )
                                }
                            }
                            if (columns <= 1) {
                                items(episodes, key = { "episode:${it.id}" }) { episode ->
                                    Box(modifier = seasonSwipe) { episodeCard(episode) }
                                }
                            } else {
                                // Wide screens: episode cards side by side instead of stretched rows.
                                items(episodes.chunked(columns), key = { "episodes:${it.first().id}" }) { row ->
                                    Row(modifier = Modifier.padding(horizontal = 8.dp).then(seasonSwipe)) {
                                        row.forEach { episode ->
                                            Box(modifier = Modifier.weight(1f)) { episodeCard(episode) }
                                        }
                                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                val extraItems: LazyListScope.() -> Unit = {
                    if (details.cast.isNotEmpty()) {
                        item(key = "cast") {
                            SectionTitle(stringResource(R.string.cast_title))
                            CastRail(details.cast.take(20), onOpenPerson)
                        }
                    }

                    if (trailers.isNotEmpty()) {
                        item(key = "trailers") {
                            SectionTitle(stringResource(R.string.trailers_title))
                            TrailerRail(trailers)
                        }
                    }

                    val recommendations = details.recommendations?.results.orEmpty().filter { it.posterPath != null }
                    if (recommendations.isNotEmpty()) {
                        item(key = "more-like-this") {
                            SectionTitle(stringResource(R.string.details_more_like_this))
                            RecommendationRail(
                                items = recommendations,
                                defaultMediaType = mediaType,
                                onOpen = onOpenTitle
                            )
                        }
                    }
                }
                val infoItems: LazyListScope.() -> Unit = {
                    item(key = "info") {
                        SectionTitle(stringResource(R.string.st_details))
                        // A readable row length on tablets instead of label and value a screen apart.
                        Box(Modifier.widthIn(max = 720.dp)) { InfoList(details, isMovie) }
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Tablets and landscape: a full-width backdrop hero with the title and actions
                    // over it, then episodes in columns and full-width rails. Phones keep the
                    // poster-first single column.
                    val wide = maxWidth >= WIDE_MIN_WIDTH
                    val episodeColumns = if (maxWidth >= TWO_COLUMN_EPISODES_MIN_WIDTH) 2 else 1
                    val heroMinHeight = (maxHeight * 0.72f).coerceIn(420.dp, 640.dp)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomContentPadding)
                    ) {
                        if (wide) {
                            item(key = "hero") {
                                WideHero(
                                    details = details,
                                    isMovie = isMovie,
                                    minHeight = heroMinHeight,
                                    parallax = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset / 2f else 0f },
                                    actions = { actions(Modifier.padding(top = 20.dp)) }
                                )
                            }
                            nextAiringItem()
                        } else {
                            summaryItems()
                        }
                        episodeItems(if (wide) episodeColumns else 1)
                        extraItems()
                        infoItems()
                    }
                }
            }
        }

        TopBar(
            title = (uiState as? DetailsUiState.Success)?.details?.name.orEmpty(),
            collapsed = collapsed,
            shareUrl = (uiState as? DetailsUiState.Success)?.details?.let { "https://www.themoviedb.org/$mediaType/${it.id}" },
            onBackClick = onBackClick
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }

    val loadedTitle = (uiState as? DetailsUiState.Success)?.details?.name
    if (showListSheet && loadedTitle != null) {
        AddToListSheet(
            titleName = loadedTitle,
            lists = lists,
            memberOf = memberOf,
            isInWatchLater = isSaved,
            onToggleWatchLater = viewModel::toggleWatchLater,
            onToggleList = viewModel::setInList,
            onCreateList = viewModel::createListWithTitle,
            onDismiss = { showListSheet = false }
        )
    }
    if (confirmUnwatched && loadedTitle != null) {
        AlertDialog(
            onDismissRequest = { confirmUnwatched = false },
            title = { Text(stringResource(R.string.details_mark_unwatched_confirm, loadedTitle)) },
            text = { Text(stringResource(R.string.pf_clear_profile_history)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnwatched = false
                    viewModel.setTitleWatched(false)
                }) { Text(stringResource(R.string.action_mark_unwatched)) }
            },
            dismissButton = { TextButton(onClick = { confirmUnwatched = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

// region Top bar & hero

@Composable
private fun TopBar(
    title: String,
    collapsed: Boolean,
    shareUrl: String?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "topBarAlpha")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f * barAlpha))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExpressiveBackButton(onClick = onBackClick)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .graphicsLayer { alpha = barAlpha }
            )
            if (shareUrl != null) {
                IconButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "$title — $shareUrl")
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_title, title)))
                    }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.cd_share),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(
    details: AnimeDetailsDto,
    isMovie: Boolean,
    parallax: () -> Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            // The parallax shifts the artwork down; without clipping its bottom edge shows below the hero.
            .clipToBounds()
    ) {
        AsyncImage(
            model = "https://image.tmdb.org/t/p/w1280${details.backdropPath ?: details.posterPath}",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = parallax() }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.62f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                shape = ExpressiveShapes.large,
                shadowElevation = 12.dp,
                modifier = Modifier.size(width = 112.dp, height = 166.dp)
            ) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w342${details.posterPath}",
                    contentDescription = stringResource(R.string.cd_poster, details.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                StatusPill(details.status)
                Text(
                    text = details.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() }
                )
                details.nativeTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MetaLine(details, isMovie, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    val genres = details.genres.orEmpty().filter { it.name != "Animation" }
    if (genres.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        ) {
            genres.take(4).forEach { genre ->
                Surface(
                    shape = ExpressiveShapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = genre.name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * The hero on tablets: landscape artwork across the whole width, with poster, title, overview and
 * actions on its left side. Its height follows the content, never less than [minHeight].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WideHero(
    details: AnimeDetailsDto,
    isMovie: Boolean,
    minHeight: androidx.compose.ui.unit.Dp,
    parallax: () -> Float,
    actions: @Composable () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clipToBounds()
    ) {
        AsyncImage(
            model = "https://image.tmdb.org/t/p/w1280${details.backdropPath ?: details.posterPath}",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationY = parallax() }
        )
        // Scrims: the left side carries the text, the bottom melts into the page.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = 0.94f),
                        0.38f to background.copy(alpha = 0.72f),
                        0.72f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to background
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, top = 96.dp)
                .widthIn(max = 560.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Surface(
                    shape = ExpressiveShapes.large,
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(width = 120.dp, height = 178.dp)
                ) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w342${details.posterPath}",
                        contentDescription = stringResource(R.string.cd_poster, details.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp)
                ) {
                    StatusPill(details.status)
                    Text(
                        text = details.name,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { heading() }
                    )
                    details.nativeTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    MetaLine(details, isMovie, modifier = Modifier.padding(top = 8.dp))
                }
            }
            val genres = details.genres.orEmpty().filter { it.name != "Animation" }
            if (genres.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    genres.take(4).forEach { genre ->
                        Surface(
                            shape = ExpressiveShapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = genre.name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            if (details.overview.isNotBlank()) {
                Overview(details.overview, modifier = Modifier.padding(top = 16.dp), collapsedLines = 3)
            }
            actions()
        }
    }
}

@Composable
private fun StatusPill(status: String?) {
    val labelRes = when (status) {
        "Returning Series", "In Production" -> R.string.status_airing
        "Ended" -> R.string.status_completed
        "Canceled" -> R.string.status_cancelled
        "Released", null -> return
        else -> null
    }
    val label = labelRes?.let { stringResource(it) } ?: status.orEmpty()
    Surface(
        shape = ExpressiveShapes.small,
        color = if (labelRes == R.string.status_airing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (labelRes == R.string.status_airing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MetaLine(details: AnimeDetailsDto, isMovie: Boolean, modifier: Modifier = Modifier) {
    val runtimeText = details.runtime?.takeIf { it > 0 }?.let { formatRuntime(it) }
    val seasonsText = details.numberOfSeasons?.let {
        if (it == 1) stringResource(R.string.season_count_one) else stringResource(R.string.season_count_other, it)
    }
    val episodesText = details.numberOfEpisodes?.let { stringResource(R.string.eps_count, it) }
    val parts = buildList {
        details.date.take(4).takeIf { it.length == 4 }?.let(::add)
        if (isMovie) {
            runtimeText?.let(::add)
        } else {
            seasonsText?.let(::add)
            episodesText?.let(::add)
        }
    }
    val rating = details.voteAverage.takeIf { it > 0 }
    val ratedDescription = rating?.let { stringResource(R.string.cd_rated_format, String.format(Locale.US, "%.1f", it)) }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(
                ratedDescription,
                parts.joinToString(", ")
            ).joinToString(". ")
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rating != null) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
            Text(
                text = String.format(Locale.US, " %.1f", rating) +
                    (details.voteCount?.takeIf { it > 0 }?.let { " (${compactCount(it)})" } ?: "") +
                    if (parts.isNotEmpty()) "  ·  " else "",
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

// endregion

// region Actions

@Composable
private fun PrimaryActions(
    details: AnimeDetailsDto,
    isMovie: Boolean,
    resume: WatchProgress?,
    isSaved: Boolean,
    activeDownloads: Int,
    movieDownload: DownloadEntity?,
    hasTrailer: Boolean,
    trailer: VideoDto?,
    onPlay: () -> Unit,
    isWatched: Boolean,
    watchedBusy: Boolean,
    onToggleWatched: () -> Unit,
    inListCount: Int,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    showRemind: Boolean,
    reminderSet: Boolean,
    onToggleReminder: () -> Unit,
    rating: Int?,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        Button(
            onClick = onPlay,
            shape = ExpressiveShapes.large,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = when {
                        resume == null -> stringResource(R.string.details_play)
                        isMovie -> stringResource(R.string.cd_resume)
                        resume.isUpNext -> stringResource(R.string.continue_with_code, "S${resume.season} E${resume.episode}")
                        else -> stringResource(R.string.resume_code, "S${resume.season} E${resume.episode}")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (resume != null && resume.fraction > 0f) {
                    val minutesLeft = ((resume.durationMs - resume.positionMs) / 60_000L).coerceAtLeast(1)
                    Text(stringResource(R.string.details_minutes_left, minutesLeft), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (resume != null && resume.fraction > 0f) {
            LinearProgressIndicator(
                progress = { resume.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Save opens the list picker (Watch later, the user's lists, a new list), so custom
            // lists are reachable from the same button as Watch later.
            val saved = isSaved || inListCount > 0
            ActionTile(
                icon = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (saved) stringResource(R.string.cd_saved) else stringResource(R.string.action_save),
                highlighted = saved,
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
            val downloadLabel = when {
                activeDownloads > 0 -> stringResource(R.string.downloading_count, activeDownloads)
                isMovie && movieDownload?.status == DownloadStatus.COMPLETED -> stringResource(R.string.cd_downloaded)
                isMovie -> stringResource(R.string.action_download)
                else -> stringResource(R.string.action_download_season)
            }
            ActionTile(
                icon = if (isMovie && movieDownload?.status == DownloadStatus.COMPLETED) Icons.Default.DownloadDone else Icons.Default.Download,
                label = downloadLabel,
                busy = activeDownloads > 0,
                onClick = onDownload,
                modifier = Modifier.weight(1f)
            )
            if (hasTrailer && trailer != null) {
                ActionTile(
                    icon = Icons.Default.Theaters,
                    label = stringResource(R.string.details_trailer),
                    onClick = { uriHandler.openUri("https://www.youtube.com/watch?v=${trailer.key}") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionTile(
                icon = if (isWatched) Icons.Default.DoneAll else Icons.Default.Done,
                label = when {
                    watchedBusy -> stringResource(R.string.marking_label)
                    isWatched -> stringResource(R.string.details_watched)
                    isMovie -> stringResource(R.string.action_mark_watched)
                    else -> stringResource(R.string.action_mark_all_watched)
                },
                highlighted = isWatched,
                busy = watchedBusy,
                onClick = onToggleWatched,
                modifier = Modifier.weight(1f)
            )
            ActionTile(
                icon = Icons.Default.Share,
                label = stringResource(R.string.action_share),
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showRemind) {
                ActionTile(
                    icon = if (reminderSet) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                    label = stringResource(if (reminderSet) R.string.remind_on else R.string.remind_me),
                    highlighted = reminderSet,
                    onClick = onToggleReminder,
                    modifier = Modifier.weight(1f)
                )
            }
            ActionTile(
                icon = Icons.Default.ThumbUp,
                label = stringResource(R.string.cd_like),
                highlighted = rating == TitleRatingEntity.LIKE,
                onClick = { onRate(TitleRatingEntity.LIKE) },
                modifier = Modifier.weight(1f)
            )
            ActionTile(
                icon = Icons.Default.ThumbDown,
                label = stringResource(R.string.cd_dislike),
                highlighted = rating == TitleRatingEntity.DISLIKE,
                onClick = { onRate(TitleRatingEntity.DISLIKE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    busy: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = ExpressiveShapes.medium,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 72.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (busy) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(icon, contentDescription = null)
            }
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
private fun NextAiringBanner(text: String) {
    Surface(
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Event, contentDescription = null)
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(stringResource(R.string.action_next_episode), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Overview(
    text: String,
    modifier: Modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
    collapsedLines: Int = 4
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.animateContentSize()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis
        )
        if (text.length > 220) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(if (expanded) stringResource(R.string.action_show_less) else stringResource(R.string.more_short))
            }
        }
    }
}

// endregion

// region Episodes

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 14.dp)
            .semantics { heading() }
    )
}

@Composable
private fun SeasonWatchedAction(allWatched: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = onClick) {
            Icon(
                if (allWatched) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (allWatched) stringResource(R.string.action_mark_season_unwatched) else stringResource(R.string.action_mark_season_watched))
        }
    }
}

/** Seasons with episodes, specials last: the order the picker and the season swipe share. */
private fun AnimeDetailsDto.orderedSeasons() = seasons.orEmpty()
    .filter { it.episodeCount > 0 }
    .sortedBy { if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber }

/** A decided horizontal swipe (a fifth of the width) calls [onPrevious] (right) or [onNext] (left). */
@Composable
private fun Modifier.seasonSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier {
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)
    val haptics = LocalHapticFeedback.current
    return pointerInput(Unit) {
        var travel = 0f
        detectHorizontalDragGestures(
            onDragStart = { travel = 0f },
            onDragEnd = {
                val threshold = size.width / 5f
                when {
                    travel < -threshold -> { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); latestNext() }
                    travel > threshold -> { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); latestPrevious() }
                }
            }
        ) { change, amount ->
            change.consume()
            travel += amount
        }
    }
}

/** Pulling the page down past its top closes it, the way a sheet would. */
private class PullToClose(
    private val thresholdPx: Float,
    private val onClose: () -> Unit
) {
    var distance by mutableFloatStateOf(0f)
        private set
    private var closing = false

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Scrolling back up first takes back the pull.
            if (available.y < 0f && distance > 0f) {
                val used = maxOf(available.y, -distance)
                distance += used
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Whatever the list couldn't scroll (it's at the top) becomes the pull, with resistance.
            if (source == NestedScrollSource.UserInput && available.y > 0f && !closing) {
                distance += available.y * 0.5f
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (distance <= 0f) return Velocity.Zero
            if (distance > thresholdPx || (available.y > 2_000f && distance > thresholdPx / 3f)) {
                closing = true
                onClose()
            } else {
                animate(distance, 0f) { value, _ -> distance = value }
            }
            return available
        }
    }
}

@Composable
private fun rememberPullToClose(onClose: () -> Unit): PullToClose {
    val latestClose by rememberUpdatedState(onClose)
    val thresholdPx = with(LocalDensity.current) { 140.dp.toPx() }
    return remember(thresholdPx) { PullToClose(thresholdPx) { latestClose() } }
}

@Composable
private fun SeasonPicker(
    details: AnimeDetailsDto,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    val seasons = details.orderedSeasons()
    val rowState = rememberLazyListState()
    // Keep the picked season in view, e.g. after swiping to it from the episode list.
    LaunchedEffect(selected) {
        val index = seasons.indexOfFirst { it.seasonNumber == selected }
        if (index >= 0) rowState.animateScrollToItem(index)
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            FilterChip(
                selected = season.seasonNumber == selected,
                onClick = { onSelect(season.seasonNumber) },
                label = { Text("${season.name} · ${season.episodeCount}") },
                shape = ExpressiveShapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(
    episode: EpisodeDto,
    progress: WatchProgress?,
    download: DownloadEntity?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onSetWatched: (Boolean) -> Unit
) {
    val released = episode.isReleased()
    val watched = progress?.completed == true
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val markWatchedLabel = stringResource(if (watched) R.string.action_mark_unwatched else R.string.action_mark_watched)
    val downloadActionLabel = stringResource(R.string.action_download)
    val episodeNumberText = stringResource(R.string.misc_episode_number, episode.episodeNumber)
    val meta = listOfNotNull(
        episode.runtime?.takeIf { it > 0 }?.let { formatRuntime(it) },
        episode.airDate?.let { formatDate(it, "MMM d, yyyy") }
    ).joinToString("  ·  ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(ExpressiveShapes.large)
            .combinedClickable(
                enabled = released,
                onClick = onPlay,
                onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
                onClickLabel = stringResource(R.string.play_episode, episode.episodeNumber),
                onLongClickLabel = stringResource(R.string.misc_more_options)
            )
            .semantics(mergeDescendants = true) {
                customActions = listOf(
                    CustomAccessibilityAction(markWatchedLabel) {
                        onSetWatched(!watched); true
                    },
                    CustomAccessibilityAction(downloadActionLabel) { onDownload(); true }
                )
            }
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.graphicsLayer { alpha = if (released) 1f else 0.55f }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(148.dp)
                        .aspectRatio(16f / 9f)
                        .clip(ExpressiveShapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    AsyncImage(
                        model = episode.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    EpisodeThumbnailOverlay(episode.episodeNumber, progress)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = episode.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (released) meta.ifEmpty { episodeNumberText } else episode.airDate?.let { stringResource(R.string.coming_date, formatDate(it, "MMM d")) } ?: stringResource(R.string.coming_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (released) EpisodeDownloadAction(download, onDownload)
            }
            if (episode.overview.isNotBlank()) {
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(markWatchedLabel) },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onSetWatched(!watched)
                }
            )
            DropdownMenuItem(
                text = { Text(downloadActionLabel) },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                enabled = released,
                onClick = {
                    menuOpen = false
                    onDownload()
                }
            )
        }
    }
}

@Composable
private fun BoxScope.EpisodeThumbnailOverlay(number: Int, progress: WatchProgress?) {
    Surface(
        shape = ExpressiveShapes.extraSmall,
        color = Color.Black.copy(alpha = 0.65f),
        contentColor = Color.White,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
    ) {
        Text(
            text = "E$number",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
    when {
        progress?.completed == true -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.cd_watched),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        progress != null && progress.fraction > 0f -> LinearProgressIndicator(
            progress = { progress.fraction },
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.45f),
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}

/** Download state for one episode: start, progress, or done. Failed downloads can be retried here. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EpisodeDownloadAction(download: DownloadEntity?, onDownload: () -> Unit) {
    val downloadingDescription = stringResource(R.string.cd_downloading_percent, download?.progress ?: 0)
    when (download?.status) {
        DownloadStatus.COMPLETED -> Icon(
            Icons.Default.DownloadDone,
            contentDescription = stringResource(R.string.cd_downloaded),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp)
        )
        DownloadStatus.RESOLVING, DownloadStatus.QUEUED -> LoadingIndicator(
            modifier = Modifier
                .padding(8.dp)
                .size(32.dp)
        )
        DownloadStatus.RUNNING -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(8.dp)
                .size(32.dp)
                .semantics { contentDescription = downloadingDescription }
        ) {
            CircularProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp
            )
        }
        else -> IconButton(onClick = onDownload) {
            Icon(
                if (download?.status == DownloadStatus.FAILED) Icons.Default.Refresh else Icons.Default.Download,
                contentDescription = if (download?.status == DownloadStatus.FAILED) stringResource(R.string.action_retry_download) else stringResource(R.string.action_download)
            )
        }
    }
}

// endregion

// region Rails & info

@Composable
private fun CastRail(cast: List<CastDto>, onOpenPerson: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(cast, key = { it.id }) { person ->
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .clip(ExpressiveShapes.medium)
                    .clickable(onClickLabel = stringResource(R.string.misc_open_title, person.name)) { onOpenPerson(person.id) }
                    .semantics(mergeDescendants = true) {}
                    // The role line used to sit on the clipped bottom corners.
                    .padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = person.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                person.role?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailerRail(trailers: List<VideoDto>) {
    val uriHandler = LocalUriHandler.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(trailers, key = { it.id }) { video ->
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .clip(ExpressiveShapes.medium)
                    .clickable(onClickLabel = stringResource(R.string.play_on_youtube, video.name)) {
                        uriHandler.openUri("https://www.youtube.com/watch?v=${video.key}")
                    }
                    .padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(ExpressiveShapes.medium)
                ) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/${video.key}/hqdefault.jpg",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RecommendationRail(
    items: List<AnimeDto>,
    defaultMediaType: String,
    onOpen: (Int, String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { anime ->
            Column(
                modifier = Modifier
                    .width(124.dp)
                    .clip(ExpressiveShapes.medium)
                    .clickable(onClickLabel = stringResource(R.string.misc_open_title, anime.name)) {
                        onOpen(anime.id, anime.mediaType?.takeIf { it == "movie" || it == "tv" } ?: defaultMediaType)
                    }
                    .padding(bottom = 10.dp)
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InfoList(details: AnimeDetailsDto, isMovie: Boolean) {
    val uriHandler = LocalUriHandler.current
    val statusLabel = stringResource(R.string.st_status)
    val firstAiredLabel = stringResource(R.string.details_first_aired)
    val originalTitleLabel = stringResource(R.string.details_original_title)
    val languageLabel = stringResource(R.string.details_language)
    val rows = buildList {
        details.status?.let { add(statusLabel to it) }
        details.date.takeIf { it.isNotBlank() }?.let {
            add((if (isMovie) stringResource(R.string.info_released) else firstAiredLabel) to formatDate(it, "MMMM d, yyyy"))
        }
        details.nativeTitle?.let { add(originalTitleLabel to it) }
        details.originalLanguage?.let { code ->
            val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).ifBlank { code }
            add(languageLabel to name)
        }
        details.networks?.takeIf { it.isNotEmpty() }?.let { add(stringResource(R.string.info_network) to it.joinToString { n -> n.name }) }
        details.productionCompanies?.takeIf { it.isNotEmpty() }?.let {
            add(stringResource(R.string.info_studios) to it.take(3).joinToString { c -> c.name })
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        rows.forEachIndexed { index, (label, value) ->
            // Read-only rows: the non-clickable item. A disabled clickable one renders its text greyed out.
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size + if (details.homepage.isNullOrBlank()) 0 else 1),
                colors = ListItemDefaults.segmentedColors(),
                supportingContent = { Text(value) }
            ) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
        details.homepage?.takeIf { it.isNotBlank() }?.let { homepage ->
            SegmentedListItem(
                onClick = { uriHandler.openUri(homepage) },
                shapes = ListItemDefaults.segmentedShapes(index = rows.size, count = rows.size + 1),
                colors = ListItemDefaults.segmentedColors(),
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
            ) {
                Text(stringResource(R.string.details_official_website), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailsError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.home_could_not_load_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = onRetry, shape = ExpressiveShapes.medium, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.action_try_again))
        }
    }
}

// endregion

// region Helpers

private fun AnimeDetailsDto.firstPlayableSeason(): Int =
    seasons?.filter { it.seasonNumber > 0 && it.episodeCount > 0 }?.minByOrNull { it.seasonNumber }?.seasonNumber
        ?: seasons?.firstOrNull()?.seasonNumber
        ?: 1

/** Movies download through the same path as episodes, as season 1 episode 1. */
private fun AnimeDetailsDto.asMovieEpisode() = EpisodeDto(
    id = id,
    name = name,
    overview = overview,
    voteAverage = voteAverage,
    voteCount = voteCount ?: 0,
    airDate = date,
    episodeNumber = 1,
    seasonNumber = 1,
    stillPath = backdropPath,
    productionCode = "",
    runtime = runtime,
    showId = id
)

private fun EpisodeDto.isReleased(): Boolean {
    val date = airDate?.takeIf { it.isNotBlank() } ?: return true
    return runCatching { !LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(true)
}

private fun formatDate(isoDate: String, pattern: String): String =
    runCatching { LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())) }
        .getOrDefault(isoDate)

@Composable
private fun formatRuntime(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.misc_duration_hm, minutes / 60, minutes % 60)
    else stringResource(R.string.misc_duration_m, minutes)

private fun compactCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000f)
    else -> count.toString()
}

// endregion

/** Mirrors the title page: artwork, poster and title, play button, action tiles, overview. */
@Composable
private fun DetailsSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(540.dp)) {
            SkeletonBox(modifier = Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 112.dp, height = 166.dp)
                        .clip(ExpressiveShapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(width = 190.dp, height = 30.dp).clip(ExpressiveShapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    Box(Modifier.size(width = 130.dp, height = 16.dp).clip(ExpressiveShapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
            }
        }
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(60.dp), shape = ExpressiveShapes.large)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { SkeletonBox(modifier = Modifier.weight(1f).height(72.dp)) }
            }
            repeat(3) { index ->
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(if (index == 2) 0.6f else 1f).height(16.dp),
                    shape = ExpressiveShapes.small
                )
            }
        }
    }
}

/** From here Details uses the backdrop hero; below it, the phone layout. */
private val WIDE_MIN_WIDTH = 600.dp

/** Episode cards go two to a row from here. */
private val TWO_COLUMN_EPISODES_MIN_WIDTH = 900.dp
