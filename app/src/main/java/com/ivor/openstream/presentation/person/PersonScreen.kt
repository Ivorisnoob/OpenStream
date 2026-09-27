package com.ivor.openstream.presentation.person

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
                Text("Couldn't load this person", style = MaterialTheme.typography.titleMedium)
                Button(onClick = viewModel::load) { Text("Try again") }
            }

            is PersonUiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
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
                            text = "Known for",
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
    var bioExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = person.profilePath?.let { "https://image.tmdb.org/t/p/h632$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        personFacts(person)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
                Text(if (bioExpanded) "Show less" else "Read more")
            }
        }
    }
}

@Composable
private fun CreditCard(title: AnimeDto, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClickLabel = "Open ${title.name}", onClick = onClick)) {
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
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        title.date.take(4).takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

/** "Acting · Born 1974-11-11 in Los Angeles" and similar, from whatever TMDB has. */
private fun personFacts(person: PersonDto): String? {
    val born = person.birthday?.let { date ->
        buildString {
            append("Born ").append(date)
            person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { append(" in ").append(it) }
        }
    }
    val died = person.deathday?.let { "Died $it" }
    return listOfNotNull(person.knownForDepartment, born, died).joinToString(" · ").takeIf { it.isNotBlank() }
}
