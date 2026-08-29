package com.ivor.openstream.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenMarketplace: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                navigationIcon = {
                    ExpressiveBackButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                MarketplaceEntryCard(
                    state = state,
                    onOpenMarketplace = onOpenMarketplace
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Extension,
                    title = "Installed sources",
                    subtitle = "${state.enabledCount} of ${state.installedCount} installed sources are active",
                    onClick = onOpenMarketplace
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Public,
                    title = "Repositories",
                    subtitle = "${state.repoCount} connected · add community catalogs by URL",
                    onClick = onOpenMarketplace
                )
            }

            if (state.updateCount > 0) {
                item {
                    SettingsRow(
                        icon = Icons.Default.Update,
                        title = "Extension updates",
                        subtitle = "${state.updateCount} waiting to be applied",
                        onClick = onOpenMarketplace
                    )
                }
            }

            item { SafetyNote() }

            item {
                Text(
                    text = "OpenStream ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MarketplaceEntryCard(
    state: SettingsUiState,
    onOpenMarketplace: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.extraLarge)
            .clickable(onClick = onOpenMarketplace),
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.medium,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Extension marketplace",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Browse the top charts, install new sources, and connect community repositories.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(label = "Available", value = state.availableCount.toString())
                HeroStat(label = "Installed", value = state.installedCount.toString())
                HeroStat(label = "Enabled", value = state.enabledCount.toString())
            }
            Button(onClick = onOpenMarketplace, shape = ExpressiveShapes.small) {
                Text("Open marketplace")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.large)
            .clickable(onClick = onClick),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = ExpressiveShapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SafetyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Data-only extensions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Repositories publish manifests, never executable code. Each manifest configures a resolver that already ships with the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}
