package com.ivor.openstream.presentation.person

import com.ivor.openstream.presentation.components.isCompactWidth
import com.ivor.openstream.R
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import com.ivor.openstream.presentation.components.byWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.PersonDto
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonScreen(
    onBackClick: () -> Unit,
    onOpenTitle: (id: Int, mediaType: String) -> Unit,
    viewModel: PersonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            PersonUiState.Loading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))

            is PersonUiState.Error -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.details_could_not_load_person), style = MaterialTheme.typography.titleMedium)
                Button(onClick = viewModel::load) { Text(stringResource(R.string.action_try_again)) }
            }

            is PersonUiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = byWidth(compact = 108.dp, medium = 128.dp, expanded = 140.dp)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 136.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    PersonHeader(state.person)
                }
                if (state.credits.isNotEmpty()) {
                    item(key = "credits-title", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.details_known_for),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .semantics { heading() }
                        )
                    }
                }
                items(state.credits, key = { "${it.mediaType}:${it.id}" }) { title ->
                    CreditCard(
                        title = title,
                        onClick = { onOpenTitle(title.id, if (title.isMovie) "movie" else "tv") }
                    )
                }
            }
        }

        ExpressiveBackButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp)
        )
    }
}

@Composable
private fun PersonHeader(person: PersonDto) {
    if (isCompactWidth) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PersonPhoto(person, size = 160.dp)
            Spacer(Modifier.height(16.dp))
            PersonText(person, centered = true)
        }
    } else {
        // Tablets: photo beside the text, so the bio keeps a readable line length.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 72.dp, start = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            PersonPhoto(person, size = 200.dp)
            Column(
                modifier = Modifier
                    .padding(start = 28.dp, top = 8.dp)
                    .widthIn(max = 640.dp)
            ) {
                PersonText(person, centered = false)
            }
        }
    }
}

@Composable
private fun PersonPhoto(person: PersonDto, size: androidx.compose.ui.unit.Dp) {
    AsyncImage(
        model = person.profilePath?.let { "https://image.tmdb.org/t/p/h632$it" },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

@Composable
private fun PersonText(person: PersonDto, centered: Boolean) {
    var bioExpanded by rememberSaveable { mutableStateOf(false) }
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Text(
        text = person.name,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        textAlign = align,
        modifier = Modifier.semantics { heading() }
    )
    personFacts(person)?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    person.biography?.takeIf { it.isNotBlank() }?.let { bio ->
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (bioExpanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp)
        )
        TextButton(onClick = { bioExpanded = !bioExpanded }) {
            Text(if (bioExpanded) stringResource(R.string.action_show_less) else stringResource(R.string.action_read_more))
        }
    }
}

@Composable
private fun CreditCard(title: AnimeDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(ExpressiveShapes.medium)
            .clickable(onClickLabel = stringResource(R.string.misc_open_title, title.name), onClick = onClick)
            .padding(bottom = 10.dp)
    ) {
        AsyncImage(
            model = "https://image.tmdb.org/t/p/w342${title.posterPath}",
            contentDescription = title.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(ExpressiveShapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Text(
            text = title.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp)
        )
        title.date.take(4).takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

/** "Acting · Born 1974-11-11 in Los Angeles" and similar, from whatever TMDB has. */
@Composable
private fun personFacts(person: PersonDto): String? {
    val born = person.birthday?.let { date ->
        person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { place ->
            stringResource(R.string.ps_born_in, date, place)
        } ?: stringResource(R.string.ps_born, date)
    }
    val died = person.deathday?.let { stringResource(R.string.ps_died, it) }
    return listOfNotNull(person.knownForDepartment, born, died).joinToString(" · ").takeIf { it.isNotBlank() }
}
