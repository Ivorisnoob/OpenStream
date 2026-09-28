package com.ivor.openstream.presentation.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** Color role an avatar is drawn in, so avatars follow the theme (and dynamic color). */
enum class AvatarTone { PRIMARY, SECONDARY, TERTIARY }

/**
 * Built-in profile avatars. [key] is what the `profiles.avatar` column stores; the icon, shape and
 * tone are how it looks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class ProfileAvatar(val key: String, val icon: ImageVector, val shape: () -> RoundedPolygon, val tone: AvatarTone) {
    FOX("fox", Icons.Default.Pets, { MaterialShapes.Cookie9Sided }, AvatarTone.PRIMARY),
    ROCKET("rocket", Icons.Default.RocketLaunch, { MaterialShapes.Sunny }, AvatarTone.TERTIARY),
    STAR("star", Icons.Default.Star, { MaterialShapes.Clover4Leaf }, AvatarTone.SECONDARY),
    FACE("face", Icons.Default.Face, { MaterialShapes.Circle }, AvatarTone.PRIMARY),
    GAME("game", Icons.Default.SportsEsports, { MaterialShapes.Pill }, AvatarTone.TERTIARY),
    MUSIC("music", Icons.Default.MusicNote, { MaterialShapes.Flower }, AvatarTone.SECONDARY),
    LEAF("leaf", Icons.Default.Eco, { MaterialShapes.Gem }, AvatarTone.PRIMARY),
    ROBOT("robot", Icons.Default.SmartToy, { MaterialShapes.Cookie6Sided }, AvatarTone.SECONDARY),
    SPARKLE("sparkle", Icons.Default.AutoAwesome, { MaterialShapes.SoftBurst }, AvatarTone.TERTIARY),
    ICECREAM("icecream", Icons.Default.Icecream, { MaterialShapes.Puffy }, AvatarTone.PRIMARY);

    companion object {
        fun from(key: String?): ProfileAvatar = entries.firstOrNull { it.key == key } ?: FOX
    }
}

@Composable
private fun AvatarTone.colors(): Pair<Color, Color> = when (this) {
    AvatarTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    AvatarTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    AvatarTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

/** A profile's avatar: its icon inside its expressive shape. Decorative; callers label it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileAvatarBadge(avatarKey: String, size: Dp, modifier: Modifier = Modifier) {
    val avatar = ProfileAvatar.from(avatarKey)
    val (container, content) = avatar.tone.colors()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(avatar.shape().toShape())
            .background(container)
    ) {
        Icon(avatar.icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

/** Avatar size for small places like the Home top bar. */
val SmallAvatar = 36.dp
