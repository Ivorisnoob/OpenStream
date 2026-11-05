package com.ivor.openstream.presentation.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil3.compose.AsyncImage
import com.ivor.openstream.R

/** Sizes for the 960x540 dp canvas a 1080p TV gives the app. */
object TvLayout {
    /** Where content starts: clear of the collapsed navigation drawer. */
    val ContentStart: Dp = 96.dp
    /** Where content starts on full-screen pages without the drawer (Details): the TV safe margin. */
    val PageStart: Dp = 56.dp
    val ContentEnd: Dp = 48.dp
    val PosterWidth: Dp = 104.dp
    val PosterHeight: Dp = 156.dp
    val LandscapeWidth: Dp = 196.dp
    val LandscapeHeight: Dp = 110.dp
    val CardGap: Dp = 14.dp
    val CardShape = RoundedCornerShape(10.dp)

    /** Room above and below a row so a focused card can grow without being clipped. */
    val RowFocusPadding: Dp = 12.dp
}

const val TV_POSTER_BASE = "https://image.tmdb.org/t/p/w342"
const val TV_STILL_BASE = "https://image.tmdb.org/t/p/w500"
const val TV_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

/** A full-bleed backdrop that crossfades as the shown title changes. */
@Composable
fun TvBackdrop(url: String?, modifier: Modifier = Modifier) {
    Crossfade(targetState = url, animationSpec = tween(400), label = "backdrop", modifier = modifier) { shown ->
        if (shown != null) {
            AsyncImage(
                model = shown,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

/** Fades a backdrop into the background from the left and from the bottom, so text stays readable. */
@Composable
fun TvBackdropScrims(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to background,
                    0.3f to background.copy(alpha = 0.85f),
                    0.7f to Color.Transparent
                )
            )
    )
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.3f to Color.Transparent,
                    0.75f to background.copy(alpha = 0.9f),
                    1f to background
                )
            )
    )
}

/**
 * Lines a focused card up just below the top of a vertical list, so moving between rows always
 * lands the new row in the same place instead of the list creeping a few pixels at a time.
 */
@OptIn(ExperimentalFoundationApi::class)
fun tvRowPivot(headerSpace: Float): BringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Already on screen with room for its row title: leave the list where it is.
        if (offset >= headerSpace && offset + size <= containerSize) return 0f
        return offset - headerSpace
    }
}

/** A row title for a rail of cards. */
@Composable
fun TvRowHeader(title: String, modifier: Modifier = Modifier, start: Dp = TvLayout.ContentStart) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .padding(start = start)
            .semantics { heading() }
    )
}

/**
 * A horizontal rail. It remembers the card that had focus, so coming back to the row with Up or
 * Down returns to where the user was rather than to the first card.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvRail(
    title: String,
    modifier: Modifier = Modifier,
    start: Dp = TvLayout.ContentStart,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    Column(modifier) {
        TvRowHeader(title, start = start)
        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            contentPadding = PaddingValues(
                start = start,
                end = TvLayout.ContentEnd,
                top = TvLayout.RowFocusPadding,
                bottom = TvLayout.RowFocusPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(TvLayout.CardGap),
            content = content
        )
    }
}

@Composable
private fun tvCardBorder() = CardDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
        shape = TvLayout.CardShape
    )
)

/** A 2:3 poster. The title is spoken, and shown by the screen's info panel rather than on the card. */
@Composable
fun TvPosterCard(
    title: String,
    posterPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    /** Holding OK: the card's secondary action, such as delete. */
    onLongClick: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(TvLayout.PosterWidth, TvLayout.PosterHeight)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { contentDescription = title },
        shape = CardDefaults.shape(TvLayout.CardShape),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = tvCardBorder(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (posterPath != null) {
                AsyncImage(
                    model = "$TV_POSTER_BASE$posterPath",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp)
                )
            }
        }
    }
}

/** A 16:9 card with its label on the picture: Continue watching, episodes. */
@Composable
fun TvLandscapeCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    width: Dp = TvLayout.LandscapeWidth,
    onFocused: () -> Unit = {},
    /** Holding OK: the card's secondary action, such as delete. */
    onLongClick: (() -> Unit)? = null
) {
    val label = listOfNotNull(title, subtitle).joinToString(", ")
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width, width * 9f / 16f)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { contentDescription = label },
        shape = CardDefaults.shape(TvLayout.CardShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = tvCardBorder(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = if (progress != null) 6.dp else 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

@Composable
internal fun TvErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.st_could_not_catalog),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_check_connection),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            // TV components set their content color for the TV Text, not the Material 3 one.
            androidx.tv.material3.Text(stringResource(R.string.action_try_again))
        }
    }
}

/** A tab page's title, with an optional line under it and actions on the right. */
@Composable
fun TvPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd, top = 36.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

/**
 * A centred question over a dimmed screen, in place of a dialog a remote handles badly. Focus
 * starts on Cancel so a stray OK never deletes anything, and Back cancels.
 */
@Composable
fun TvConfirmPanel(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val cancelRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onCancel)
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { cancelRequester.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(Modifier.padding(32.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onConfirm,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            focusedContainerColor = MaterialTheme.colorScheme.error,
                            focusedContentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        androidx.tv.material3.Text(confirmLabel)
                    }
                    androidx.tv.material3.OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.focusRequester(cancelRequester)
                    ) {
                        androidx.tv.material3.Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

/** A sentence in the middle of an empty page. */
@Composable
fun TvEmptyPage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A rounded text pill: filters, genres, recent searches. [selected] keeps it lit when not focused. */
@Composable
fun TvPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                androidx.tv.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            androidx.tv.material3.Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** A poster with its title and a short line under it, for grids that are read rather than browsed. */
@Composable
fun TvCaptionedPoster(
    title: String,
    posterPath: String?,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Column(modifier) {
        TvPosterCard(title = title, posterPath = posterPath, onClick = onClick, onLongClick = onLongClick)
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TvLayout.PosterWidth)
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(TvLayout.PosterWidth)
            )
        }
    }
}
